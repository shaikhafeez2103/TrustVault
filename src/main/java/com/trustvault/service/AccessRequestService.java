package com.trustvault.service;

import com.trustvault.dto.AccessRequestDto;
import com.trustvault.dto.AccessRequestResponse;
import com.trustvault.entity.AccessRequest;
import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.User;
import com.trustvault.enums.AccessStatus;
import com.trustvault.enums.AuditAction;
import com.trustvault.exception.ResourceNotFoundException;
import com.trustvault.exception.UnauthorizedAccessException;
import com.trustvault.repository.AccessRequestRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AccessRequestService {

    @Autowired
    private AccessRequestRepository requestRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private AuditService auditService;

    @Transactional
    public AccessRequestResponse createRequest(AccessRequestDto dto, Authentication authentication, HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "UNKNOWN";
        User requester = getCurrentUser(authentication);

        Document doc = documentRepository.findById(dto.getDocumentId())
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + dto.getDocumentId()));

        if (doc.getOwner().getId().equals(requester.getId())) {
            throw new IllegalArgumentException("You are already the owner of this document.");
        }

        AccessRequest req = new AccessRequest(
                doc,
                requester,
                dto.getRequestedPermission(),
                dto.getReason()
        );

        AccessRequest saved = requestRepository.save(req);

        auditService.logSecurityEvent(
                requester.getUsername(),
                AuditAction.ACCESS_REQUEST_SUBMITTED,
                "ACCESS_REQUEST",
                saved.getId().toString(),
                "User requested " + dto.getRequestedPermission() + " access for document '" + doc.getTitle() + "'",
                clientIp,
                true
        );

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> getPendingRequestsForOwner(Authentication authentication) {
        User owner = getCurrentUser(authentication);
        List<AccessRequest> list = requestRepository.findByDocumentOwnerAndStatus(owner, AccessStatus.PENDING);
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> getMyRequests(Authentication authentication) {
        User requester = getCurrentUser(authentication);
        List<AccessRequest> list = requestRepository.findByRequester(requester);
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public AccessRequestResponse reviewRequest(Long requestId, boolean approve,
                                               Authentication authentication, HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "UNKNOWN";
        User reviewer = getCurrentUser(authentication);

        AccessRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found with ID: " + requestId));

        if (!req.getDocument().getOwner().getId().equals(reviewer.getId())) {
            auditService.logSecurityEvent(
                    reviewer.getUsername(),
                    AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
                    "ACCESS_REQUEST",
                    requestId.toString(),
                    "Unauthorized review attempt on access request",
                    clientIp,
                    false
            );
            throw new UnauthorizedAccessException("Only the document owner can approve or reject access requests.");
        }

        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());

        if (approve) {
            req.setStatus(AccessStatus.APPROVED);

            // Automatically grant the requested permission
            Optional<DocumentPermission> existingOpt = permissionRepository.findByDocumentAndUser(req.getDocument(), req.getRequester());
            DocumentPermission perm;
            if (existingOpt.isPresent()) {
                perm = existingOpt.get();
                perm.setActive(true);
                perm.setPermissionType(req.getRequestedPermission());
                perm.setGrantedBy(reviewer);
                perm.setGrantedAt(LocalDateTime.now());
                perm.setRevokedAt(null);
            } else {
                perm = new DocumentPermission(
                        req.getDocument(),
                        req.getRequester(),
                        req.getRequestedPermission(),
                        reviewer,
                        null
                );
            }
            permissionRepository.save(perm);

            auditService.logSecurityEvent(
                    reviewer.getUsername(),
                    AuditAction.ACCESS_REQUEST_APPROVED,
                    "ACCESS_REQUEST",
                    req.getId().toString(),
                    "Approved " + req.getRequestedPermission() + " access request from user '" + req.getRequester().getUsername() + "'",
                    clientIp,
                    true
            );
        } else {
            req.setStatus(AccessStatus.REJECTED);

            auditService.logSecurityEvent(
                    reviewer.getUsername(),
                    AuditAction.ACCESS_REQUEST_REJECTED,
                    "ACCESS_REQUEST",
                    req.getId().toString(),
                    "Rejected access request from user '" + req.getRequester().getUsername() + "'",
                    clientIp,
                    true
            );
        }

        AccessRequest updated = requestRepository.save(req);
        return mapToResponse(updated);
    }

    private User getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private AccessRequestResponse mapToResponse(AccessRequest r) {
        AccessRequestResponse res = new AccessRequestResponse();
        res.setId(r.getId());
        res.setDocumentId(r.getDocument().getId());
        res.setDocumentTitle(r.getDocument().getTitle());
        res.setRequesterId(r.getRequester().getId());
        res.setRequesterUsername(r.getRequester().getUsername());
        res.setRequesterEmail(r.getRequester().getEmail());
        res.setRequestedPermission(r.getRequestedPermission());
        res.setReason(r.getReason());
        res.setStatus(r.getStatus());
        res.setReviewedByUsername(r.getReviewedBy() != null ? r.getReviewedBy().getUsername() : null);
        res.setReviewedAt(r.getReviewedAt());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }
}
