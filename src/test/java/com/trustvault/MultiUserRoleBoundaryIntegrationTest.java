package com.trustvault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.dto.AccessRequestDto;
import com.trustvault.dto.AuthResponse;
import com.trustvault.dto.LoginRequest;
import com.trustvault.dto.PermissionGrantRequest;
import com.trustvault.entity.Document;
import com.trustvault.entity.User;
import com.trustvault.enums.PermissionType;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.AccessRequestRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.DocumentVersionRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuthService;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public class MultiUserRoleBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        authService.clearRateLimiter();
        accessRequestRepository.deleteAll();
        permissionRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getJwtToken();
    }

    @Test
    @DisplayName("ROLE-01: Evaluator built-in demo accounts log in independently with correct roles and strict RBAC")
    public void testBuiltinAccountsIndependentLoginAndRBAC() throws Exception {
        // 1. Admin login
        String adminToken = loginAndGetToken("admin", "Admin@2026Test");
        assertThat(adminToken).isNotBlank();

        // Admin can access admin stats
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 2. Owner login
        String ownerToken = loginAndGetToken("owner", "Owner@123");
        assertThat(ownerToken).isNotBlank();

        // Owner cannot access admin stats (403 Forbidden)
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        // 3. Viewer login
        String viewerToken = loginAndGetToken("viewer", "Viewer@123");
        assertThat(viewerToken).isNotBlank();

        // Viewer cannot access admin stats (403 Forbidden)
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // Viewer cannot upload documents (403 Forbidden)
        MockMultipartFile file = new MockMultipartFile("file", "viewer_test.pdf", "application/pdf", "sample".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("title", "Viewer Illegal Upload")
                        .param("description", "Should fail")
                        .param("category", "General")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // 4. Designated demo owner login
        String demoOwnerToken = loginAndGetToken("abdul_shaik_21", "Owner@123");
        assertThat(demoOwnerToken).isNotBlank();

        // Demo owner can upload documents (200 OK)
        MockMultipartFile ownerFile = new MockMultipartFile("file", "demo_owner_test.pdf", "application/pdf", "%PDF-1.4 sample content".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(ownerFile)
                        .param("title", "Demo Owner Upload")
                        .param("description", "Authorized upload")
                        .param("category", "Legal")
                        .header("Authorization", "Bearer " + demoOwnerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE-02: Multi-Owner Document Isolation - Owner Alpha and Owner Beta cannot access each other's private documents")
    public void testMultiOwnerDocumentIsolation() throws Exception {
        // Create Owner Alpha
        User ownerAlpha = userRepository.findByUsername("owner_alpha_test").orElseGet(() ->
                userRepository.save(new User("owner_alpha_test", "alpha@trustvault.local",
                        passwordEncoder.encode("Owner@123"), "Owner Alpha", RoleName.ROLE_OWNER)));

        // Create Owner Beta
        User ownerBeta = userRepository.findByUsername("owner_beta_test").orElseGet(() ->
                userRepository.save(new User("owner_beta_test", "beta@trustvault.local",
                        passwordEncoder.encode("Owner@123"), "Owner Beta", RoleName.ROLE_OWNER)));

        String tokenAlpha = loginAndGetToken("owner_alpha_test", "Owner@123");
        String tokenBeta = loginAndGetToken("owner_beta_test", "Owner@123");

        // Owner Alpha uploads Document Alpha
        MockMultipartFile fileAlpha = new MockMultipartFile("file", "doc_alpha.pdf", "application/pdf", "%PDF-1.4 Alpha private".getBytes());
        MvcResult resAlpha = mockMvc.perform(multipart("/api/documents/upload")
                        .file(fileAlpha)
                        .param("title", "Alpha Confidential Strategy")
                        .param("description", "Alpha proprietary")
                        .param("category", "Strategy")
                        .header("Authorization", "Bearer " + tokenAlpha))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> alphaDocData = objectMapper.readValue(resAlpha.getResponse().getContentAsString(), Map.class);
        Long docAlphaId = ((Number) alphaDocData.get("id")).longValue();

        // Owner Beta uploads Document Beta
        MockMultipartFile fileBeta = new MockMultipartFile("file", "doc_beta.pdf", "application/pdf", "%PDF-1.4 Beta private".getBytes());
        MvcResult resBeta = mockMvc.perform(multipart("/api/documents/upload")
                        .file(fileBeta)
                        .param("title", "Beta Confidential Financials")
                        .param("description", "Beta proprietary")
                        .param("category", "Finance")
                        .header("Authorization", "Bearer " + tokenBeta))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> betaDocData = objectMapper.readValue(resBeta.getResponse().getContentAsString(), Map.class);
        Long docBetaId = ((Number) betaDocData.get("id")).longValue();

        // Owner Beta CANNOT download Owner Alpha's Document Alpha (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + docAlphaId + "/download")
                        .header("Authorization", "Bearer " + tokenBeta))
                .andExpect(status().isForbidden());

        // Owner Alpha CANNOT download Owner Beta's Document Beta (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + docBetaId + "/download")
                        .header("Authorization", "Bearer " + tokenAlpha))
                .andExpect(status().isForbidden());

        // Owner Beta CANNOT grant permissions on Owner Alpha's document (403 Forbidden)
        PermissionGrantRequest grantReq = new PermissionGrantRequest();
        grantReq.setUsername("viewer");
        grantReq.setPermissionType(PermissionType.READ);
        mockMvc.perform(post("/api/permissions/documents/" + docAlphaId + "/grant")
                        .header("Authorization", "Bearer " + tokenBeta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE-03: Access request lifecycle - cross-owner review blocked, true owner approves, viewer downloads, revocation locks immediately")
    public void testAccessRequestAndRevocationLifecycle() throws Exception {
        // Setup Owner Alpha and Owner Beta
        userRepository.findByUsername("owner_alpha_test").orElseGet(() ->
                userRepository.save(new User("owner_alpha_test", "alpha@trustvault.local",
                        passwordEncoder.encode("Owner@123"), "Owner Alpha", RoleName.ROLE_OWNER)));
        userRepository.findByUsername("owner_beta_test").orElseGet(() ->
                userRepository.save(new User("owner_beta_test", "beta@trustvault.local",
                        passwordEncoder.encode("Owner@123"), "Owner Beta", RoleName.ROLE_OWNER)));

        String tokenAlpha = loginAndGetToken("owner_alpha_test", "Owner@123");
        String tokenBeta = loginAndGetToken("owner_beta_test", "Owner@123");
        String tokenViewer = loginAndGetToken("viewer", "Viewer@123");

        // Owner Alpha uploads Document Alpha
        MockMultipartFile fileAlpha = new MockMultipartFile("file", "doc_alpha_review.pdf", "application/pdf", "%PDF-1.4 Alpha content".getBytes());
        MvcResult resAlpha = mockMvc.perform(multipart("/api/documents/upload")
                        .file(fileAlpha)
                        .param("title", "Alpha Review Document")
                        .param("description", "Needs access request")
                        .param("category", "Review")
                        .header("Authorization", "Bearer " + tokenAlpha))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> alphaDocData = objectMapper.readValue(resAlpha.getResponse().getContentAsString(), Map.class);
        Long docAlphaId = ((Number) alphaDocData.get("id")).longValue();

        // 1. Viewer cannot download without permission (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + docAlphaId + "/download")
                        .header("Authorization", "Bearer " + tokenViewer))
                .andExpect(status().isForbidden());

        // 2. Viewer submits access request
        AccessRequestDto reqDto = new AccessRequestDto();
        reqDto.setDocumentId(docAlphaId);
        reqDto.setRequestedPermission(PermissionType.READ);
        reqDto.setReason("Need to review contract for compliance audit");
        MvcResult reqRes = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + tokenViewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> reqData = objectMapper.readValue(reqRes.getResponse().getContentAsString(), Map.class);
        Long requestId = ((Number) reqData.get("id")).longValue();

        // 3. Owner Beta attempts to review/approve Owner Alpha's request -> 403 Forbidden
        mockMvc.perform(post("/api/requests/" + requestId + "/review")
                        .param("approve", "true")
                        .header("Authorization", "Bearer " + tokenBeta))
                .andExpect(status().isForbidden());

        // 4. True Owner Alpha approves the request -> 200 OK
        mockMvc.perform(post("/api/requests/" + requestId + "/review")
                        .param("approve", "true")
                        .header("Authorization", "Bearer " + tokenAlpha))
                .andExpect(status().isOk());

        // 5. Viewer can now download Document Alpha -> 200 OK
        mockMvc.perform(get("/api/documents/" + docAlphaId + "/download")
                        .header("Authorization", "Bearer " + tokenViewer))
                .andExpect(status().isOk());

        // 6. Get permission ID created for Viewer
        MvcResult permListRes = mockMvc.perform(get("/api/permissions/documents/" + docAlphaId)
                        .header("Authorization", "Bearer " + tokenAlpha))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<?> permList = objectMapper.readValue(permListRes.getResponse().getContentAsString(), java.util.List.class);
        assertThat(permList).isNotEmpty();
        Map<?, ?> firstPerm = (Map<?, ?>) permList.get(0);
        Long permId = ((Number) firstPerm.get("id")).longValue();

        // 7. Owner Alpha revokes permission
        mockMvc.perform(delete("/api/permissions/documents/" + docAlphaId + "/revoke/" + permId)
                        .header("Authorization", "Bearer " + tokenAlpha))
                .andExpect(status().isOk());

        // 8. Immediate Revocation: Viewer is immediately blocked from downloading (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + docAlphaId + "/download")
                        .header("Authorization", "Bearer " + tokenViewer))
                .andExpect(status().isForbidden());
    }
}
