package com.trustvault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.dto.LoginRequest;
import com.trustvault.dto.AuthResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DocumentUploadSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String ownerToken;
    private String viewerToken;

    @BeforeAll
    void obtainTokens() throws Exception {
        adminToken = loginAndGetToken("admin", "Admin@2026Test");
        ownerToken = loginAndGetToken("owner", "Owner@123");
        viewerToken = loginAndGetToken("viewer", "Viewer@123");
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
    void uploadAllowedPdfSucceeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", "dummy content".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Test PDF")
                .param("description", "desc")
                .param("category", "General")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", "dummy".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Viewer PDF")
                .param("description", "desc")
                .param("category", "General")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadExceedsSizeLimitRejected() throws Exception {
        // Create a file just over 10 MB (10 * 1024 * 1024 + 1 bytes)
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Big File")
                .param("description", "desc")
                .param("category", "General")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadExecutableRejectedEvenIfMimeFaked() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/pdf", "MZ...".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Exe File")
                .param("description", "desc")
                .param("category", "General")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }
}
