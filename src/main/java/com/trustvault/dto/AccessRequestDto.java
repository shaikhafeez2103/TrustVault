package com.trustvault.dto;

import com.trustvault.enums.PermissionType;
import jakarta.validation.constraints.NotNull;

public class AccessRequestDto {
    @NotNull
    private Long documentId;

    @NotNull
    private PermissionType requestedPermission;

    private String reason;

    public AccessRequestDto() {}

    public AccessRequestDto(Long documentId, PermissionType requestedPermission, String reason) {
        this.documentId = documentId;
        this.requestedPermission = requestedPermission;
        this.reason = reason;
    }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public PermissionType getRequestedPermission() { return requestedPermission; }
    public void setRequestedPermission(PermissionType requestedPermission) { this.requestedPermission = requestedPermission; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
