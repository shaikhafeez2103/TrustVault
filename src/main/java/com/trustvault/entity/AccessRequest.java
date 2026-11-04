package com.trustvault.entity;

import com.trustvault.enums.AccessStatus;
import com.trustvault.enums.PermissionType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "access_requests", indexes = {
    @Index(name = "idx_req_doc", columnList = "document_id"),
    @Index(name = "idx_req_user", columnList = "requester_id"),
    @Index(name = "idx_req_status", columnList = "status")
})
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_permission", nullable = false, length = 20)
    private PermissionType requestedPermission;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessStatus status = AccessStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AccessRequest() {}

    public AccessRequest(Document document, User requester, PermissionType requestedPermission, String reason) {
        this.document = document;
        this.requester = requester;
        this.requestedPermission = requestedPermission;
        this.reason = reason;
        this.status = AccessStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }

    public PermissionType getRequestedPermission() { return requestedPermission; }
    public void setRequestedPermission(PermissionType requestedPermission) { this.requestedPermission = requestedPermission; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public AccessStatus getStatus() { return status; }
    public void setStatus(AccessStatus status) { this.status = status; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
