package com.trustvault.controller;

import com.trustvault.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @Autowired
    private JwtTokenProvider tokenProvider;

    private boolean isUserAuthenticated(HttpServletRequest request) {
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("trustvault_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        return StringUtils.hasText(token) && tokenProvider.validateToken(token);
    }

    @GetMapping("/")
    public String root(HttpServletRequest request) {
        if (isUserAuthenticated(request)) {
            return "redirect:/dashboard.html";
        }
        return "redirect:/login.html";
    }

    @GetMapping("/index.html")
    public String index(HttpServletRequest request) {
        if (isUserAuthenticated(request)) {
            return "redirect:/dashboard.html";
        }
        return "redirect:/login.html";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request) {
        if (isUserAuthenticated(request)) {
            return "redirect:/dashboard.html";
        }
        return "redirect:/login.html";
    }
}
