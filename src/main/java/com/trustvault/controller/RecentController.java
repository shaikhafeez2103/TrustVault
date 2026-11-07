package com.trustvault.controller;

import com.trustvault.dto.AccessRequestResponse;
import com.trustvault.entity.AuditLog;
import com.trustvault.entity.User;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.AuditLogRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AccessRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecentController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessRequestService accessRequestService;

    @GetMapping("/recent-activities")
    public ResponseEntity<List<Map<String, String>>> getRecentActivities(Authentication authentication) {
        List<Map<String, String>> activities = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a");
        String username = authentication != null ? authentication.getName() : "";
        boolean isAdmin = userRepository.findByUsername(username)
                .map(u -> u.getRole() == RoleName.ROLE_ADMIN)
                .orElse(false);

        List<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc();
        for (AuditLog log : logs) {
            if (isAdmin || username.equalsIgnoreCase(log.getUsername())) {
                Map<String, String> map = new HashMap<>();
                map.put("description", "[" + log.getAction() + "] " + log.getUsername() + " — " + log.getDetails());
                map.put("timestamp", log.getTimestamp() != null ? log.getTimestamp().format(fmt) : "");
                activities.add(map);
                if (activities.size() >= 10) break;
            }
        }
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/pending-requests")
    public ResponseEntity<List<Map<String, String>>> getPendingRequests(Authentication authentication) {
        List<Map<String, String>> requests = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a");
        List<AccessRequestResponse> pending = accessRequestService.getPendingRequestsForOwner(authentication);
        for (AccessRequestResponse req : pending) {
            Map<String, String> map = new HashMap<>();
            map.put("id", String.valueOf(req.getId()));
            map.put("user", req.getRequesterUsername());
            map.put("document", req.getDocumentTitle());
            map.put("requestedAt", req.getCreatedAt() != null ? req.getCreatedAt().format(fmt) : "");
            requests.add(map);
        }
        return ResponseEntity.ok(requests);
    }
}
