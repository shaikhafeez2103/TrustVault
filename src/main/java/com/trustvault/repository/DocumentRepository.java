package com.trustvault.repository;

import com.trustvault.entity.Document;
import com.trustvault.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByDeletedFalse();

    List<Document> findByDeletedTrue();

    List<Document> findByOwnerAndDeletedFalse(User owner);

    List<Document> findByOwnerAndDeletedTrue(User owner);

    @Query("SELECT d FROM Document d WHERE d.deleted = false AND " +
           "(:query IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:category IS NULL OR d.category = :category) AND " +
           "(:ownerUsername IS NULL OR d.owner.username = :ownerUsername) AND " +
           "(:startDate IS NULL OR d.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR d.createdAt <= :endDate)")
    List<Document> searchDocuments(
            @Param("query") String query,
            @Param("category") String category,
            @Param("ownerUsername") String ownerUsername,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT DISTINCT d FROM Document d JOIN DocumentPermission p ON p.document = d " +
           "WHERE d.deleted = false AND p.user = :user AND p.active = true AND " +
           "(p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    List<Document> findSharedWithUser(@Param("user") User user);
}
