package com.trustvault.dto;

import com.trustvault.enums.AccessStatus;
import com.trustvault.enums.PermissionType;
import java.time.LocalDateTime;

public class AccessRequestResponse {
    private Long id;
    private Long documentId;
    private String documentTitle;
    private Long requesterId;
    private String requesterUsername;
    private String requesterEmail;
    private PermissionType requestedPermission;
    private String reason;
    private AccessStatus status;
    private String reviewedByUsername;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public AccessRequestResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }

    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }

    public String getRequesterUsername() { return requesterUsername; }
    public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }

    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }

    public PermissionType getRequestedPermission() { return requestedPermission; }
    public void setRequestedPermission(PermissionType requestedPermission) { this.requestedPermission = requestedPermission; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public AccessStatus getStatus() { return status; }
    public void setStatus(AccessStatus status) { this.status = status; }

    public String getReviewedByUsername() { return reviewedByUsername; }
    public void setReviewedByUsername(String reviewedByUsername) { this.reviewedByUsername = reviewedByUsername; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
