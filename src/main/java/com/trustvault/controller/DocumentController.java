package com.trustvault.controller;

import com.trustvault.dto.DocumentResponse;
import com.trustvault.entity.DocumentVersion;
import com.trustvault.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", defaultValue = "General") String category,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest request) {

        DocumentResponse response = documentService.uploadDocument(title, description, category, file, authentication, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocumentResponse>> searchDocuments(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "owner", required = false) String ownerUsername,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {

        List<DocumentResponse> results = documentService.searchDocuments(query, category, ownerUsername, startDate, endDate, authentication);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/my")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments(Authentication authentication) {
        List<DocumentResponse> list = documentService.getMyDocuments(authentication);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/shared")
    public ResponseEntity<List<DocumentResponse>> getSharedWithMe(Authentication authentication) {
        List<DocumentResponse> list = documentService.getSharedWithMe(authentication);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<DocumentResponse>> getDeletedDocuments(Authentication authentication) {
        List<DocumentResponse> list = documentService.getDeletedDocuments(authentication);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<DocumentResponse> restoreDocument(
            @PathVariable("id") Long id,
            Authentication authentication,
            HttpServletRequest request) {
        DocumentResponse restored = documentService.restoreDocument(id, authentication, request);
        return ResponseEntity.ok(restored);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable("id") Long id,
            Authentication authentication,
            HttpServletRequest request) {

        DocumentResponse doc = documentService.getDocumentById(id, authentication, request);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable("id") Long id,
            @RequestParam(value = "watermark", defaultValue = "true") boolean watermark,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline,
            Authentication authentication,
            HttpServletRequest request) {

        DocumentService.ResourceAndMetadata res = documentService.downloadDocument(id, watermark, authentication, request);
        String safeFilename = res.getFilename() != null
                ? res.getFilename().replaceAll("[\\r\\n\"\\\\]", "_")
                : "document";
        String dispositionType = inline ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(res.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + safeFilename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(res.getResource());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication,
            HttpServletRequest request) {

        String title = body.get("title");
        String description = body.get("description");
        String category = body.get("category");

        DocumentResponse updated = documentService.updateDocument(id, title, description, category, authentication, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadNewVersion(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeSummary", required = false) String changeSummary,
            Authentication authentication,
            HttpServletRequest request) {

        DocumentResponse updated = documentService.uploadNewVersion(id, file, changeSummary, authentication, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<DocumentVersion>> getDocumentVersions(
            @PathVariable("id") Long id,
            Authentication authentication) {

        List<DocumentVersion> versions = documentService.getDocumentVersions(id, authentication);
        return ResponseEntity.ok(versions);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable("id") Long id,
            Authentication authentication,
            HttpServletRequest request) {

        documentService.deleteDocument(id, authentication, request);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
    }
}
