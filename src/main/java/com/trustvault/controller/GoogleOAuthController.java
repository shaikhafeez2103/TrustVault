package com.trustvault.controller;

import com.trustvault.dto.AuthResponse;
import com.trustvault.enums.AuditAction;
import com.trustvault.security.JwtTokenProvider;
import com.trustvault.service.AuditService;
import com.trustvault.service.GoogleOAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
public class GoogleOAuthController {

    private static final String STATE_COOKIE_NAME = "tv_oauth_nonce";

    @Autowired
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuditService auditService;

    @GetMapping("/api/auth/oauth2/google/status")
    public ResponseEntity<Map<String, Object>> getGoogleOAuthStatus(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of(
                "configured", googleOAuthService.isConfigured(),
                "authorizationEndpoint", "/oauth2/authorization/google",
                "redirectUri", googleOAuthService.resolveRedirectUri(request)
        ));
    }

    @GetMapping({"/oauth2/authorization/google", "/api/auth/oauth2/google"})
    public void initiateGoogleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!googleOAuthService.isConfigured()) {
            response.sendRedirect("/login.html?oauth_error=not_configured");
            return;
        }

        String nonce = googleOAuthService.generateNonce();
        String stateToken = tokenProvider.generateOAuthStateToken(nonce);

        boolean isSecure = isHttpsRequest(request);
        String cookieHeader = String.format("%s=%s; Path=/; Max-Age=600; HttpOnly; SameSite=Lax%s",
                STATE_COOKIE_NAME, nonce, isSecure ? "; Secure" : "");
        response.addHeader("Set-Cookie", cookieHeader);

        String googleAuthUrl = googleOAuthService.buildAuthorizationUrl(stateToken, request);
        response.sendRedirect(googleAuthUrl);
    }

    @GetMapping({"/login/oauth2/code/google", "/api/auth/oauth2/google/callback"})
    public void handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        clearStateCookie(request, response);

        if (error != null && !error.isBlank()) {
            auditService.logSecurityEvent("GOOGLE_OAUTH", AuditAction.AUTH_LOGIN_FAILED, "OAUTH2", null,
                    "Google OAuth login cancelled or denied by provider: " + error, clientIp, false);
            response.sendRedirect("/login.html?oauth_error=cancelled");
            return;
        }

        String expectedNonce = extractCookieValue(request, STATE_COOKIE_NAME);
        if (state == null || state.isBlank() || expectedNonce == null || !tokenProvider.validateOAuthStateToken(state, expectedNonce)) {
            auditService.logSecurityEvent("GOOGLE_OAUTH", AuditAction.AUTH_LOGIN_FAILED, "OAUTH2", null,
                    "Rejected Google OAuth callback due to invalid or missing CSRF state token", clientIp, false);
            response.sendRedirect("/login.html?oauth_error=invalid_state");
            return;
        }

        try {
            String exchangeCode = googleOAuthService.handleAuthorizationCodeCallback(code, request);
            response.sendRedirect("/login.html?oauth_code=" + exchangeCode);
        } catch (SecurityException se) {
            String reason = se.getMessage() != null ? se.getMessage() : "auth_failed";
            response.sendRedirect("/login.html?oauth_error=" + reason.replaceAll("[^a-zA-Z0-9_-]", ""));
        } catch (IllegalStateException ise) {
            response.sendRedirect("/login.html?oauth_error=not_configured");
        } catch (Exception ex) {
            response.sendRedirect("/login.html?oauth_error=token_exchange_failed");
        }
    }

    @PostMapping("/api/auth/oauth2/exchange")
    public ResponseEntity<?> exchangeOAuthCode(@RequestBody Map<String, String> payload) {
        String code = payload != null ? payload.get("code") : null;
        try {
            AuthResponse authResponse = googleOAuthService.consumeExchangeCode(code);
            return ResponseEntity.ok(authResponse);
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", e.getMessage()
            ));
        }
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearStateCookie(HttpServletRequest request, HttpServletResponse response) {
        boolean isSecure = isHttpsRequest(request);
        String cookieHeader = String.format("%s=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax%s",
                STATE_COOKIE_NAME, isSecure ? "; Secure" : "");
        response.addHeader("Set-Cookie", cookieHeader);
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request == null) return false;
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
    }
}
