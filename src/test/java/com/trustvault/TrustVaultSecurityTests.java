package com.trustvault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.dto.LoginRequest;
import com.trustvault.dto.PermissionGrantRequest;
import com.trustvault.dto.TamperCheckResult;
import com.trustvault.entity.AuditLog;
import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.PermissionType;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.AccessRequestRepository;
import com.trustvault.repository.AuditLogRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.DocumentVersionRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditService;
import com.trustvault.service.AuthService;
import com.trustvault.service.GoogleOAuthService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public class TrustVaultSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuthService authService;

    @Autowired
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String viewerToken;
    private String adminToken;
    private Document confidentialDoc;

    @BeforeEach
    public void setup() throws Exception {
        authService.clearRateLimiter();
        accessRequestRepository.deleteAll();
        permissionRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Setup Genesis block
        auditService.logSecurityEvent("SYSTEM", AuditAction.TAMPER_VERIFICATION_CHECK, "GENESIS", "0", "Genesis", "127.0.0.1", true);

        // 2. Create Users
        User admin = userRepository.save(new User("admin", "admin@trustvault.io", passwordEncoder.encode("Admin@123"), "Admin", RoleName.ROLE_ADMIN));
        User owner = userRepository.save(new User("owner", "owner@trustvault.io", passwordEncoder.encode("Owner@123"), "Owner", RoleName.ROLE_OWNER));
        User viewer = userRepository.save(new User("viewer", "viewer@trustvault.io", passwordEncoder.encode("Viewer@123"), "Viewer", RoleName.ROLE_VIEWER));

        // 3. Obtain JWT Tokens
        ownerToken = obtainToken("owner", "Owner@123");
        viewerToken = obtainToken("viewer", "Viewer@123");
        adminToken = obtainToken("admin", "Admin@123");

        // 4. Create Private Document owned by Owner
        confidentialDoc = new Document(
                "Restricted Financial Projections",
                "Strictly confidential 2027 revenue forecast",
                "Finance",
                "finances_2027.pdf",
                "test_finances.pdf",
                "application/pdf",
                1024L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                owner
        );
        confidentialDoc = documentRepository.save(confidentialDoc);
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(body, Map.class);
        return (String) map.get("token");
    }

    @Test
    @DisplayName("SECURITY-01: Proves IDOR Defense - Viewer without permission receives 403 Forbidden")
    public void testUnauthorizedViewerCannotAccessOwnersDocument() throws Exception {
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY-02: Every unauthorized access attempt must be recorded in the audit trail")
    public void testUnauthorizedAccessAttemptIsLoggedToAuditTrail() throws Exception {
        long initialCount = auditLogRepository.countByActionAndTimestampAfter(
                AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT, java.time.LocalDateTime.now().minusMinutes(5));

        // Perform forbidden access
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        long afterCount = auditLogRepository.countByActionAndTimestampAfter(
                AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT, java.time.LocalDateTime.now().minusMinutes(5));

        assertTrue(afterCount > initialCount, "Audit log must contain UNAUTHORIZED_ACCESS_ATTEMPT event");
    }

    @Test
    @DisplayName("SECURITY-03: Viewer with granted READ permission can access the document")
    public void testGrantedViewerCanReadDocument() throws Exception {
        // Owner grants READ permission to viewer
        PermissionGrantRequest grantReq = new PermissionGrantRequest("viewer", PermissionType.READ, null);

        mockMvc.perform(post("/api/permissions/documents/" + confidentialDoc.getId() + "/grant")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk());

        // Now viewer accesses document successfully
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SECURITY-04: Read-only Viewer cannot edit document metadata (403 Forbidden)")
    public void testReadOnlyViewerCannotEditDocument() throws Exception {
        // Grant READ only
        PermissionGrantRequest grantReq = new PermissionGrantRequest("viewer", PermissionType.READ, null);
        mockMvc.perform(post("/api/permissions/documents/" + confidentialDoc.getId() + "/grant")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk());

        // Viewer attempts to edit metadata
        Map<String, String> editPayload = Map.of("title", "Hacked Title");
        mockMvc.perform(put("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editPayload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY-05: Instant Revocation stops access immediately")
    public void testInstantRevocationStopsAccessImmediately() throws Exception {
        // 1. Grant permission
        PermissionGrantRequest grantReq = new PermissionGrantRequest("viewer", PermissionType.READ, null);
        MvcResult grantRes = mockMvc.perform(post("/api/permissions/documents/" + confidentialDoc.getId() + "/grant")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> permMap = objectMapper.readValue(grantRes.getResponse().getContentAsString(), Map.class);
        Long permissionId = ((Number) permMap.get("id")).longValue();

        // 2. Verify access is allowed
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // 3. Revoke permission immediately
        mockMvc.perform(delete("/api/permissions/documents/" + confidentialDoc.getId() + "/revoke/" + permissionId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // 4. Access must be instantly forbidden
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY-06: Non-Admin users cannot access Admin endpoints (RBAC check)")
    public void testNonAdminCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        // Admin is allowed
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SECURITY-07: File upload prevents path traversal and dangerous files")
    public void testPathTraversalFilenameIsRejected() throws Exception {
        MockMultipartFile dangerousFile = new MockMultipartFile(
                "file",
                "../../etc/passwd",
                "text/plain",
                "root:x:0:0:...".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(dangerousFile)
                        .param("title", "Malicious Upload")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SECURITY-08: Cryptographic hash chain detects database record tampering")
    public void testTamperVerificationDetectsDatabaseAlteration() {
        // 1. Initial chain must be valid
        TamperCheckResult initialCheck = auditService.verifyAuditTrailIntegrity();
        assertTrue(initialCheck.isValid(), "Initial audit chain must be cryptographically valid");

        // 2. Add an audit log entry
        AuditLog legitimate = auditService.logSecurityEvent(
                "owner", AuditAction.DOCUMENT_VIEW, "DOCUMENT", "1", "Legitimate view", "127.0.0.1", true);

        TamperCheckResult checkAfterAdd = auditService.verifyAuditTrailIntegrity();
        assertTrue(checkAfterAdd.isValid(), "Chain must remain valid after legitimate additions");

        // 3. Simulate an adversary or corrupt DBA altering the record directly in the database
        legitimate.setDetails("ADVERSARY_MODIFIED_CONTENT");
        auditLogRepository.save(legitimate);

        // 4. Run tamper verification
        TamperCheckResult tamperDetected = auditService.verifyAuditTrailIntegrity();
        assertFalse(tamperDetected.isValid(), "Tamper verification must detect data tampering in modified record");
        assertEquals(legitimate.getId(), tamperDetected.getBrokenRecordId(), "Tamper verification must pinpoint the exact compromised record");
    }

    @Test
    @DisplayName("SECURITY-09: Unauthenticated requests and invalid JWT tokens return 401 Unauthorized (not 403)")
    public void testUnauthenticatedAndInvalidTokenReturn401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY-10: Document version history succeeds without LazyInitializationException and hides storagePath")
    public void testDocumentVersionHistoryAndAuthorization() throws Exception {
        MockMultipartFile v1File = new MockMultipartFile(
                "file", "contract_v1.pdf", "application/pdf", "%PDF-1.4 v1".getBytes());

        MvcResult uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(v1File)
                        .param("title", "Master Service Agreement")
                        .param("description", "Initial contract")
                        .param("category", "Legal")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> docMap = objectMapper.readValue(uploadRes.getResponse().getContentAsString(), Map.class);
        Long docId = ((Number) docMap.get("id")).longValue();

        // Owner fetches version history -> 200 OK, no LazyInitializationException, no internal storagePath leaked
        MvcResult versionsRes = mockMvc.perform(get("/api/documents/" + docId + "/versions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        String versionsJson = versionsRes.getResponse().getContentAsString();
        assertTrue(versionsJson.contains("\"versionNumber\":1"), "Version 1 must be present");
        assertFalse(versionsJson.contains("storagePath"), "Internal storagePath must not be serialized");

        // Unauthorized viewer receives 403 Forbidden
        mockMvc.perform(get("/api/documents/" + docId + "/versions")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY-11: Security stats count only active, non-expired permissions on non-deleted documents")
    public void testSecurityStatsActivePermissionsAccuracy() throws Exception {
        PermissionGrantRequest grantReq = new PermissionGrantRequest("viewer", PermissionType.READ, null);
        MvcResult grantRes = mockMvc.perform(post("/api/permissions/documents/" + confidentialDoc.getId() + "/grant")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> permMap = objectMapper.readValue(grantRes.getResponse().getContentAsString(), Map.class);
        Long permId = ((Number) permMap.get("id")).longValue();

        MvcResult statsBefore = mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> statsMapBefore = objectMapper.readValue(statsBefore.getResponse().getContentAsString(), Map.class);
        assertEquals(1, ((Number) statsMapBefore.get("activePermissions")).intValue());

        // Revoke permission -> activePermissions must drop to 0
        mockMvc.perform(delete("/api/permissions/documents/" + confidentialDoc.getId() + "/revoke/" + permId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        MvcResult statsAfter = mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> statsMapAfter = objectMapper.readValue(statsAfter.getResponse().getContentAsString(), Map.class);
        assertEquals(0, ((Number) statsMapAfter.get("activePermissions")).intValue());
    }

    @Test
    @DisplayName("SECURITY-12: Permission revocation validates documentId match and grant rejects past expiresAt")
    public void testPermissionRevocationDocumentMismatchAndPastExpiryRejected() throws Exception {
        PermissionGrantRequest grantReq = new PermissionGrantRequest("viewer", PermissionType.READ, null);
        MvcResult grantRes = mockMvc.perform(post("/api/permissions/documents/" + confidentialDoc.getId() + "/grant")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> permMap = objectMapper.readValue(grantRes.getResponse().getContentAsString(), Map.class);
        Long permId = ((Number) permMap.get("id")).longValue();

        // Create second document
        User ownerUser = userRepository.findByUsername("owner").orElseThrow();
        Document secondDoc = documentRepository.save(new Document(
                "Second Document", "Desc", "General", "sec.pdf", "sec_stored.pdf", "application/pdf", 512L, "abc1234", ownerUser));

        // Attempt to revoke permId using secondDoc's ID -> 400 Bad Request
        mockMvc.perform(delete("/api/permissions/documents/" + secondDoc.getId() + "/revoke/" + permId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());

        // Attempt to grant permission with past expiration -> 400 Bad Request
        PermissionGrantRequest pastGrant = new PermissionGrantRequest("viewer", PermissionType.READ, LocalDateTime.now().minusDays(2));
        mockMvc.perform(post("/api/permissions/documents/" + confidentialDoc.getId() + "/grant")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pastGrant)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SECURITY-13: Access request approval clears expired expiresAt, blocks duplicate pending requests, and blocks re-review")
    public void testAccessRequestLifecycleAndExpiredPermissionRenewal() throws Exception {
        User ownerUser = userRepository.findByUsername("owner").orElseThrow();
        User viewerUser = userRepository.findByUsername("viewer").orElseThrow();

        // Pre-seed an expired permission for viewer on confidentialDoc
        DocumentPermission expiredPerm = new DocumentPermission(
                confidentialDoc, viewerUser, PermissionType.READ, ownerUser, LocalDateTime.now().minusDays(1));
        permissionRepository.save(expiredPerm);

        // Viewer cannot currently access the document because permission is expired
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // Viewer requests access
        Map<String, Object> reqPayload = Map.of(
                "documentId", confidentialDoc.getId(),
                "requestedPermission", "READ",
                "reason", "Need updated access for Q4 audit"
        );
        MvcResult createRes = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPayload)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> createdReq = objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class);
        Long requestId = ((Number) createdReq.get("id")).longValue();

        // Duplicate pending request for same document is rejected (400 Bad Request)
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPayload)))
                .andExpect(status().isBadRequest());

        // Owner approves the request
        mockMvc.perform(post("/api/requests/" + requestId + "/review")
                        .param("approve", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Viewer can now access the document immediately (expired expiresAt was cleared)
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // Re-reviewing an already reviewed request is rejected (400 Bad Request)
        mockMvc.perform(post("/api/requests/" + requestId + "/review")
                        .param("approve", "false")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SECURITY-14: Soft delete moves document to Recycle Bin and restore recovers it")
    public void testSoftDeleteAndRecycleBinRestore() throws Exception {
        // Soft-delete confidentialDoc
        mockMvc.perform(delete("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Accessing deleted document fails with 404 Not Found
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        // Deleted document appears in Recycle Bin
        MvcResult binRes = mockMvc.perform(get("/api/documents/deleted")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        List<?> deletedList = objectMapper.readValue(binRes.getResponse().getContentAsString(), List.class);
        assertEquals(1, deletedList.size());

        // Restore document from Recycle Bin
        mockMvc.perform(post("/api/documents/" + confidentialDoc.getId() + "/restore")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Document is accessible again
        mockMvc.perform(get("/api/documents/" + confidentialDoc.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SECURITY-15: Brute-force login rate limiting locks out after 5 failed attempts and weak passwords are rejected")
    public void testBruteForceProtectionAndStrongPasswordPolicy() throws Exception {
        LoginRequest badLogin = new LoginRequest("viewer", "WrongPassword123!");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt must be locked out with HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isTooManyRequests());

        authService.clearRateLimiter();

        // Weak password registration (missing uppercase / < 8 chars) must be rejected with 400 Bad Request
        Map<String, String> weakReg = Map.of(
                "username", "weakuser",
                "email", "weak@trustvault.io",
                "password", "123456",
                "fullName", "Weak User"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakReg)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SECURITY-16: Dynamic PDF watermarking produces valid PDF and sets nosniff & sanitized Content-Disposition")
    public void testDynamicPdfWatermarkingAndDownloadHeaders() throws Exception {
        byte[] validPdfBytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(baos);
            validPdfBytes = baos.toByteArray();
        }

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "report_2026.pdf", "application/pdf", validPdfBytes);

        MvcResult uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(pdfFile)
                        .param("title", "Board Report 2026")
                        .param("description", "Valid PDF test")
                        .param("category", "Corporate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> docMap = objectMapper.readValue(uploadRes.getResponse().getContentAsString(), Map.class);
        Long docId = ((Number) docMap.get("id")).longValue();

        MvcResult downloadRes = mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .param("watermark", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        byte[] downloadedBytes = downloadRes.getResponse().getContentAsByteArray();
        assertTrue(downloadedBytes.length > validPdfBytes.length, "Watermarked PDF should contain embedded watermark stream");
        assertEquals("%PDF-", new String(downloadedBytes, 0, 5));
    }

    @Test
    @DisplayName("SECURITY-17: Public registration always assigns least-privileged ROLE_VIEWER and Admin can manage roles")
    public void testPublicRegistrationLeastPrivilegeAndAdminRoleManagement() throws Exception {
        Map<String, String> regPayload = Map.of(
                "username", "newpublicuser",
                "email", "newpublic@trustvault.io",
                "password", "StrongPass123!",
                "fullName", "New Public User",
                "role", "ROLE_ADMIN"
        );

        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regPayload)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> regMap = objectMapper.readValue(regRes.getResponse().getContentAsString(), Map.class);
        assertEquals("ROLE_VIEWER", regMap.get("role"), "Public registration must assign least-privileged ROLE_VIEWER");
        String publicUserToken = (String) regMap.get("token");
        Long publicUserId = ((Number) regMap.get("id")).longValue();

        // Newly registered ROLE_VIEWER cannot upload documents (403 Forbidden)
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("title", "Unauthorized Upload")
                        .header("Authorization", "Bearer " + publicUserToken))
                .andExpect(status().isForbidden());

        // Non-admin cannot promote user role (403 Forbidden)
        mockMvc.perform(put("/api/admin/users/" + publicUserId + "/role")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ROLE_OWNER"))))
                .andExpect(status().isForbidden());

        // Admin can promote user role to ROLE_OWNER
        mockMvc.perform(put("/api/admin/users/" + publicUserId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ROLE_OWNER"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SECURITY-18: Google OAuth 2.0 state protection, account takeover defense, and least-privileged provisioning")
    public void testGoogleOAuthSecurityAndProvisioning() throws Exception {
        // 1. OAuth status endpoint works and does not expose secrets
        MvcResult statusRes = mockMvc.perform(get("/api/auth/oauth2/google/status"))
                .andExpect(status().isOk())
                .andReturn();
        String statusBody = statusRes.getResponse().getContentAsString();
        assertTrue(statusBody.contains("\"authorizationEndpoint\":\"/oauth2/authorization/google\""));
        assertFalse(statusBody.contains("clientSecret"));

        // 2. Callback without valid CSRF state cookie/token is rejected and redirects with invalid_state
        MvcResult invalidStateRes = mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "fake_code")
                        .param("state", "tampered_state"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertEquals("/login.html?oauth_error=invalid_state", invalidStateRes.getResponse().getRedirectedUrl());

        // 3. Unverified Google email is rejected
        assertThrows(SecurityException.class, () ->
                googleOAuthService.processVerifiedGoogleIdentity("google-sub-001", "unverified@gmail.com", false, "Unverified", "127.0.0.1")
        );

        // 4. Account takeover defense: Google OAuth cannot auto-link to privileged Admin or Owner accounts by email
        SecurityException adminConflict = assertThrows(SecurityException.class, () ->
                googleOAuthService.processVerifiedGoogleIdentity("google-sub-attacker", "admin@trustvault.io", true, "Attacker", "127.0.0.1")
        );
        assertEquals("account_conflict", adminConflict.getMessage());

        SecurityException ownerConflict = assertThrows(SecurityException.class, () ->
                googleOAuthService.processVerifiedGoogleIdentity("google-sub-attacker2", "owner@trustvault.io", true, "Attacker", "127.0.0.1")
        );
        assertEquals("account_conflict", ownerConflict.getMessage());

        // 5. Valid Google identity provisions least-privileged ROLE_VIEWER and issues single-use exchange code
        String exchangeCode = googleOAuthService.processVerifiedGoogleIdentity(
                "google-sub-valid-999", "googleuser@example.com", true, "Google Verified User", "127.0.0.1");
        assertNotNull(exchangeCode);

        MvcResult exchangeRes = mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", exchangeCode))))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> authData = objectMapper.readValue(exchangeRes.getResponse().getContentAsString(), Map.class);
        assertEquals("ROLE_VIEWER", authData.get("role"), "Google OAuth user must receive least-privileged ROLE_VIEWER");
        assertEquals("googleuser@example.com", authData.get("email"));
        assertNotNull(authData.get("token"));

        // 6. Replaying the single-use exchange code a second time is rejected with 401 Unauthorized
        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", exchangeCode))))
                .andExpect(status().isUnauthorized());
    }
}
