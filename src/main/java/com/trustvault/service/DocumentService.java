package com.trustvault.service;

import com.trustvault.dto.DocumentResponse;
import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.DocumentVersion;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.PermissionType;
import com.trustvault.enums.RoleName;
import com.trustvault.exception.ResourceNotFoundException;
import com.trustvault.exception.UnauthorizedAccessException;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.DocumentVersionRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.security.DocumentSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private WatermarkService watermarkService;

    @Autowired
    private DocumentSecurityService documentSecurityService;

    @Transactional
    public DocumentResponse uploadDocument(String title, String description, String category,
                                          MultipartFile file, Authentication authentication,
                                          HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        User user = getCurrentUser(authentication);

        String checksum = fileStorageService.calculateChecksum(file);
        String storageFilename = fileStorageService.storeFile(file);

        Document doc = new Document(
                title,
                description,
                category != null ? category : "General",
                file.getOriginalFilename(),
                storageFilename,
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                file.getSize(),
                checksum,
                user
        );

        Document savedDoc = documentRepository.save(doc);

        // Record initial version 1
        DocumentVersion version = new DocumentVersion(
                savedDoc,
                1,
                storageFilename,
                file.getOriginalFilename(),
                file.getSize(),
                checksum,
                "Initial document upload",
                user
        );
        versionRepository.save(version);

        auditService.logSecurityEvent(
                user.getUsername(),
                AuditAction.DOCUMENT_UPLOAD,
                "DOCUMENT",
                savedDoc.getId().toString(),
                "Uploaded new document: '" + title + "' (checksum: " + checksum.substring(0, 16) + "...)",
                clientIp,
                true
        );

        return mapToResponse(savedDoc, user);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> searchDocuments(String query, String category, String ownerUsername,
                                                 LocalDateTime startDate, LocalDateTime endDate,
                                                 Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<Document> allMatches = documentRepository.searchDocuments(query, category, ownerUsername, startDate, endDate);

        // Security Filter: Return only documents the user is authorized to read (or Admin)
        List<DocumentResponse> authorizedResponses = new ArrayList<>();
        for (Document doc : allMatches) {
            if (documentSecurityService.canRead(authentication, doc.getId()) || currentUser.getRole() == RoleName.ROLE_ADMIN) {
                authorizedResponses.add(mapToResponse(doc, currentUser));
            }
        }
        return authorizedResponses;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getMyDocuments(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<Document> docs = documentRepository.findByOwnerAndDeletedFalse(currentUser);
        return docs.stream().map(d -> mapToResponse(d, currentUser)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getSharedWithMe(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<Document> docs = documentRepository.findSharedWithUser(currentUser);
        return docs.stream().map(d -> mapToResponse(d, currentUser)).collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponse getDocumentById(Long id, Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        User currentUser = getCurrentUser(authentication);

        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        // Strict Backend IDOR Authorization Check
        if (!documentSecurityService.canRead(authentication, id)) {
            auditService.logSecurityEvent(
                    currentUser.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "DOCUMENT",
                    id.toString(),
                    "Blocked IDOR attempt to view document: '" + doc.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Access denied. You do not have permission to view document ID: " + id);
        }

        auditService.logSecurityEvent(
                currentUser.getUsername(),
                AuditAction.DOCUMENT_VIEW,
                "DOCUMENT",
                id.toString(),
                "Viewed document metadata: '" + doc.getTitle() + "'",
                clientIp,
                true
        );

        return mapToResponse(doc, currentUser);
    }

    @Transactional
    public ResourceAndMetadata downloadDocument(Long id, boolean applyWatermark,
                                                Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        User currentUser = getCurrentUser(authentication);

        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        // Strict Backend IDOR Authorization Check
        if (!documentSecurityService.canRead(authentication, id)) {
            auditService.logSecurityEvent(
                    currentUser.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "DOCUMENT",
                    id.toString(),
                    "Blocked IDOR attempt to download document: '" + doc.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Access denied. You do not have permission to download document ID: " + id);
        }

        Resource rawResource = fileStorageService.loadFileAsResource(doc.getStoragePath());
        Resource downloadResource = rawResource;

        // Apply dynamic watermarking for viewers or when requested
        boolean isPdf = doc.getContentType().equalsIgnoreCase("application/pdf") ||
                doc.getOriginalFilename().toLowerCase().endsWith(".pdf");

        boolean shouldWatermark = applyWatermark || currentUser.getRole() == RoleName.ROLE_VIEWER ||
                !doc.getOwner().getId().equals(currentUser.getId());

        if (isPdf && shouldWatermark) {
            try (InputStream is = rawResource.getInputStream()) {
                byte[] watermarkedBytes = watermarkService.applyWatermarkToPdf(is, currentUser.getUsername(), clientIp);
                downloadResource = new ByteArrayResource(watermarkedBytes);
            } catch (Exception e) {
                // Fallback to raw if watermark fails
            }
        }

        auditService.logSecurityEvent(
                currentUser.getUsername(),
                AuditAction.DOCUMENT_DOWNLOAD,
                "DOCUMENT",
                id.toString(),
                "Downloaded file '" + doc.getOriginalFilename() + "' (watermarked: " + shouldWatermark + ")",
                clientIp,
                true
        );

        return new ResourceAndMetadata(downloadResource, doc.getOriginalFilename(), doc.getContentType());
    }

    @Transactional
    public DocumentResponse updateDocument(Long id, String title, String description, String category,
                                          Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        User currentUser = getCurrentUser(authentication);

        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        if (!documentSecurityService.canEdit(authentication, id)) {
            auditService.logSecurityEvent(
                    currentUser.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "DOCUMENT",
                    id.toString(),
                    "Blocked unauthorized edit attempt on document: '" + doc.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Access denied. You do not have edit permission for document ID: " + id);
        }

        if (title != null && !title.isBlank()) doc.setTitle(title);
        if (description != null) doc.setDescription(description);
        if (category != null && !category.isBlank()) doc.setCategory(category);
        doc.setUpdatedAt(LocalDateTime.now());

        Document saved = documentRepository.save(doc);

        auditService.logSecurityEvent(
                currentUser.getUsername(),
                AuditAction.DOCUMENT_EDIT,
                "DOCUMENT",
                id.toString(),
                "Updated document metadata: '" + saved.getTitle() + "'",
                clientIp,
                true
        );

        return mapToResponse(saved, currentUser);
    }

    @Transactional
    public DocumentResponse uploadNewVersion(Long id, MultipartFile file, String changeSummary,
                                            Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        User currentUser = getCurrentUser(authentication);

        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        if (!documentSecurityService.canEdit(authentication, id)) {
            auditService.logSecurityEvent(
                    currentUser.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "DOCUMENT",
                    id.toString(),
                    "Blocked unauthorized version upload attempt on document: '" + doc.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Access denied. You do not have edit permission to upload a new version.");
        }

        String checksum = fileStorageService.calculateChecksum(file);
        String storageFilename = fileStorageService.storeFile(file);

        int newVersionNumber = doc.getCurrentVersion() + 1;
        doc.setCurrentVersion(newVersionNumber);
        doc.setStoragePath(storageFilename);
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setFileSize(file.getSize());
        doc.setContentType(file.getContentType());
        doc.setFileChecksum(checksum);
        doc.setUpdatedAt(LocalDateTime.now());

        Document savedDoc = documentRepository.save(doc);

        DocumentVersion version = new DocumentVersion(
                savedDoc,
                newVersionNumber,
                storageFilename,
                file.getOriginalFilename(),
                file.getSize(),
                checksum,
                changeSummary != null ? changeSummary : "Uploaded version " + newVersionNumber,
                currentUser
        );
        versionRepository.save(version);

        auditService.logSecurityEvent(
                currentUser.getUsername(),
                AuditAction.DOCUMENT_EDIT,
                "DOCUMENT",
                id.toString(),
                "Uploaded new version v" + newVersionNumber + " for '" + savedDoc.getTitle() + "'",
                clientIp,
                true
        );

        return mapToResponse(savedDoc, currentUser);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersion> getDocumentVersions(Long id, Authentication authentication) {
        if (!documentSecurityService.canRead(authentication, id)) {
            throw new UnauthorizedAccessException("Access denied. You cannot view version history for this document.");
        }

        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        return versionRepository.findByDocumentOrderByVersionNumberDesc(doc);
    }

    @Transactional
    public void deleteDocument(Long id, Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        User currentUser = getCurrentUser(authentication);

        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        if (!documentSecurityService.canDelete(authentication, id)) {
            auditService.logSecurityEvent(
                    currentUser.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "DOCUMENT",
                    id.toString(),
                    "Blocked unauthorized deletion attempt on document: '" + doc.getTitle() + "'",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Access denied. Only the document owner can delete this document.");
        }

        doc.setDeleted(true);
        documentRepository.save(doc);

        auditService.logSecurityEvent(
                currentUser.getUsername(),
                AuditAction.DOCUMENT_DELETE,
                "DOCUMENT",
                id.toString(),
                "Soft deleted document: '" + doc.getTitle() + "'",
                clientIp,
                true
        );
    }

    private User getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private DocumentResponse mapToResponse(Document doc, User currentUser) {
        DocumentResponse res = new DocumentResponse();
        res.setId(doc.getId());
        res.setTitle(doc.getTitle());
        res.setDescription(doc.getDescription());
        res.setCategory(doc.getCategory());
        res.setOriginalFilename(doc.getOriginalFilename());
        res.setFileSize(doc.getFileSize());
        res.setContentType(doc.getContentType());
        res.setFileChecksum(doc.getFileChecksum());
        res.setCurrentVersion(doc.getCurrentVersion());
        res.setOwnerId(doc.getOwner().getId());
        res.setOwnerUsername(doc.getOwner().getUsername());
        res.setOwnerFullName(doc.getOwner().getFullName());
        res.setCreatedAt(doc.getCreatedAt());
        res.setUpdatedAt(doc.getUpdatedAt());

        boolean isOwner = doc.getOwner().getId().equals(currentUser.getId());
        res.setOwner(isOwner);

        if (isOwner || currentUser.getRole() == RoleName.ROLE_ADMIN) {
            res.setCanEdit(true);
            res.setCanDelete(true);
            res.setMyPermission("OWNER");
        } else {
            Optional<DocumentPermission> permOpt = permissionRepository.findByDocumentAndUserAndActiveTrue(doc, currentUser);
            if (permOpt.isPresent() && permOpt.get().isCurrentlyValid()) {
                DocumentPermission p = permOpt.get();
                res.setMyPermission(p.getPermissionType().name());
                res.setCanEdit(p.getPermissionType() == PermissionType.EDIT);
                res.setCanDelete(false);
            } else {
                res.setMyPermission("NONE");
                res.setCanEdit(false);
                res.setCanDelete(false);
            }
        }

        return res;
    }

    public static class ResourceAndMetadata {
        private final Resource resource;
        private final String filename;
        private final String contentType;

        public ResourceAndMetadata(Resource resource, String filename, String contentType) {
            this.resource = resource;
            this.filename = filename;
            this.contentType = contentType;
        }

        public Resource getResource() { return resource; }
        public String getFilename() { return filename; }
        public String getContentType() { return contentType; }
    }
}
