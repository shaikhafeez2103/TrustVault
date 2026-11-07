# TrustVault – Project Implementation & Engineering Write-Up

**Company:** Snovasys  
**Event:** Snovasys Fresher Hiring Challenge 2026 – RISE (Round 2)  
**Project Name:** TrustVault – Secure Document Sharing with a Real Audit Trail  
**Candidate Submission Report**  

---

## 1. Project Overview & Architectural Vision

TrustVault is an enterprise document security platform designed around the principles of **Zero Trust Architecture (ZTA)** and **Tamper-Evident Auditing**. In traditional file-sharing solutions, security is often treated as an afterthought—implemented as shallow frontend UI toggles while the backend remains susceptible to Insecure Direct Object References (IDOR), SQL log tampering, and delayed token revocations.

TrustVault was engineered to demonstrate how a production-grade security product operates:
1. **True Defense-in-Depth:** All authorization policies are enforced by Spring Security and custom Resource Permission Evaluators at the service layer.
2. **Cryptographic Accountability:** Audit logs are linked using a sequential SHA-256 hash chain (Merkle-chain model), making historical record modification mathematically impossible without detection.
3. **Forensic Non-Repudiation:** Documents downloaded by viewers are dynamically stamped with forensic watermarks (user identity, timestamp, IP address) using Apache PDFBox.
4. **Resilient Portability:** Supports production MySQL 8.0 with automatic schema migration, alongside a zero-configuration in-memory H2 profile that enables evaluators to build and verify tests on any operating system without external database prerequisites.

---

## 2. Technical Stack & Engineering Choices

| Layer | Technologies Selected | Justification |
|---|---|---|
| **Backend Framework** | Java 22 + Spring Boot 3.3.4 | Provides modern virtual thread support, robust dependency injection, and production-tested enterprise stability. |
| **Security & Auth** | Spring Security 6 + JJWT 0.12.5 | Stateless identity authentication paired with real-time database-backed authorization evaluation. |
| **Data & Persistence** | Spring Data JPA + Hibernate 6.5 + MySQL 8.0 | Structured relational schema with foreign key constraints, indexing for sub-millisecond search, and connection pooling via HikariCP. |
| **Audit Chaining** | Java Security MessageDigest (SHA-256) | Standard cryptographic hashing without third-party proprietary dependencies. |
| **File Inspection** | Apache Tika 2.9.2 | Deep magic-byte binary header inspection to eliminate extension spoofing and web shells. |
| **Watermarking** | Apache PDFBox 3.0.2 | Programmatic, multi-layer PDF watermarking preserving document layout while rendering forensic ownership stamps. |
| **Frontend** | Responsive Vanilla HTML5, Modern CSS3, JavaScript ES6 | Fast, zero-build-step deployment. Runs directly out of Spring Boot’s embedded static resource pipeline with dark cybersecurity glassmorphism styling. |
| **Automated Testing** | JUnit 5 + Spring Boot Test + MockMvc | Automated suite proving IDOR protection, instant revocation, and tamper detection with 100% test pass rates. |

---

## 3. Core Implementation Details

### 3.1 Three-Tier Role-Based and Attribute-Based Access Control
TrustVault implements both RBAC (Role-Based Access Control) and ABAC (Attribute-Based Access Control):
* `ROLE_ADMIN`: System monitoring, audit ledger review, user management, and cryptographic tamper verification.
* `ROLE_OWNER`: Document creators who have full governance over their confidential files, including versioning, granting read/edit permissions, and instant revocation.
* `ROLE_VIEWER`: Users who can only browse permitted documents, submit access requests, and download watermarked copies.

### 3.2 Insecure Direct Object Reference (IDOR) Mitigation
Instead of exposing internal filesystem paths or relying on user ID checks in controllers, every access route (`GET /api/documents/{id}`, `/download`, `/versions`) routes through `DocumentSecurityService`:
```java
public boolean canRead(Authentication auth, Long docId) {
    // 1. Verify caller authenticity
    // 2. Document Owner has perpetual read rights
    // 3. Check individual active permissions:
    return permissionRepository.findByDocumentAndUserAndActiveTrue(doc, user)
        .filter(DocumentPermission::isCurrentlyValid)
        .isPresent();
}
```
If a viewer attempts to access a document ID they do not own and have not been granted, the system:
1. Immediately rejects the request with HTTP `403 Forbidden`.
2. Generates an `UNAUTHORIZED_ACCESS_ATTEMPT` security event in the audit trail recording caller identity, resource ID, and client IP.

### 3.3 Zero-Grace Period Instant Revocation
Unlike stateless JWT claims that remain valid until expiration, permission revocations take effect in real time:
- When an owner revokes a user's permission, `active` is set to `false` and `revoked_at` is timestamped.
- The next request made by the user—even 1 millisecond later—fails authorization at the database level.

### 3.4 Tamper-Evident Cryptographic Audit Ledger
To defeat internal threats (such as a compromised DBA attempting to erase footprints):
* Each record $i$ stores `previous_hash` equal to record $i-1$'s `current_hash`.
* `current_hash` is computed as:
  $$\text{current\_hash} = \text{SHA-256}(\text{id} \parallel \text{timestamp} \parallel \text{userId} \parallel \text{action} \parallel \text{details} \parallel \text{ipAddress} \parallel \text{previous\_hash})$$
* The verification engine iterates through all records:
  - If any row is modified, its recomputed hash does not match `current_hash`.
  - If any row is deleted, the hash pointer of the subsequent row breaks.
  - The verification report pinpoints the exact record ID where tampering occurred.

---

## 4. AI Usage & Collaborative Engineering

AI was used as an interactive architectural sounding board and code generation assistant across the 7-day development lifecycle. However, strict engineering critique was applied to every AI output:
1. **Timestamp Precision:** Corrected AI's unformatted `LocalDateTime.toString()` which caused hash mismatch bugs between JVM memory and database persistence.
2. **Authorization Scopes:** Rejected AI's simplistic static role checks in favor of dynamic resource-level evaluation with TTL expiration.
3. **File Inspection:** Upgraded AI's client-side `Content-Type` checks to deep binary magic-byte inspection via Apache Tika.
4. **Library Modernization:** Updated deprecated PDFBox 2.x APIs to PDFBox 3.x with dynamic page geometry and semi-transparent visual layering.
5. **Real-world Security Testing:** Refactored synthetic `@WithMockUser` tests into authentic JWT token-bearing requests executing across real filters.

Full prompts, evaluation critiques, and refinements are documented in [`PROMPTS.md`](PROMPTS.md).

---

## 5. Automated Security Test Results

The automated security test suite was executed via `mvn test` and passed with zero failures:

```text
[INFO] Running com.trustvault.TrustVaultSecurityTests
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 15.74 s
[INFO] 
[INFO] Results:
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Key verified test cases:
* `SECURITY-01`: Proves IDOR Defense (Viewer without permission receives 403 Forbidden).
* `SECURITY-02`: Every unauthorized access attempt is recorded in the audit trail.
* `SECURITY-03`: Granted Viewer with READ permission can access the document.
* `SECURITY-04`: Read-only Viewer cannot edit document metadata.
* `SECURITY-05`: Instant Revocation terminates access immediately.
* `SECURITY-06`: Non-Admin users are blocked from admin audit and stats routes.
* `SECURITY-07`: File upload sanitizes filenames and blocks path traversal attempts.
* `SECURITY-08`: Cryptographic hash chain detects database record tampering.

---

## 6. Unfinished Features & Future Enterprise Roadmap

While all mandatory and core security requirements are fully operational, the following enhancements represent the production roadmap:

1. **Hardware Security Module (HSM) Integration:**
   Anchor the Genesis block hash and periodic ledger checkpoints into an external Cloud HSM or public blockchain (e.g. Ethereum/Polygon) for third-party audit verification.
2. **Zero-Knowledge Document Encryption (Envelope Encryption):**
   Encrypt document contents at rest using client-side generated AES-256 data encryption keys (DEK) wrapped with an AWS KMS / HashiCorp Vault Key Encryption Key (KEK).
3. **Advanced Optical Character Recognition (OCR) Content Redaction:**
   Automatically detect and redact personally identifiable information (PII) such as Aadhaar, SSN, and credit card numbers during document ingest.
4. **Digital Cryptographic Signatures (PKCS#7 / PAdES):**
   Allow document owners and approved approvers to cryptographically sign documents with digital certificates during the approval workflow.
