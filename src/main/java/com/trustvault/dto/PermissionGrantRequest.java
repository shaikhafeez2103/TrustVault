package com.trustvault.dto;

import com.trustvault.enums.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class PermissionGrantRequest {
    @NotBlank
    private String username;

    @NotNull
    private PermissionType permissionType;

    private LocalDateTime expiresAt;

    public PermissionGrantRequest() {}

    public PermissionGrantRequest(String username, PermissionType permissionType, LocalDateTime expiresAt) {
        this.username = username;
        this.permissionType = permissionType;
        this.expiresAt = expiresAt;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public PermissionType getPermissionType() { return permissionType; }
    public void setPermissionType(PermissionType permissionType) { this.permissionType = permissionType; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
