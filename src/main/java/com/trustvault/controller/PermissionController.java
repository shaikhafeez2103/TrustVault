package com.trustvault.controller;

import com.trustvault.dto.PermissionGrantRequest;
import com.trustvault.dto.PermissionResponse;
import com.trustvault.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

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
