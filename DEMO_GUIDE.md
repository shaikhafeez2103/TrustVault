# TrustVault – 5-Minute Video Demonstration Script

**Event:** Snovasys Fresher Hiring Challenge 2026 – RISE  
**Project:** TrustVault – Secure Document Sharing with a Real Audit Trail  
**Target Duration:** Exactly 5 Minutes (0:00 - 5:00)  

---

## Video Breakdown & Timed Cue Sheet

### [0:00 - 0:45] Introduction & The Problem Statement
* **Visual:** Browser showing `http://localhost:8080` with the TrustVault Dark Security Dashboard.
* **Speaker Script:**
  > "Hello everyone! Welcome to TrustVault. In modern enterprise environments, sensitive documents—such as board resolutions, M&A agreements, and financial reports—are frequently exposed to unauthorized access, Insecure Direct Object References (IDOR), and un-audited insider leaks.
  > TrustVault solves this by providing a Zero-Trust Document Management Platform with three core pillars:
  > 1. Strict backend-enforced authorization with instant permission revocation.
  > 2. Dynamic visual document watermarking for non-repudiation.
  > 3. A blockchain-inspired, cryptographically tamper-evident audit ledger."

---

### [0:45 - 1:45] Three-Role Architecture & Document Management
* **Visual:**
  - Click the **"Owner (Alice)"** demo button. Shows Alice's Vault.
  - Show the confidential document: *"Q4 Strategic M&A Resolution"*.
  - Click **"Upload Document"**, upload a PDF, show category selection and Apache Tika magic-byte validation.
  - Show Version History modal: upload a new version (`v2`) with a change summary.
* **Speaker Script:**
  > "We have three pre-seeded enterprise roles: Admin, Document Owner, and Viewer. 
  > Right now, I am logged in as Alice, our Chief Legal Officer and Document Owner.
  > Alice can upload confidential documents, classify them by category, and maintain version history. Each version computes an SHA-256 checksum and isolates physical storage on disk using sanitized UUIDs to completely eliminate path traversal risks."

---

### [1:45 - 2:45] Access Control, IDOR Prevention & Instant Revocation
* **Visual:**
  - Open Alice's Document Permissions modal for *"Q4 Strategic M&A Resolution"*.
  - Show that Bob (`viewer`) has an active `READ` permission.
  - Click **"Viewer (Bob)"** demo button.
  - In Bob's view, show that he can see and download the shared document.
  - Download the PDF: open it to show the **forensic dynamic watermark** stamped across every page with Bob's username, UTC timestamp, and IP address.
  - Now, attempt to access Alice's private *"FY2026 Executive Compensation Ledger"* (which is NOT shared with Bob).
  - Show that the backend blocks the request with **403 Forbidden** and logs an `UNAUTHORIZED_ACCESS_ATTEMPT`.
  - Switch back to Alice: click **"Revoke Immediately"** on Bob's permission.
  - Switch back to Bob: refresh / attempt to access the document. It immediately returns **403 Forbidden**—zero grace period!
* **Speaker Script:**
  > "Now let's observe our access control and IDOR defense in action.
  > Alice shares the M&A document with Bob as a Viewer. When Bob downloads it, TrustVault's WatermarkService dynamically stamps Bob's username, timestamp, and IP address across the PDF to prevent unauthorized redistribution.
  > Next, if Bob attempts to directly access Alice's unshared executive payroll document via direct ID injection, our backend intercepts it, denies access with a 403 Forbidden, and flags an IDOR probe in the audit trail.
  > Furthermore, when Alice clicks 'Revoke', Bob's access is terminated immediately with zero latency."

---

### [2:45 - 3:45] Access Request & Approval Workflow
* **Visual:**
  - In Bob's view: Bob browses the Vault, finds a restricted document, and clicks **"Request Access"**.
  - Bob enters justification: *"Need to review Section 4 for compliance audit"*.
  - Switch back to Alice: open the **"Access Requests"** tab. The pending request appears in real time.
  - Alice clicks **"Approve"**.
  - Switch back to Bob: the document is now unlocked in his *"Shared with Me"* tab!
* **Speaker Script:**
  > "To streamline collaboration without compromising security, TrustVault features an Access Request & Approval workflow.
  > Viewers can request temporary or permanent access with a business justification. Owners receive these requests in their inbox and can approve or reject them with a single click, instantly provisioning access rights and logging the decision."

---

### [3:45 - 4:30] Immutable Audit Ledger & Cryptographic Tamper Proofing
* **Visual:**
  - Click the **"Admin"** demo button.
  - Navigate to **"Audit Trail Ledger"**. Show all activities: logins, uploads, permission grants, revoked access, and the recorded unauthorized probe.
  - Highlight the `previous_hash` and `current_hash` columns.
  - Navigate to **"Security & Tamper Center"**.
  - Click **"Run Tamper Verification"**: screen displays green *"100% VERIFIED: All blocks chained with SHA-256 integrity"*.
* **Speaker Script:**
  > "Now let's examine TrustVault's security crown jewel: the Immutable Audit Ledger.
  > Logged in as Admin, we can review every system action. But unlike ordinary databases where a rogue DBA or attacker could tamper with logs, TrustVault chains every audit record to the preceding row using SHA-256 hash pointers.
  > When we run Tamper Verification, the system recalculates every hash in sequence from the Genesis block to the present. If even a single byte or record is altered in the database, the chain breaks and pinpoints the exact compromised record."

---

### [4:30 - 5:00] Automated Security Tests & Closing
* **Visual:**
  - Switch to terminal: run `run_maven.bat test` (or show the successful test output).
  - Show the 8/8 automated security tests passing:
    - IDOR defense verified.
    - Unauthorized access audit logging verified.
    - Instant revocation verified.
    - Database tamper detection verified.
* **Speaker Script:**
  > "All these security properties are backed by our automated JUnit security test suite, proving 100% backend enforcement.
  > TrustVault demonstrates robust enterprise architecture, proactive threat thinking, and rigorous code quality.
  > Thank you for reviewing TrustVault!"
