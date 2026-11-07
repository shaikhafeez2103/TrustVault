package com.trustvault;

import com.trustvault.entity.Document;
import com.trustvault.entity.DocumentPermission;
import com.trustvault.entity.DocumentVersion;
import com.trustvault.entity.User;
import com.trustvault.enums.AuditAction;
import com.trustvault.enums.PermissionType;
import com.trustvault.enums.RoleName;
import com.trustvault.repository.AuditLogRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.DocumentVersionRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@SpringBootApplication
public class TrustVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustVaultApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDatabase(UserRepository userRepository,
                                         DocumentRepository documentRepository,
                                         DocumentVersionRepository versionRepository,
                                         DocumentPermissionRepository permissionRepository,
                                         AuditLogRepository auditLogRepository,
                                         AuditService auditService,
                                         PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Seed Roles & Users
            if (userRepository.count() == 0) {
                User admin = new User(
                        "admin",
                        "admin@trustvault.io",
                        passwordEncoder.encode("Admin@2026Test"),
                        "Demo Admin",
                        RoleName.ROLE_ADMIN
                );
                userRepository.save(admin);

                User owner = new User(
                        "owner",
                        "owner@trustvault.io",
                        passwordEncoder.encode("Owner@123"),
                        "Alice Jenkins (Chief Legal Officer)",
                        RoleName.ROLE_OWNER
                );
                userRepository.save(owner);

                User viewer = new User(
                        "viewer",
                        "viewer@trustvault.io",
                        passwordEncoder.encode("Viewer@123"),
                        "Bob Smith (Compliance Analyst)",
                        RoleName.ROLE_VIEWER
                );
                userRepository.save(viewer);

                // 2. Initialize Genesis Block in Audit Log
                auditService.logSecurityEvent(
                        "SYSTEM",
                        AuditAction.TAMPER_VERIFICATION_CHECK,
                        "GENESIS_BLOCK",
                        "0",
                        "TrustVault Cryptographic Audit Ledger Initialized. Root Hash Chained.",
                        "127.0.0.1",
                        true
                );

                // 3. Seed Sample Confidential Document
                Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
                Files.createDirectories(uploadDir);

                String sampleFileName = "seed_board_resolution_2026.pdf";
                Path sampleFilePath = uploadDir.resolve(sampleFileName);
                if (!Files.exists(sampleFilePath) || Files.size(sampleFilePath) < 200) {
                    writeValidSeedPdf(sampleFilePath,
                            "Q4 Strategic M&A Resolution",
                            "Confidential board-approved acquisition and IP transfer terms for Project Phoenix.");
                }

                Document sampleDoc = new Document(
                        "Q4 Strategic M&A Resolution",
                        "Confidential board-approved acquisition and intellectual property transfer terms for Project Phoenix.",
                        "Legal & Board",
                        "Q4_Board_Resolution_Confidential.pdf",
                        sampleFileName,
                        "application/pdf",
                        Files.size(sampleFilePath),
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        owner
                );
                Document savedDoc = documentRepository.save(sampleDoc);

                DocumentVersion version = new DocumentVersion(
                        savedDoc,
                        1,
                        sampleFileName,
                        "Q4_Board_Resolution_Confidential.pdf",
                        Files.size(sampleFilePath),
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        "Initial board ratified draft",
                        owner
                );
                versionRepository.save(version);

                // Seed permission: Bob (Viewer) has READ permission on this document
                DocumentPermission initialPerm = new DocumentPermission(
                        savedDoc,
                        viewer,
                        PermissionType.READ,
                        owner,
                        LocalDateTime.now().plusDays(30)
                );
                permissionRepository.save(initialPerm);

                // Seed a strictly private document owned by Alice where Bob has NO permission (for testing IDOR)
                String privateFileName = "seed_executive_salaries_2026.pdf";
                Path privateFilePath = uploadDir.resolve(privateFileName);
                if (!Files.exists(privateFilePath) || Files.size(privateFilePath) < 200) {
                    writeValidSeedPdf(privateFilePath,
                            "FY2026 Executive Compensation Ledger",
                            "Highly restricted executive payroll, equity options, and retention bonuses.");
                }

                Document privateDoc = new Document(
                        "FY2026 Executive Compensation Ledger",
                        "Highly restricted executive payroll, equity options, and retention bonuses.",
                        "Finance",
                        "FY2026_Executive_Payroll.pdf",
                        privateFileName,
                        "application/pdf",
                        Files.size(privateFilePath),
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b856",
                        owner
                );
                documentRepository.save(privateDoc);

                // Log audit events for seeding
                auditService.logSecurityEvent(
                        owner.getUsername(),
                        AuditAction.DOCUMENT_UPLOAD,
                        "DOCUMENT",
                        savedDoc.getId().toString(),
                        "Document 'Q4 Strategic M&A Resolution' uploaded",
                        "127.0.0.1",
                        true
                );

                auditService.logSecurityEvent(
                        owner.getUsername(),
                        AuditAction.PERMISSION_GRANTED,
                        "PERMISSION",
                        initialPerm.getId().toString(),
                        "Granted READ permission to user 'viewer' on document 'Q4 Strategic M&A Resolution'",
                        "127.0.0.1",
                        true
                );
            } else {
                userRepository.findByUsername("admin").ifPresent(existingAdmin -> {
                    boolean updated = false;
                    if (!"Demo Admin".equals(existingAdmin.getFullName())) {
                        existingAdmin.setFullName("Demo Admin");
                        updated = true;
                    }
                    if (existingAdmin.getRole() != RoleName.ROLE_ADMIN) {
                        existingAdmin.setRole(RoleName.ROLE_ADMIN);
                        updated = true;
                    }
                    if (!passwordEncoder.matches("Admin@2026Test", existingAdmin.getPassword())) {
                        existingAdmin.setPassword(passwordEncoder.encode("Admin@2026Test"));
                        updated = true;
                    }
                    if (updated) {
                        userRepository.save(existingAdmin);
                    }
                });
            }
        };
    }

    private static void writeValidSeedPdf(Path targetPath, String title, String subtitle) {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("TRUSTVAULT ENTERPRISE DOCUMENT");
                cs.endText();

                cs.beginText();
                cs.setFont(boldFont, 13);
                cs.newLineAtOffset(50, 690);
                cs.showText(title);
                cs.endText();

                cs.beginText();
                cs.setFont(bodyFont, 11);
                cs.newLineAtOffset(50, 665);
                cs.showText(subtitle);
                cs.endText();
            }
            pdf.save(targetPath.toFile());
        } catch (Exception e) {
            try {
                Files.writeString(targetPath, "%PDF-1.4\n%TrustVault Seed Document\n%%EOF");
            } catch (Exception ignored) {
            }
        }
    }
}
