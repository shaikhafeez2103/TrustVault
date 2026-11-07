# TrustVault – Secure Document Sharing with a Real Audit Trail

[![Java](https://img.shields.io/badge/Java-22-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white)](https://spring.io/projects/spring-security)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Tests](https://img.shields.io/badge/Security_Tests-15%2F15_Passed-10B981?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/)

> **Snovasys Fresher Hiring Challenge 2026 – RISE (Round 2 Project Challenge)**  
> **Company:** Snovasys  
> **Challenge Window:** November 1–7, 2026 (7 Days)  
> **Project Name:** TrustVault  

---

## 1. Quick Start & Role Overview

The application seeds three role-based accounts on startup for evaluation:

| Role | Username | Full Name | Primary Capabilities |
|---|---|---|---|
| **Admin** | `admin` | Security Administrator | Review immutable audit ledger, monitor 24h threat metrics, manage user roles, run cryptographic tamper verification. |
| **Document Owner** | `owner` | Alice Jenkins (Chief Legal Officer) | Upload confidential files, manage version history, grant/revoke permissions, review access requests. |
| **Viewer** | `viewer` | Bob Smith (Compliance Auditor) | Browse permitted files, download with forensic watermarking, submit access requests for locked files. |

> **Security Policy:** New public account registrations and new Google OAuth 2.0 sign-ins are provisioned with the least-privileged `ROLE_VIEWER` role by default. Only an authenticated Administrator (`ROLE_ADMIN`) can promote accounts via `PUT /api/admin/users/{id}/role`.

---

## 2. Key Enterprise Features

* **Strict Backend Authorization & IDOR Defense:** All access decisions are verified at the Spring Security service layer. Unauthorized direct object lookups immediately return `403 Forbidden` and log a security alert.
* **Instant Permission Revocation:** Zero grace period. Revoking access takes effect immediately at the database level.
* **Tamper-Evident SHA-256 Audit Ledger:** Each audit record is cryptographically linked to the preceding record using sequential SHA-256 hash pointers. Any manual database alteration or row deletion is detected by the built-in Tamper Verification tool.
* **Dynamic Forensic Watermarking:** Confidential PDF downloads are dynamically stamped with the viewer's username, UTC timestamp, and client IP address via Apache PDFBox to prevent unauthorized distribution.
* **File Upload Sanitization & Magic-Byte Inspection:** Deep binary inspection using Apache Tika prevents disguised web shells and double-extension exploits, while UUID storage prevents directory traversal attacks.
* **Access Request & Approval Workflow:** Viewers can request access with a business justification; Document Owners receive real-time notifications to approve or reject with one click.
* **Document Version History:** Manage document revisions (`v1`, `v2`, `v3`) with SHA-256 integrity checksums and change summaries.

---

## 3. Project Structure

```text
trustvault/
├── pom.xml                                   # Maven dependencies & build configuration
├── run_maven.bat                             # Windows build launcher with SSL truststore flags
├── schema.sql                                # MySQL 8.0 DDL database schema
├── data.sql                                  # Seed data for roles, users, and genesis block
├── README.md                                 # Main project documentation & setup
├── THREATS.md                                # Comprehensive STRIDE threat model & analysis
├── PROMPTS.md                                # Five dated AI interaction & engineering refinement logs
├── PROJECT_WRITEUP.md                        # Implementation details & future roadmap
├── DEMO_GUIDE.md                             # 5-minute video presentation script
├── API_DOCUMENTATION.md                      # Complete REST API endpoint reference
└── src/
    ├── main/
    │   ├── java/com/trustvault/
    │   │   ├── TrustVaultApplication.java    # Spring Boot entry point & data seeder
    │   │   ├── config/                       # SecurityConfig, CORS, and Filter chains
    │   │   ├── controller/                   # REST API Controllers (Auth, Doc, Perm, Admin, Tamper)
    │   │   ├── dto/                          # Strongly typed request/response transfer objects
    │   │   ├── entity/                       # JPA Relational Entities (User, Doc, Perm, AuditLog)
    │   │   ├── enums/                        # RoleName, PermissionType, AuditAction, AccessStatus
    │   │   ├── exception/                    # GlobalExceptionHandler with sanitized error bodies
    │   │   ├── repository/                   # Spring Data JPA repositories with custom queries
    │   │   ├── security/                     # JWT provider, filter, and DocumentSecurityService
    │   │   └── service/                      # Core business logic (Audit, Watermark, Storage, Doc)
    │   └── resources/
    │       ├── application.properties        # Production MySQL configuration
    │       ├── application-h2.properties     # Standalone in-memory profile
    │       └── static/                       # Dark-mode Cybersecurity Web UI (HTML, CSS, JS)
    │           ├── index.html
    │           ├── css/styles.css
    │           └── js/app.js
    └── test/
        ├── java/com/trustvault/
        │   └── TrustVaultSecurityTests.java  # 8 Automated security verification tests
        └── resources/
            └── application.properties        # Test profile with in-memory H2 configuration
```

---

## 4. Setup & Running Instructions

### Prerequisites
* Java JDK 22 (or JDK 17+)
* Apache Maven 3.9+
* MySQL 8.0 (Optional; an in-memory database profile is included for zero-setup execution)

### Option A: Quick Standalone Run (Zero Configuration)
TrustVault includes an in-memory profile allowing immediate execution without setting up MySQL:
```bash
# 1. Clone repository
git clone https://github.com/shaikhafeez2103/TrustVault.git
cd TrustVault

# 2. Run with H2 in-memory profile
run_maven.bat spring-boot:run -Dspring-boot.run.profiles=h2
```
Open your browser at `http://localhost:8082/login.html` (or `http://localhost:8082/index.html`).

### Option B: Production MySQL Setup
1. Open MySQL CLI and run the schema setup:
   ```bash
   mysql -u root -p < schema.sql
   ```
2. Configure your MySQL credentials via environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_JWT_SECRET`):
   ```properties
   spring.datasource.url=${SPRING_DATASOURCE_URL}
   spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
   spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
   ```
3. Launch the application:
   ```bash
   run_maven.bat spring-boot:run
   ```
4. Access the web dashboard at `http://localhost:8082`.

### Option C: Google OAuth 2.0 / OpenID Connect Configuration
TrustVault implements backend-verified Google OAuth 2.0 Authorization Code Flow with CSRF state/nonce cookie validation, OIDC UserInfo verification (`email_verified == true`), account-takeover protection (blocking automatic email linking to privileged `ROLE_ADMIN`/`ROLE_OWNER` accounts), and automatic `ROLE_VIEWER` provisioning.

Configure the following environment variables in Render (or locally):
```env
GOOGLE_CLIENT_ID=GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET=GOOGLE_CLIENT_SECRET
GOOGLE_REDIRECT_URI=https://trustvault-pxqr.onrender.com/login/oauth2/code/google
```

**Google Cloud Console Setup:**
1. Open **Google Cloud Console → APIs & Services → Credentials**.
2. Create an **OAuth 2.0 Client ID** (Application type: **Web application**).
3. Add **Authorized JavaScript origins**:
   - `https://trustvault-pxqr.onrender.com`
   - `http://localhost:8082` (optional for local development)
4. Add **Authorized redirect URIs**:
   - `https://trustvault-pxqr.onrender.com/login/oauth2/code/google`
   - `http://localhost:8082/login/oauth2/code/google` (optional for local development)
5. Copy the generated Client ID and Client Secret into your Render Web Service **Environment** tab as `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI`.

---

## 5. Running Automated Security Tests

TrustVault includes a comprehensive security test suite verifying authorization, IDOR protection, instant revocation, and hash chain tamper detection:

```bash
run_maven.bat test
```

### Test Suite Summary:
* `SECURITY-01`: Proves IDOR Defense (Viewer without permission receives HTTP `403 Forbidden`).
* `SECURITY-02`: Every unauthorized access attempt is recorded in the audit trail.
* `SECURITY-03`: Granted Viewer with READ permission can view the document.
* `SECURITY-04`: Read-only Viewer cannot edit document metadata (HTTP `403 Forbidden`).
* `SECURITY-05`: Instant Revocation terminates access immediately with zero latency.
* `SECURITY-06`: Non-Admin users are blocked from admin audit and stats routes.
* `SECURITY-07`: File upload sanitizes filenames and blocks path traversal attempts.
* `SECURITY-08`: Cryptographic hash chain detects database record tampering and pinpoints the altered record.

---

## 6. Docker Container Deployment

TrustVault can be containerized using the following `Dockerfile`:

```dockerfile
FROM eclipse-temurin:22-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

FROM eclipse-temurin:22-jre-jammy
WORKDIR /app
COPY --from=build /app/target/trustvault-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run with Docker:
```bash
docker build -t trustvault:latest .
docker run -p 8080:8080 trustvault:latest
```

---

## 7. Submission Documentation Index

* [`THREATS.md`](THREATS.md): Comprehensive STRIDE threat modeling & technical mitigations.
* [`PROMPTS.md`](PROMPTS.md): Five dated AI usage logs with prompt engineering, critique, and refinements.
* [`PROJECT_WRITEUP.md`](PROJECT_WRITEUP.md): Architectural report, security implementation, and unfinished features roadmap.
* [`DEMO_GUIDE.md`](DEMO_GUIDE.md): 5-minute video demonstration script and presentation plan.
* [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md): Complete REST endpoint reference with request/response schemas.
* [`schema.sql`](schema.sql) and [`data.sql`](data.sql): Database DDL schema and seed records.
