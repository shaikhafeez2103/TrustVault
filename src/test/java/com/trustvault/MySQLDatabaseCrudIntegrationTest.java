package com.trustvault;

import com.trustvault.entity.AuditLog;
import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.PermissionType;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.AccessRequestRepository;
import com.trustvault.repository.AuditLogRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.DocumentVersionRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
public class MySQLDatabaseCrudIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Verify real MySQL connection, user registration, update, retrieve, and delete")
    public void testMySQLUserCrudOperations() {
        String testUsername = "mysql_test_user_" + System.currentTimeMillis();
        String testEmail = testUsername + "@trustvault.io";

        // 1. CREATE / INSERT
        User user = new User(
                testUsername,
                testEmail,
                passwordEncoder.encode("Test@MySQL2026!"),
                "MySQL Verification User",
                RoleName.ROLE_VIEWER
        );
        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();

        // 2. RETRIEVE
        Optional<User> retrieved = userRepository.findByUsername(testUsername);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getEmail()).isEqualTo(testEmail);

        // 3. UPDATE
        retrieved.get().setFullName("MySQL Verification User Updated");
        userRepository.save(retrieved.get());

        Optional<User> updated = userRepository.findByUsername(testUsername);
        assertThat(updated).isPresent();
        assertThat(updated.get().getFullName()).isEqualTo("MySQL Verification User Updated");

        // 4. CLEANUP / DELETE
        userRepository.delete(updated.get());
        Optional<User> deleted = userRepository.findByUsername(testUsername);
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("Verify real MySQL document, permission, and audit log persistence")
    public void testMySQLDocumentPermissionAndAuditPersistence() {
        String testDocOwnerName = "doc_owner_" + System.currentTimeMillis();
        User owner = userRepository.save(new User(
                testDocOwnerName,
                testDocOwnerName + "@trustvault.io",
                passwordEncoder.encode("Owner@2026!"),
                "Document Owner Test",
                RoleName.ROLE_OWNER
        ));

        // 1. Document persistence
        Document doc = new Document(
                "MySQL Verification Confidential Policy",
                "Testing real MySQL document persistence",
                "Policy",
                "policy_v1.pdf",
                "uploads/test_policy.pdf",
                "application/pdf",
                1024L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                owner
        );
        Document savedDoc = documentRepository.save(doc);
        assertThat(savedDoc.getId()).isNotNull();

        // 2. Permission persistence
        DocumentPermission permission = new DocumentPermission(
                savedDoc,
                owner,
                PermissionType.READ,
                owner,
                LocalDateTime.now().plusDays(7)
        );
        DocumentPermission savedPerm = permissionRepository.save(permission);
        assertThat(savedPerm.getId()).isNotNull();

        // 3. Audit log persistence with cryptographic hash chain
        AuditLog log = auditService.logSecurityEvent(
                owner.getUsername(),
                AuditAction.DOCUMENT_UPLOAD,
                "DOCUMENT",
                savedDoc.getId().toString(),
                "Document uploaded to real MySQL storage",
                "127.0.0.1",
                true
        );
        assertThat(log.getId()).isNotNull();
        assertThat(log.getCurrentHash()).isNotBlank();

        // Verify retrieval directly from repository
        Optional<AuditLog> foundLog = auditLogRepository.findById(log.getId());
        assertThat(foundLog).isPresent();
        assertThat(foundLog.get().getAction()).isEqualTo(AuditAction.DOCUMENT_UPLOAD);

        // Cleanup test document and owner
        permissionRepository.delete(savedPerm);
        documentRepository.delete(savedDoc);
        userRepository.delete(owner);
    }
}
