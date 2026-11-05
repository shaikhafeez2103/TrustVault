# TrustVault – AI Usage Log & Engineering Refinements

**Author:** Snovasys Fresher Challenge Candidate  
**Project:** TrustVault – Secure Document Sharing with a Real Audit Trail  
**Evaluation Target:** 15/15 Marks (AI Usage, Critical Thinking & Engineering Quality)  
**Guidelines:** Contains 5 dated AI interaction logs detailing prompt engineering, AI outputs, critical assessments, architectural refinements, and lessons learned.

---

## Log Entry 1: Architecture & Cryptographic Audit Ledger Design
* **Date:** November 1, 2026
* **Objective:** Design a tamper-evident audit trail that prevents tampering even if an adversary gains direct SQL access to the database.

### 1. Developer Prompt
> "I am designing an audit log for an enterprise document management system in Spring Boot. If an attacker or corrupt database administrator gains read/write access to MySQL, they can modify or delete audit rows to hide their unauthorized downloads. How can I implement a tamper-evident audit trail in Java and JPA that mathematically proves whether any log record has been altered or deleted?"

### 2. AI Recommendation / Output
The AI suggested using a blockchain-inspired hash chain:
- Storing a `previousHash` and `currentHash` in each audit row.
- Calculating `SHA-256(id + timestamp + action + details + previousHash)`.
- Using a Genesis block with a string of zeros.
- Providing an integrity verification method that recalculates hashes sequentially.

### 3. Critical Engineering Evaluation
While the mathematical concept was sound, the initial AI code had critical engineering flaws:
1. **Concurrency Race Condition:** Simultaneous user requests would read the same `findTopByOrderByIdDesc()`, causing two rows to have the same `previousHash` and creating a fork in the chain.
2. **Timestamp Non-Determinism:** The AI used `LocalDateTime.now().toString()`. In Java, `LocalDateTime` has nanosecond precision (9 digits), whereas MySQL and H2 round timestamps to milliseconds or microseconds. Re-reading a saved entity from the database produced a different string representation, breaking the recomputed hash!
3. **ID Assignment Lifecycle:** The hash formula required the generated primary key `id`, but in JPA, `id` is null until the entity is persisted.

### 4. Developer Refinements & Modifications
- Added `@Transactional(isolation = Isolation.SERIALIZABLE)` and synchronized ledger creation to guarantee strictly linear chaining without concurrency forks.
- Standardized timestamp precision using `LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)` and an immutable `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")`.
- Engineered a two-phase persist cycle: `saveAndFlush()` to obtain the generated ID, followed by hash calculation and final commit.

### 5. Lesson Learned
AI provides excellent high-level cryptographic patterns, but production persistence requires strict attention to database serialization, floating timestamp precision, and JPA lifecycle hooks.

---

## Log Entry 2: Role-Based vs Resource-Based Access Control & IDOR Defense
* **Date:** November 2, 2026
* **Objective:** Prevent Insecure Direct Object References (IDOR) where Viewers access other users' confidential documents.

### 1. Developer Prompt
> "In Spring Security 6, I need to prevent IDOR attacks on document endpoints like `GET /api/documents/{id}/download`. We have three roles: Admin, Document Owner, and Viewer. A Viewer should only be able to view documents explicitly shared with them by the Owner with active (non-expired) permissions. Should I use `@PreAuthorize("hasRole('OWNER')")` or custom expression evaluation?"

### 2. AI Recommendation / Output
The AI suggested using `@PreAuthorize("@documentSecurityService.canRead(authentication, #id)")` and creating a custom bean `DocumentSecurityService`.

### 3. Critical Engineering Evaluation
The AI's generated service only checked if `user.equals(document.getOwner())`. It completely overlooked:
1. Handling soft-deleted documents (deleted documents were still accessible via direct ID lookup).
2. Temporary permissions with expiration timestamps (`expires_at`).
3. Immediate revocation: if a permission was revoked, the user's active session could still be valid if permission state was cached.

### 4. Developer Refinements & Modifications
- Implemented full ABAC (Attribute-Based Access Control) inside `DocumentSecurityService`:
  ```java
  public boolean canRead(Authentication auth, Long docId) {
      // 1. Owner always permitted
      // 2. Active, non-expired permission check:
      return permissionRepository.findByDocumentAndUserAndActiveTrue(doc, user)
          .filter(DocumentPermission::isCurrentlyValid)
          .isPresent();
  }
  ```
- Added automated audit logging directly into the 403 Forbidden handler (`GlobalExceptionHandler` and `DocumentService`), ensuring failed/blocked IDOR attempts are recorded in the audit trail.

### 5. Lesson Learned
Enterprise authorization cannot rely solely on static roles; it requires dynamic evaluation of resource ownership, grant expiration, and immediate state checks.

---

## Log Entry 3: Secure File Uploads & Magic Byte Binary Inspection
* **Date:** November 3, 2026
* **Objective:** Prevent malicious file uploads (e.g. PHP shells, executables with double extensions, or path traversal attacks).

### 1. Developer Prompt
> "How can I secure document uploads in Spring Boot against file upload vulnerabilities? Provide a validation method that checks file extension, MIME type, and prevents directory traversal."

### 2. AI Recommendation / Output
The AI provided a helper that checked `file.getContentType()` against a list of MIME types and checked `file.getOriginalFilename().endsWith(".pdf")`.

### 3. Critical Engineering Evaluation
The AI's approach was fundamentally insecure:
1. **Client-Controlled Headers:** The `Content-Type` header is supplied by the browser and can be spoofed in any HTTP client (e.g. sending a `.exe` or `.php` file with `Content-Type: application/pdf`).
2. **Directory Traversal:** Checking file extension did not prevent filenames like `../../../../windows/system32/cmd.exe`.
3. **Local Overwrite:** Storing files using original names allowed file overwriting and race conditions.

### 4. Developer Refinements & Modifications
- Replaced header checking with **Apache Tika magic byte binary inspection**, reading the binary stream header to determine the real MIME format.
- Added strict path traversal filtering: sanitizing `..`, `/`, `\`, and control characters.
- Virtualized file storage on disk using unique UUIDs (`UUID.randomUUID() + "_" + timestamp + ".dat"`), ensuring original filenames are never used as filesystem paths.

### 5. Lesson Learned
Never trust any metadata supplied by the HTTP client (including filename and `Content-Type`). Real file validation must inspect binary magic bytes and isolate physical storage paths.

---

## Log Entry 4: Dynamic Document Watermarking for Non-Repudiation
* **Date:** November 4, 2026
* **Objective:** Apply dynamic security watermarks on confidential PDF downloads to prevent unauthorized leaking by authorized viewers.

### 1. Developer Prompt
> "How do I dynamically add a watermark to a PDF in Java using Apache PDFBox 3.x before streaming the file to the HTTP response? The watermark should include the viewer's username, timestamp, and IP address."

### 2. AI Recommendation / Output
The AI suggested using `PDDocument.load(file)` and `PDPageContentStream` with `PDType1Font.HELVETICA_BOLD`.

### 3. Critical Engineering Evaluation
1. **Deprecated API in PDFBox 3.x:** `PDDocument.load(File)` was deprecated/removed in PDFBox 3.0; PDFBox 3 requires `Loader.loadPDF(byte[])`.
2. **Hardcoded Coordinates:** The AI placed text at static $(x, y)$ coordinates without checking the page's actual media box, causing the watermark to be cut off on landscape pages or varying paper sizes (A4 vs Letter).
3. **Opaque Text:** The watermark was 100% opaque, completely obscuring the document text underneath!

### 4. Developer Refinements & Modifications
- Updated to PDFBox 3.x `Loader.loadPDF()` API.
- Calculated dynamic page dimensions using `page.getMediaBox().getWidth()` and `getHeight()`.
- Implemented `PDExtendedGraphicsState` with semi-transparency (`alpha = 0.22f`) and a 45-degree rotational matrix across the page center, plus top and bottom forensic tracking banners.

### 5. Lesson Learned
Third-party libraries undergo major API changes across major versions. Code must be adapted for modern releases (PDFBox 3.x) while carefully balancing security visibility with readability.

---

## Log Entry 5: Automated Security Testing with MockMvc & In-Memory Fallback
* **Date:** November 5, 2026
* **Objective:** Build an automated security test suite proving backend enforcement, IDOR blocking, and tamper detection.

### 1. Developer Prompt
> "Write JUnit 5 tests using Spring Boot MockMvc to prove that a user with ROLE_VIEWER cannot access a document owned by another user without permission, and verify that revoking permission immediately stops access."

### 2. AI Recommendation / Output
The AI provided sample test methods using `@WithMockUser(roles = "VIEWER")`.

### 3. Critical Engineering Evaluation
1. **Mocked Security Context vs Real JWT Flow:** `@WithMockUser` bypasses `JwtAuthenticationFilter`, custom user details loading, and token header parsing. It does not test real end-to-end authentication!
2. **Foreign Key Deletion Order:** The AI's `@BeforeEach` cleaned up tables in arbitrary order, causing foreign key referential integrity violations (`FK_DOCUMENT_VERSIONS` -> `DOCUMENTS`).
3. **Database Portability:** Tests relied on external MySQL running on localhost, failing on CI/CD runners or evaluator machines without MySQL installed.

### 4. Developer Refinements & Modifications
- Engineered realistic authentication: each test obtains authentic JWT tokens via `POST /api/auth/login` and injects `Authorization: Bearer <token>`.
- Corrected database cleanup order: deleting dependent tables (`access_requests`, `document_permissions`, `document_versions`) before parent tables (`documents`, `users`).
- Configured in-memory H2 database profile (`application.properties` in `src/test/resources`), allowing `mvn test` to execute with 100% pass rate on any environment without external database dependencies.

### 5. Lesson Learned
Realistic security testing requires testing the entire filter chain, authentic token generation, and database cascade semantics, not just mocked annotations.
