package com.trustvault.security;

import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.User;
import com.trustvault.enums.PermissionType;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service("documentSecurityService")
@Transactional(readOnly = true)
public class DocumentSecurityService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    public boolean canRead(Authentication authentication, Long documentId) {
        // Admins should have full read access across all documents
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        Optional<Document> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty() || docOpt.get().isDeleted()) {
            return false;
        }

        Document document = docOpt.get();

        // 1. Owner always has read access
        if (document.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // 2. Check individual active, non-expired permission
        Optional<DocumentPermission> permOpt = permissionRepository.findByDocumentAndUserAndActiveTrue(document, user);
        if (permOpt.isPresent()) {
            DocumentPermission perm = permOpt.get();
            if (perm.getExpiresAt() == null || perm.getExpiresAt().isAfter(LocalDateTime.now())) {
                return true;
            }
        }

        return false;
    }

    public boolean canEdit(Authentication authentication, Long documentId) {
        // Admins have full edit access across all documents
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        Optional<Document> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty() || docOpt.get().isDeleted()) {
            return false;
        }

        Document document = docOpt.get();

        // 1. Owner always has edit access
        if (document.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // 2. Check individual active, non-expired EDIT permission
        Optional<DocumentPermission> permOpt = permissionRepository.findByDocumentAndUserAndActiveTrue(document, user);
        if (permOpt.isPresent()) {
            DocumentPermission perm = permOpt.get();
            if (perm.getPermissionType() == PermissionType.EDIT) {
                if (perm.getExpiresAt() == null || perm.getExpiresAt().isAfter(LocalDateTime.now())) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean canDelete(Authentication authentication, Long documentId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        Optional<Document> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty() || docOpt.get().isDeleted()) {
            return false;
        }

        Document document = docOpt.get();

        // Only document owner (or Admin for emergency maintenance) can delete
        return document.getOwner().getId().equals(user.getId()) || user.getRole() == RoleName.ROLE_ADMIN;
    }

    public boolean isOwner(Authentication authentication, Long documentId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<Document> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty() || docOpt.get().isDeleted()) {
            return false;
        }

        return docOpt.get().getOwner().getUsername().equalsIgnoreCase(username);
    }
}
