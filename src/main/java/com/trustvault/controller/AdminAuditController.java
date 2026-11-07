package com.trustvault.controller;

import com.trustvault.dto.AuditLogResponse;
import com.trustvault.dto.SecurityStatsResponse;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.RoleName;
import com.trustvault.exception.ResourceNotFoundException;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(value = "action", required = false) AuditAction action,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<AuditLogResponse> logs = auditService.getAuditLogs(action, username, resourceType, success, startDate, endDate);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/stats")
    public ResponseEntity<SecurityStatsResponse> getSecurityStats() {
        SecurityStatsResponse stats = auditService.getSecurityStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> response = users.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("fullName", u.getFullName());
            map.put("email", u.getEmail());
            map.put("role", u.getRole().name());
            map.put("enabled", u.isEnabled());
            map.put("oauthProvider", u.getOauthProvider() != null ? u.getOauthProvider() : "LOCAL");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @PathVariable("id") Long userId,
            @RequestBody Map<String, String> payload,
            Authentication authentication,
            HttpServletRequest request) {

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if ("admin".equalsIgnoreCase(target.getUsername())) {
            throw new IllegalArgumentException("Primary system administrator role cannot be modified.");
        }

        String requestedRoleStr = payload != null ? payload.get("role") : null;
        if (requestedRoleStr == null || requestedRoleStr.isBlank()) {
            throw new IllegalArgumentException("Target role is required.");
        }

        RoleName newRole;
        try {
            newRole = RoleName.valueOf(requestedRoleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role specified: " + requestedRoleStr);
        }

        RoleName oldRole = target.getRole();
        target.setRole(newRole);
        User saved = userRepository.save(target);

        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        String adminUser = authentication != null ? authentication.getName() : "admin";
        auditService.logSecurityEvent(
                adminUser,
                AuditAction.PERMISSION_GRANTED,
                "USER",
                saved.getId().toString(),
                "Updated user '" + saved.getUsername() + "' role from " + oldRole + " to " + newRole,
                clientIp,
                true
        );

        Map<String, Object> result = new HashMap<>();
        result.put("id", saved.getId());
        result.put("username", saved.getUsername());
        result.put("fullName", saved.getFullName());
        result.put("email", saved.getEmail());
        result.put("role", saved.getRole().name());
        result.put("enabled", saved.isEnabled());
        return ResponseEntity.ok(result);
    }
}
