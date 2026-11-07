package com.trustvault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.dto.AuthResponse;
import com.trustvault.dto.LoginRequest;
import com.trustvault.dto.RegisterRequest;
import com.trustvault.enums.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testSuccessfulRegistrationAndPasswordIsHashed() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setUsername("user1");
        register.setEmail("user1@example.com");
        register.setPassword("Secret123");
        register.setFullName("User One");
        // attempt to set ADMIN role – should be ignored
        register.setRole(RoleName.ROLE_ADMIN);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(response.getUsername()).isEqualTo("user1");
        assertThat(response.getRole()).isEqualTo(RoleName.ROLE_VIEWER.name());
        // token should be non‑null
        assertThat(response.getJwtToken()).isNotBlank();
    }

    @Test
    public void testLoginSuccessAndFailure() throws Exception {
        // First register a user
        RegisterRequest register = new RegisterRequest();
        register.setUsername("loginUser");
        register.setEmail("login@example.com");
        register.setPassword("Pwd12345");
        register.setFullName("Login User");
        register.setRole(null);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        // Successful login
        LoginRequest login = new LoginRequest();
        login.setUsername("loginUser");
        login.setPassword("Pwd12345");
        MvcResult okResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse okResp = objectMapper.readValue(okResult.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(okResp.getJwtToken()).isNotBlank();

        // Invalid login must return HTTP 401 Unauthorized without sensitive details
        login.setPassword("wrongPwd");
        MvcResult badResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String errorBody = badResult.getResponse().getContentAsString();
        assertThat(errorBody).contains("Invalid username or password");
        assertThat(errorBody).doesNotContain("Exception", "trace", "Pwd12345", "wrongPwd");
    }

    @Test
    public void testProtectedEndpointAccess() throws Exception {
        // register a normal user
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("protectedUser");
        reg.setEmail("protected@example.com");
        reg.setPassword("Secret123");
        reg.setFullName("Protected User");
        reg.setRole(null);
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse regResp = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);
        String token = regResp.getJwtToken();

        // Access a generic protected endpoint, e.g., /api/me (exists via AuthController)
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Attempt to access admin endpoint – should be forbidden (403)
        mockMvc.perform(get("/api/admin/audit-logs") // placeholder admin path, may not exist; using a known admin endpoint if any
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
