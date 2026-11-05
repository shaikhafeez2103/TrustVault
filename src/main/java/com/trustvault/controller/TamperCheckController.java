package com.trustvault.controller;

import com.trustvault.dto.TamperCheckResult;
import com.trustvault.enums.AuditAction;
import com.trustvault.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tamper")
public class TamperCheckController {

    @Autowired
    private AuditService auditService;

    @GetMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TamperCheckResult> verifyIntegrity(Authentication authentication, HttpServletRequest request) {
        String clientIp = request != null ? request.getRemoteAddr() : "UNKNOWN";
        TamperCheckResult result = auditService.verifyAuditTrailIntegrity();

        auditService.logSecurityEvent(
                authentication != null ? authentication.getName() : "ADMIN",
                AuditAction.TAMPER_VERIFICATION_CHECK,
                "AUDIT_TRAIL",
                null,
                "Cryptographic tamper verification executed. Result valid: " + result.isValid(),
                clientIp,
                result.isValid()
        );

        return ResponseEntity.ok(result);
    }
}
