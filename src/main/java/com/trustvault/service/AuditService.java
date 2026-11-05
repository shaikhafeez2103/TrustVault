package com.trustvault.service;

import com.trustvault.dto.AuditLogResponse;
import com.trustvault.dto.SecurityStatsResponse;
import com.trustvault.dto.TamperCheckResult;
import com.trustvault.entity.AuditLog;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.repository.AuditLogRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AuditService {

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public synchronized AuditLog logSecurityEvent(String username, AuditAction action, String resourceType,
                                                  String resourceId, String details, String ipAddress,
                                                  boolean success) {
        String roleName = "NONE";
        Long userId = null;

        if (username != null && !username.equalsIgnoreCase("ANONYMOUS")) {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                userId = userOpt.get().getId();
                roleName = userOpt.get().getRole().name();
            }
        }

        // Retrieve last audit log for tamper-evident hash chaining
        Optional<AuditLog> lastLogOpt = auditLogRepository.findTopByOrderByIdDesc();
        String previousHash = lastLogOpt.map(AuditLog::getCurrentHash).orElse(GENESIS_HASH);

        AuditLog log = new AuditLog(userId, username, roleName, action, resourceType,
                resourceId, details, ipAddress, success, previousHash);

        // Pre-save to acquire ID
        AuditLog saved = auditLogRepository.saveAndFlush(log);

        // Re-calculate hash with generated ID and update
        saved.setCurrentHash(saved.calculateHash());
        return auditLogRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public TamperCheckResult verifyAuditTrailIntegrity() {
        List<AuditLog> chain = auditLogRepository.findAllByOrderByIdAsc();
        if (chain.isEmpty()) {
            return new TamperCheckResult(true, 0, null, "Audit log is empty. Genesis integrity verified.");
        }

        String expectedPreviousHash = GENESIS_HASH;

        for (int i = 0; i < chain.size(); i++) {
            AuditLog current = chain.get(i);

            // 1. Verify previous hash pointer
            if (!current.getPreviousHash().equals(expectedPreviousHash)) {
                String error = String.format("Chain linkage broken at Record ID %d! Expected prevHash: %s, Found: %s",
                        current.getId(), expectedPreviousHash, current.getPreviousHash());
                return new TamperCheckResult(false, chain.size(), current.getId(), error);
            }

            // 2. Verify current record content hash
            String recomputedHash = current.calculateHash();
            if (!current.getCurrentHash().equals(recomputedHash)) {
                String error = String.format("Data tampering detected at Record ID %d! Record hash mismatch. Stored: %s, Computed: %s",
                        current.getId(), current.getCurrentHash(), recomputedHash);
                return new TamperCheckResult(false, chain.size(), current.getId(), error);
            }

            expectedPreviousHash = current.getCurrentHash();
        }

        return new TamperCheckResult(true, chain.size(), null,
                "Cryptographic integrity verified. All " + chain.size() + " audit records are tamper-free and correctly chained.");
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogs(AuditAction action, String username, String resourceType,
                                               Boolean success, LocalDateTime startDate, LocalDateTime endDate) {
        List<AuditLog> logs = auditLogRepository.searchAuditLogs(action, username, resourceType, success, startDate, endDate);
        return logs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SecurityStatsResponse getSecurityStats() {
        SecurityStatsResponse stats = new SecurityStatsResponse();
        LocalDateTime past24h = LocalDateTime.now().minusHours(24);

        stats.setTotalDocuments(documentRepository.findByDeletedFalse().size());
        stats.setTotalAuditLogs(auditLogRepository.count());
        stats.setFailedLogins24h(auditLogRepository.countByActionAndTimestampAfter(AuditAction.AUTH_LOGIN_FAILED, past24h));
        stats.setUnauthorizedAccessAttempts24h(auditLogRepository.countByActionAndTimestampAfter(AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT, past24h));
        stats.setActivePermissions(permissionRepository.count());

        TamperCheckResult integrity = verifyAuditTrailIntegrity();
        stats.setTamperDetected(!integrity.isValid());

        // Flag suspicious activity if failed attempts exceed threshold (e.g. >= 5 in 24h) or tampering detected
        boolean suspicious = !integrity.isValid() || (stats.getFailedLogins24h() + stats.getUnauthorizedAccessAttempts24h() >= 5);
        stats.setSuspiciousActivityFlag(suspicious);

        return stats;
    }

    public AuditLogResponse mapToResponse(AuditLog log) {
        AuditLogResponse res = new AuditLogResponse();
        res.setId(log.getId());
        res.setTimestamp(log.getTimestamp());
        res.setUserId(log.getUserId());
        res.setUsername(log.getUsername());
        res.setUserRole(log.getUserRole());
        res.setAction(log.getAction());
        res.setResourceType(log.getResourceType());
        res.setResourceId(log.getResourceId());
        res.setDetails(log.getDetails());
        res.setIpAddress(log.getIpAddress());
        res.setSuccess(log.isSuccess());
        res.setPreviousHash(log.getPreviousHash());
        res.setCurrentHash(log.getCurrentHash());
        return res;
    }
}
