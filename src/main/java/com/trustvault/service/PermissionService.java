package com.trustvault.service;

import com.trustvault.dto.PermissionGrantRequest;
import com.trustvault.dto.PermissionResponse;
import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.RoleName;
import com.trustvault.exception.ResourceNotFoundException;
import com.trustvault.exception.UnauthorizedAccessException;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.security.DocumentSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DocumentSecurityService documentSecurityService;

    @Transactional
    public PermissionResponse grantPermission(Long documentId, PermissionGrantRequest request,
                                              Authentication authentication, HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "UNKNOWN";
        User grantor = getCurrentUser(authentication);

        Document document = documentRepository.findById(documentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        // Only Document Owner (or Admin) can grant permissions
        if (!document.getOwner().getId().equals(grantor.getId()) && grantor.getRole() != RoleName.ROLE_ADMIN) {
            auditService.logSecurityEvent(
                    grantor.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "PERMISSION",
                    documentId.toString(),
                    "Unauthorized attempt to grant permission on document '" + document.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Only the document owner can grant permissions.");
        }

        User grantee = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User to grant permission to not found: " + request.getUsername()));

        if (grantee.getId().equals(document.getOwner().getId())) {
            throw new IllegalArgumentException("Cannot grant permissions to the document owner.");
        }

        // Check if an existing permission exists
        Optional<DocumentPermission> existingOpt = permissionRepository.findByDocumentAndUser(document, grantee);
        DocumentPermission permission;

        if (existingOpt.isPresent()) {
            permission = existingOpt.get();
            permission.setActive(true);
            permission.setPermissionType(request.getPermissionType());
            permission.setGrantedBy(grantor);
            permission.setExpiresAt(request.getExpiresAt());
            permission.setGrantedAt(LocalDateTime.now());
            permission.setRevokedAt(null);
        } else {
            permission = new DocumentPermission(
                    document,
                    grantee,
                    request.getPermissionType(),
                    grantor,
                    request.getExpiresAt()
            );
        }

        DocumentPermission saved = permissionRepository.save(permission);

        String expiryInfo = request.getExpiresAt() != null ? " (Expires: " + request.getExpiresAt() + ")" : " (Permanent)";
        auditService.logSecurityEvent(
                grantor.getUsername(),
                AuditAction.PERMISSION_GRANTED,
                "PERMISSION",
                saved.getId().toString(),
                "Granted " + request.getPermissionType() + " permission to user '" + grantee.getUsername() +
                        "' on document '" + document.getTitle() + "'" + expiryInfo,
                clientIp,
                true
        );

        return mapToResponse(saved);
    }

    @Transactional
    public void revokePermission(Long documentId, Long permissionId,
                                Authentication authentication, HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "UNKNOWN";
        User revoker = getCurrentUser(authentication);

        DocumentPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission record not found with ID: " + permissionId));

        Document document = permission.getDocument();

        // Only Document Owner (or Admin) can revoke permissions
        if (!document.getOwner().getId().equals(revoker.getId()) && revoker.getRole() != RoleName.ROLE_ADMIN) {
            auditService.logSecurityEvent(
                    revoker.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "PERMISSION",
                    permissionId.toString(),
                    "Unauthorized attempt to revoke permission on document '" + document.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Only the document owner can revoke permissions.");
        }

        // Immediate access revocation!
        permission.setActive(false);
        permission.setRevokedAt(LocalDateTime.now());
        permissionRepository.save(permission);

        auditService.logSecurityEvent(
                revoker.getUsername(),
                AuditAction.PERMISSION_REVOKED,
                "PERMISSION",
                permission.getId().toString(),
                "REVOKED " + permission.getPermissionType() + " permission from user '" +
                        permission.getUser().getUsername() + "' on document '" + document.getTitle() + "'. Immediate lock applied.",
                clientIp,
                true
        );
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getDocumentPermissions(Long documentId, Authentication authentication) {
        Document document = documentRepository.findById(documentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        User currentUser = getCurrentUser(authentication);
        if (!document.getOwner().getId().equals(currentUser.getId()) && currentUser.getRole() != RoleName.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("Only the document owner or admin can view document permissions.");
        }

        List<DocumentPermission> perms = permissionRepository.findByDocument(document);
        return perms.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private User getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private PermissionResponse mapToResponse(DocumentPermission p) {
        PermissionResponse res = new PermissionResponse();
        res.setId(p.getId());
        res.setDocumentId(p.getDocument().getId());
        res.setDocumentTitle(p.getDocument().getTitle());
        res.setUserId(p.getUser().getId());
        res.setUsername(p.getUser().getUsername());
        res.setUserFullName(p.getUser().getFullName());
        res.setUserEmail(p.getUser().getEmail());
        res.setPermissionType(p.getPermissionType());
        res.setGrantedByUsername(p.getGrantedBy().getUsername());
        res.setActive(p.isCurrentlyValid());
        res.setExpiresAt(p.getExpiresAt());
        res.setGrantedAt(p.getGrantedAt());
        res.setRevokedAt(p.getRevokedAt());
        return res;
    }
}
