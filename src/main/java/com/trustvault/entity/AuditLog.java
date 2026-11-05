package com.trustvault.entity;

import com.trustvault.enums.AuditAction;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_username", columnList = "username"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_res", columnList = "resource_type, resource_id"),
    @Index(name = "idx_audit_success", columnList = "success")
})
public class AuditLog {

    private static final DateTimeFormatter HASH_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "user_role", length = 30)
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 50)
    private String resourceId;

    @Column(nullable = false, length = 1000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    public AuditLog() {}

    public AuditLog(Long userId, String username, String userRole, AuditAction action,
                    String resourceType, String resourceId, String details,
                    String ipAddress, boolean success, String previousHash) {
        this.timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        this.userId = userId;
        this.username = username != null ? username : "ANONYMOUS";
        this.userRole = userRole != null ? userRole : "NONE";
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId != null ? resourceId : "";
        this.details = details != null ? details : "";
        this.ipAddress = ipAddress != null ? ipAddress : "UNKNOWN";
        this.success = success;
        this.previousHash = previousHash != null ? previousHash : "0000000000000000000000000000000000000000000000000000000000000000";
        this.currentHash = calculateHash();
    }

    public String calculateHash() {
        String tsStr = timestamp != null ? timestamp.format(HASH_TIMESTAMP_FORMAT) : "";
        String data = (id != null ? id : "") + "|" +
                tsStr + "|" +
                (userId != null ? userId : "") + "|" +
                username + "|" +
                (userRole != null ? userRole : "") + "|" +
                action.name() + "|" +
                resourceType + "|" +
                (resourceId != null ? resourceId : "") + "|" +
                details + "|" +
                (ipAddress != null ? ipAddress : "") + "|" +
                success + "|" +
                previousHash;
        return sha256Hex(data);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }
}
