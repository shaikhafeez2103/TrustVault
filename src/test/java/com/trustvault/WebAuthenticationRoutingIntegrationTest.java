package com.trustvault;

import com.trustvault.entity.User;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.UserRepository;
import com.trustvault.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public class WebAuthenticationRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String validToken;

    @BeforeEach
    public void setup() {
        User user = userRepository.findByUsername("routing_test_user").orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername("routing_test_user");
            newUser.setEmail("routing_test@example.com");
            newUser.setPassword(passwordEncoder.encode("Password123!"));
            newUser.setFullName("Routing Test User");
            newUser.setRole(RoleName.ROLE_VIEWER);
            return userRepository.save(newUser);
        });

        validToken = tokenProvider.generateTokenForUser(user);
    }

    @Test
    public void testRootUrlUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    public void testIndexHtmlUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    public void testDashboardRouteUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    public void testLoginPageAccessiblePublicly() throws Exception {
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk());
    }

    @Test
    public void testRootUrlWithValidTokenRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/")
                .cookie(new Cookie("trustvault_token", validToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard.html"));
    }

    @Test
    public void testDashboardRouteWithValidTokenIsAccessible() throws Exception {
        mockMvc.perform(get("/dashboard.html")
                .cookie(new Cookie("trustvault_token", validToken)))
                .andExpect(status().isOk());
    }
}
