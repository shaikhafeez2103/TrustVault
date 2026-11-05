package com.trustvault.controller;

import com.trustvault.dto.AuditLogResponse;
import com.trustvault.dto.SecurityStatsResponse;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
