package com.trustvault.controller;

import com.trustvault.dto.PermissionGrantRequest;
import com.trustvault.dto.PermissionResponse;
import com.trustvault.entity.User;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getShareableUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = users.stream()
                .filter(User::isEnabled)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("username", u.getUsername());
                    m.put("fullName", u.getFullName());
                    m.put("role", u.getRole().name());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/documents/{id}/grant")
    public ResponseEntity<PermissionResponse> grantPermission(
            @PathVariable("id") Long documentId,
            @Valid @RequestBody PermissionGrantRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        PermissionResponse res = permissionService.grantPermission(documentId, request, authentication, httpRequest);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/documents/{id}/revoke/{permissionId}")
    public ResponseEntity<?> revokePermission(
            @PathVariable("id") Long documentId,
            @PathVariable("permissionId") Long permissionId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        permissionService.revokePermission(documentId, permissionId, authentication, httpRequest);
        return ResponseEntity.ok(Map.of("message", "Permission revoked successfully. Access terminated immediately."));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<List<PermissionResponse>> getDocumentPermissions(
            @PathVariable("id") Long documentId,
            Authentication authentication) {

        List<PermissionResponse> list = permissionService.getDocumentPermissions(documentId, authentication);
        return ResponseEntity.ok(list);
    }
}
