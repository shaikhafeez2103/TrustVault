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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String viewerToken;
    private String adminToken;
    private Document confidentialDoc;

    @BeforeEach
    public void setup() throws Exception {
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
}
