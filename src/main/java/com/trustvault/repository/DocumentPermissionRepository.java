package com.trustvault.repository;

import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findByDocumentAndActiveTrue(Document document);

    List<DocumentPermission> findByDocument(Document document);

    Optional<DocumentPermission> findByDocumentAndUserAndActiveTrue(Document document, User user);

    Optional<DocumentPermission> findByDocumentAndUser(Document document, User user);

    List<DocumentPermission> findByUserAndActiveTrue(User user);

    @Query("SELECT COUNT(p) FROM DocumentPermission p WHERE p.active = true AND p.document.deleted = false AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    long countActiveValidPermissions();
}
