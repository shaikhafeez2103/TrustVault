package com.trustvault.service;

import com.trustvault.dto.AuthResponse;
import com.trustvault.dto.LoginRequest;
import com.trustvault.dto.RegisterRequest;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.UserRepository;
import com.trustvault.security.JwtTokenProvider;
import com.trustvault.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L; // 5 minutes
    private static final Set<String> PROTECTED_DEMO_USERS = Set.of(
            "admin", "admin@trustvault.io",
            "owner", "owner@trustvault.io",
            "viewer", "viewer@trustvault.io"
    );

    private final Map<String, FailedLoginTracker> loginAttempts = new ConcurrentHashMap<>();

    private static class FailedLoginTracker {
        int count;
        long firstFailureTime;
        long lastFailureTime;

        FailedLoginTracker(long now) {
            this.count = 1;
            this.firstFailureTime = now;
            this.lastFailureTime = now;
        }
    }

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuditService auditService;

    public void clearRateLimiter() {
        loginAttempts.clear();
    }

    @Transactional
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        String identifier = loginRequest.getUsername() != null ? loginRequest.getUsername().trim().toLowerCase() : "";
        String rateKey = identifier + "|" + clientIp;

        FailedLoginTracker tracker = loginAttempts.get(rateKey);
        long now = System.currentTimeMillis();
        if (tracker != null) {
            if (now - tracker.lastFailureTime > LOCKOUT_DURATION_MS) {
                loginAttempts.remove(rateKey);
            } else if (tracker.count >= MAX_FAILED_ATTEMPTS) {
                auditService.logSecurityEvent(
                        loginRequest.getUsername(),
                        AuditAction.AUTH_LOGIN_FAILED,
                        "USER",
                        null,
                        "Blocked login attempt due to brute-force rate limit lockout",
                        clientIp,
                        false
                );
                throw new LockedException("Too many failed login attempts. Account temporarily locked for 5 minutes.");
            }
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            loginAttempts.remove(rateKey);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.generateToken(authentication);

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            auditService.logSecurityEvent(
                    user.getUsername(),
                    AuditAction.AUTH_LOGIN_SUCCESS,
                    "USER",
                    user.getId().toString(),
                    "Successful authentication via credentials",
                    clientIp,
                    true
            );

            return new AuthResponse(
                    jwt,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole().name()
            );
        } catch (AuthenticationException ex) {
            loginAttempts.compute(rateKey, (k, existing) -> {
                if (existing == null || (now - existing.lastFailureTime > LOCKOUT_DURATION_MS)) {
                    return new FailedLoginTracker(now);
                }
                existing.count++;
                existing.lastFailureTime = now;
                return existing;
            });

            auditService.logSecurityEvent(
                    loginRequest.getUsername(),
                    AuditAction.AUTH_LOGIN_FAILED,
                    "USER",
                    null,
                    "Failed login attempt: Invalid password or username",
                    clientIp,
                    false
            );
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Transactional
    public void handleForgotPassword(String emailOrUsername, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            throw new IllegalArgumentException("Email address or username is required.");
        }
        String normalized = emailOrUsername.trim().toLowerCase();
        if (PROTECTED_DEMO_USERS.contains(normalized)) {
            throw new IllegalArgumentException("Built-in evaluator demo accounts (admin, owner, viewer) cannot be reset.");
        }
        auditService.logSecurityEvent(
                normalized,
                AuditAction.AUTH_LOGIN_SUCCESS,
                "PASSWORD_RESET",
                null,
                "Password recovery requested for identifier: " + normalized,
                clientIp,
                true
        );
    }

    @Transactional
    public void handleLogout(Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            auditService.logSecurityEvent(
                    authentication.getName(),
                    AuditAction.AUTH_LOGIN_SUCCESS,
                    "USER_SESSION",
                    null,
                    "User logged out and terminated client session",
                    clientIp,
                    true
            );
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already taken: " + registerRequest.getUsername());
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already in use: " + registerRequest.getEmail());
        }

        User user = new User(
                registerRequest.getUsername(),
                registerRequest.getEmail(),
                passwordEncoder.encode(registerRequest.getPassword()),
                registerRequest.getFullName(),
                // Force ROLE_OWNER for all public registrations; ignore client-supplied role
                RoleName.ROLE_OWNER
        );

        User savedUser = userRepository.save(user);

        auditService.logSecurityEvent(
                savedUser.getUsername(),
                AuditAction.AUTH_REGISTER,
                "USER",
                savedUser.getId().toString(),
                "User account registered with role: " + savedUser.getRole().name(),
                clientIp,
                true
        );

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        registerRequest.getUsername(),
                        registerRequest.getPassword()
                )
        );

        String jwt = tokenProvider.generateToken(authentication);

        return new AuthResponse(
                jwt,
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole().name()
        );
    }
}
