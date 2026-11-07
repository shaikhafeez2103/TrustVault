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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

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

    @Transactional
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

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
        } catch (BadCredentialsException ex) {
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
