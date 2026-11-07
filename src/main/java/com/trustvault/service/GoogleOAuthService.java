package com.trustvault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.dto.AuthResponse;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.UserRepository;
import com.trustvault.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthService {

    private static final Set<String> PROTECTED_SYSTEM_USERS = Set.of(
            "admin", "admin@trustvault.io",
            "owner", "owner@trustvault.io",
            "viewer", "viewer@trustvault.io"
    );

    private static final long EXCHANGE_CODE_TTL_MS = 60_000L; // 60 seconds single-use exchange code

    @Value("${app.oauth2.google.client-id:}")
    private String clientId;

    @Value("${app.oauth2.google.client-secret:}")
    private String clientSecret;

    @Value("${app.oauth2.google.redirect-uri:https://trustvault-pxqr.onrender.com/login/oauth2/code/google}")
    private String configuredRedirectUri;

    @Value("${app.oauth2.google.auth-uri:https://accounts.google.com/o/oauth2/v2/auth}")
    private String authUri;

    @Value("${app.oauth2.google.token-uri:https://oauth2.googleapis.com/token}")
    private String tokenUri;

    @Value("${app.oauth2.google.userinfo-uri:https://openidconnect.googleapis.com/v1/userinfo}")
    private String userInfoUri;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, ExchangeCodeEntry> exchangeStore = new ConcurrentHashMap<>();

    private static class ExchangeCodeEntry {
        final AuthResponse authResponse;
        final long expiresAt;

        ExchangeCodeEntry(AuthResponse authResponse, long expiresAt) {
            this.authResponse = authResponse;
            this.expiresAt = expiresAt;
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && !clientId.equalsIgnoreCase("GOOGLE_CLIENT_ID")
                && !clientId.startsWith("YOUR_")
                && clientSecret != null && !clientSecret.isBlank()
                && !clientSecret.equalsIgnoreCase("GOOGLE_CLIENT_SECRET")
                && !clientSecret.startsWith("YOUR_");
    }

    public String getConfiguredRedirectUri() {
        return configuredRedirectUri;
    }

    public String resolveRedirectUri(HttpServletRequest request) {
        if (configuredRedirectUri != null && !configuredRedirectUri.isBlank()
                && !configuredRedirectUri.equalsIgnoreCase("GOOGLE_REDIRECT_URI")) {
            // If running locally on localhost and redirect URI still points to Render default, adapt to local port unless explicitly set via env
            if (request != null && ("localhost".equalsIgnoreCase(request.getServerName()) || "127.0.0.1".equals(request.getServerName()))) {
                String envRedirect = System.getenv("GOOGLE_REDIRECT_URI");
                if (envRedirect == null || envRedirect.isBlank()) {
                    int port = request.getServerPort();
                    String portPart = (port == 80 || port == 443 || port <= 0) ? "" : ":" + port;
                    return request.getScheme() + "://" + request.getServerName() + portPart + "/login/oauth2/code/google";
                }
            }
            return configuredRedirectUri.trim();
        }
        if (request != null) {
            String proto = Optional.ofNullable(request.getHeader("X-Forwarded-Proto")).orElse(request.getScheme());
            String host = Optional.ofNullable(request.getHeader("X-Forwarded-Host")).orElse(request.getServerName());
            if (host != null && host.contains("onrender.com")) {
                proto = "https";
            }
            return proto + "://" + host + "/login/oauth2/code/google";
        }
        return "https://trustvault-pxqr.onrender.com/login/oauth2/code/google";
    }

    public String generateNonce() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String buildAuthorizationUrl(String stateToken, HttpServletRequest request) {
        String redirectUri = resolveRedirectUri(request);
        return authUri
                + "?client_id=" + urlEncode(clientId.trim())
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + urlEncode("openid email profile")
                + "&state=" + urlEncode(stateToken)
                + "&access_type=online"
                + "&prompt=select_account";
    }

    @Transactional
    public String handleAuthorizationCodeCallback(String code, HttpServletRequest request) throws Exception {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        if (!isConfigured()) {
            throw new IllegalStateException("not_configured");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("missing_code");
        }

        String redirectUri = resolveRedirectUri(request);
        String formBody = "code=" + urlEncode(code)
                + "&client_id=" + urlEncode(clientId.trim())
                + "&client_secret=" + urlEncode(clientSecret.trim())
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&grant_type=authorization_code";

        HttpURLConnection tokenConn = (HttpURLConnection) URI.create(tokenUri).toURL().openConnection();
        tokenConn.setRequestMethod("POST");
        tokenConn.setConnectTimeout(10_000);
        tokenConn.setReadTimeout(12_000);
        tokenConn.setDoOutput(true);
        tokenConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        tokenConn.setRequestProperty("Accept", "application/json");
        try (OutputStream os = tokenConn.getOutputStream()) {
            os.write(formBody.getBytes(StandardCharsets.UTF_8));
        }

        int tokenStatus = tokenConn.getResponseCode();
        if (tokenStatus != 200) {
            auditService.logSecurityEvent("GOOGLE_OAUTH", AuditAction.AUTH_LOGIN_FAILED, "OAUTH2", null,
                    "Google OAuth token exchange failed with HTTP " + tokenStatus, clientIp, false);
            throw new SecurityException("token_exchange_failed");
        }

        String tokenBody;
        try (InputStream is = tokenConn.getInputStream()) {
            tokenBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<?, ?> tokenMap = objectMapper.readValue(tokenBody, Map.class);
        String accessToken = (String) tokenMap.get("access_token");
        String idToken = (String) tokenMap.get("id_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new SecurityException("token_exchange_failed");
        }

        // Verify id_token audience and issuer if present
        if (idToken != null && !idToken.isBlank()) {
            verifyIdTokenClaims(idToken);
        }

        // Fetch verified user profile from Google OpenID Connect UserInfo endpoint
        HttpURLConnection userInfoConn = (HttpURLConnection) URI.create(userInfoUri).toURL().openConnection();
        userInfoConn.setRequestMethod("GET");
        userInfoConn.setConnectTimeout(10_000);
        userInfoConn.setReadTimeout(10_000);
        userInfoConn.setRequestProperty("Authorization", "Bearer " + accessToken);
        userInfoConn.setRequestProperty("Accept", "application/json");

        int userInfoStatus = userInfoConn.getResponseCode();
        if (userInfoStatus != 200) {
            auditService.logSecurityEvent("GOOGLE_OAUTH", AuditAction.AUTH_LOGIN_FAILED, "OAUTH2", null,
                    "Google OIDC userinfo lookup failed with HTTP " + userInfoStatus, clientIp, false);
            throw new SecurityException("userinfo_failed");
        }

        String userInfoBody;
        try (InputStream is = userInfoConn.getInputStream()) {
            userInfoBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<?, ?> profile = objectMapper.readValue(userInfoBody, Map.class);
        String sub = profile.get("sub") != null ? String.valueOf(profile.get("sub")).trim() : null;
        String email = profile.get("email") != null ? String.valueOf(profile.get("email")).trim().toLowerCase() : null;
        Object verifiedRaw = profile.get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verifiedRaw) || "true".equalsIgnoreCase(String.valueOf(verifiedRaw));
        String fullName = profile.get("name") != null ? String.valueOf(profile.get("name")).trim() : null;

        return processVerifiedGoogleIdentity(sub, email, emailVerified, fullName, clientIp);
    }

    private void verifyIdTokenClaims(String idToken) throws Exception {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new SecurityException("invalid_id_token");
        }
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        Map<?, ?> claims = objectMapper.readValue(payloadBytes, Map.class);
        String iss = claims.get("iss") != null ? String.valueOf(claims.get("iss")) : "";
        String aud = claims.get("aud") != null ? String.valueOf(claims.get("aud")) : "";
        if (!"https://accounts.google.com".equals(iss) && !"accounts.google.com".equals(iss)) {
            throw new SecurityException("invalid_issuer");
        }
        if (!clientId.trim().equals(aud)) {
            throw new SecurityException("invalid_audience");
        }
    }

    @Transactional
    public String processVerifiedGoogleIdentity(String sub, String email, boolean emailVerified,
                                                String fullName, String clientIp) {
        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            auditService.logSecurityEvent("GOOGLE_OAUTH", AuditAction.AUTH_LOGIN_FAILED, "OAUTH2", null,
                    "Rejected Google OAuth login due to missing subject or email claim", clientIp, false);
            throw new SecurityException("invalid_profile");
        }

        if (!emailVerified) {
            auditService.logSecurityEvent(email, AuditAction.AUTH_LOGIN_FAILED, "OAUTH2", null,
                    "Rejected Google OAuth login because email_verified is false", clientIp, false);
            throw new SecurityException("unverified_email");
        }

        String normalizedEmail = email.trim().toLowerCase();
        User targetUser;

        // 1. Check if a user is already linked via immutable Google subject ID
        Optional<User> linkedBySub = userRepository.findByOauthProviderAndOauthProviderId("GOOGLE", sub);
        if (linkedBySub.isPresent()) {
            targetUser = linkedBySub.get();
            if (!targetUser.isEnabled()) {
                auditService.logSecurityEvent(targetUser.getUsername(), AuditAction.AUTH_LOGIN_FAILED, "USER",
                        targetUser.getId().toString(), "Rejected Google OAuth login for disabled user", clientIp, false);
                throw new SecurityException("account_disabled");
            }
        } else {
            // 2. Check if an account with this email already exists
            Optional<User> existingByEmail = userRepository.findByEmail(normalizedEmail);
            if (existingByEmail.isPresent()) {
                User existing = existingByEmail.get();

                // Account takeover defense:
                // Never auto-link Google OAuth to Admin/Owner or built-in system accounts, or accounts linked to a different OAuth subject
                if (existing.getRole() == RoleName.ROLE_ADMIN
                        || existing.getRole() == RoleName.ROLE_OWNER
                        || PROTECTED_SYSTEM_USERS.contains(existing.getUsername().toLowerCase())
                        || PROTECTED_SYSTEM_USERS.contains(normalizedEmail)
                        || (existing.getOauthProviderId() != null && !existing.getOauthProviderId().equals(sub))) {
                    auditService.logSecurityEvent(
                            existing.getUsername(),
                            AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                            "USER",
                            existing.getId().toString(),
                            "Blocked unsafe Google OAuth email linking attempt to existing privileged/bound account: " + existing.getUsername(),
                            clientIp,
                            false
                    );
                    throw new SecurityException("account_conflict");
                }

                if (!existing.isEnabled()) {
                    throw new SecurityException("account_disabled");
                }

                // Safe linking for verified least-privileged viewer account
                existing.setOauthProvider("GOOGLE");
                existing.setOauthProviderId(sub);
                targetUser = userRepository.save(existing);
            } else {
                // 3. Create new least-privileged ROLE_VIEWER account
                String baseUsername = normalizedEmail.split("@")[0].replaceAll("[^a-z0-9_]", "");
                if (baseUsername.length() < 3) {
                    baseUsername = "guser_" + baseUsername;
                }
                if (baseUsername.length() > 38) {
                    baseUsername = baseUsername.substring(0, 38);
                }
                String uniqueUsername = baseUsername;
                while (userRepository.existsByUsername(uniqueUsername)) {
                    String suffix = HexFormat.of().formatHex(new byte[]{(byte) secureRandom.nextInt(256), (byte) secureRandom.nextInt(256)});
                    uniqueUsername = baseUsername + "_" + suffix;
                }

                String displayName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : uniqueUsername;
                String randomPassword = UUID.randomUUID().toString() + "Aa1!";

                User newUser = new User(
                        uniqueUsername,
                        normalizedEmail,
                        passwordEncoder.encode(randomPassword),
                        displayName,
                        RoleName.ROLE_VIEWER // Always least-privileged role for new Google accounts
                );
                newUser.setOauthProvider("GOOGLE");
                newUser.setOauthProviderId(sub);
                targetUser = userRepository.save(newUser);

                auditService.logSecurityEvent(
                        targetUser.getUsername(),
                        AuditAction.AUTH_REGISTER,
                        "USER",
                        targetUser.getId().toString(),
                        "Provisioned new Google OAuth 2.0 account with least-privileged role: " + targetUser.getRole().name(),
                        clientIp,
                        true
                );
            }
        }

        String jwt = tokenProvider.generateTokenForUser(targetUser);

        auditService.logSecurityEvent(
                targetUser.getUsername(),
                AuditAction.AUTH_LOGIN_SUCCESS,
                "USER",
                targetUser.getId().toString(),
                "Successful authentication via Google OAuth 2.0 (OpenID Connect verified)",
                clientIp,
                true
        );

        AuthResponse authResponse = new AuthResponse(
                jwt,
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                targetUser.getRole().name()
        );

        return createExchangeCode(authResponse);
    }

    public String createExchangeCode(AuthResponse response) {
        long now = System.currentTimeMillis();
        exchangeStore.entrySet().removeIf(e -> now > e.getValue().expiresAt);

        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        exchangeStore.put(code, new ExchangeCodeEntry(response, now + EXCHANGE_CODE_TTL_MS));
        return code;
    }

    public AuthResponse consumeExchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth exchange code");
        }
        ExchangeCodeEntry entry = exchangeStore.remove(code);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
            throw new SecurityException("Invalid or expired OAuth exchange code");
        }
        return entry.authResponse;
    }

    private String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
