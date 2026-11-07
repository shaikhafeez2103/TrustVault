package com.trustvault.repository;

import com.trustvault.entity.AccessRequest;
import com.trustvault.entity.Document;
import com.trustvault.entity.User;
import com.trustvault.enums.AccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    List<AccessRequest> findByDocumentOwnerAndStatus(User owner, AccessStatus status);

    List<AccessRequest> findByStatus(AccessStatus status);

    List<AccessRequest> findByDocumentOwner(User owner);

    List<AccessRequest> findByRequester(User requester);

    List<AccessRequest> findByDocumentAndRequesterAndStatus(Document document, User requester, AccessStatus status);
}
