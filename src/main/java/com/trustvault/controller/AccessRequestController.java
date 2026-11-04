package com.trustvault.controller;

import com.trustvault.dto.AccessRequestDto;
import com.trustvault.dto.AccessRequestResponse;
import com.trustvault.service.AccessRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class AccessRequestController {

    @Autowired
    private AccessRequestService requestService;

    @PostMapping
    public ResponseEntity<AccessRequestResponse> createRequest(
            @Valid @RequestBody AccessRequestDto dto,
            Authentication authentication,
            HttpServletRequest request) {

        AccessRequestResponse response = requestService.createRequest(dto, authentication, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<AccessRequestResponse>> getPendingRequests(Authentication authentication) {
        List<AccessRequestResponse> list = requestService.getPendingRequestsForOwner(authentication);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/my")
    public ResponseEntity<List<AccessRequestResponse>> getMyRequests(Authentication authentication) {
        List<AccessRequestResponse> list = requestService.getMyRequests(authentication);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<AccessRequestResponse> reviewRequest(
            @PathVariable("id") Long id,
            @RequestParam("approve") boolean approve,
            Authentication authentication,
            HttpServletRequest request) {

        AccessRequestResponse response = requestService.reviewRequest(id, approve, authentication, request);
        return ResponseEntity.ok(response);
    }
}
