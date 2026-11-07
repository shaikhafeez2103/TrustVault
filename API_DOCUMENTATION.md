# TrustVault – REST API Documentation

**Base URL:** `http://localhost:8082/api`  
**Authentication:** HTTP Authorization Header `Bearer <JWT_TOKEN>`

---

## 1. Authentication Endpoints (`/api/auth`)

### 1.1 User Login
* **Endpoint:** `POST /api/auth/login`
* **Access:** Public
* **Request Body:**
```json
{
  "username": "owner",
  "password": "<YOUR_PASSWORD>"
}
```
* **Success Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "id": 2,
  "username": "owner",
  "email": "owner@trustvault.io",
  "fullName": "Alice Jenkins (Chief Legal Officer)",
  "role": "ROLE_OWNER"
}
```
* **Failure Response (401 Unauthorized):**
```json
{
  "timestamp": "2026-11-05T21:55:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```

### 1.2 User Registration
* **Endpoint:** `POST /api/auth/register`
* **Access:** Public (assigns `ROLE_OWNER` by default; ignores client-supplied elevated roles)
* **Request Body:**
```json
{
  "username": "newanalyst",
  "email": "analyst@trustvault.io",
  "password": "<STRONG_PASSWORD>",
  "fullName": "Security Analyst"
}
```

### 1.3 Get Current User Profile
* **Endpoint:** `GET /api/auth/me`
* **Access:** Authenticated

---

## 2. Document Management Endpoints (`/api/documents`)

### 2.1 Upload Confidential Document
* **Endpoint:** `POST /api/documents/upload`
* **Access:** Authenticated (`ROLE_OWNER`, `ROLE_ADMIN`)
* **Content-Type:** `multipart/form-data`
* **Parameters:**
  - `title` (string, required)
  - `category` (string, e.g. "Legal & Board", "Finance", "Engineering")
  - `description` (string, optional)
  - `file` (binary, max 10MB, inspected by Apache Tika)

### 2.2 Search Documents
* **Endpoint:** `GET /api/documents/search`
* **Access:** Authenticated (filtered by authorization policy)
* **Query Parameters:**
  - `query` (optional string): Matches title or description
  - `category` (optional string): Filter by category
  - `owner` (optional string): Filter by owner username
  - `startDate`, `endDate` (optional ISO date): Filter by date range

### 2.3 Get My Documents
* **Endpoint:** `GET /api/documents/my`
* **Access:** Authenticated (returns documents owned by the caller)

### 2.4 Get Shared With Me
* **Endpoint:** `GET /api/documents/shared`
* **Access:** Authenticated (returns documents where caller has active permissions)

### 2.5 Get Document Metadata (IDOR-Protected)
* **Endpoint:** `GET /api/documents/{id}`
* **Access:** Document Owner, or users with active `READ`/`EDIT` permissions.
* **Failure:** Returns `403 Forbidden` and creates an audit entry if unauthorized.

### 2.6 Download Document with Watermarking
* **Endpoint:** `GET /api/documents/{id}/download?watermark=true`
* **Access:** Document Owner, or users with active `READ`/`EDIT` permissions.
* **Response:** Binary file stream. PDFs receive forensic visual watermark stamped with viewer username, UTC timestamp, and client IP.

### 2.7 Upload New Document Version
* **Endpoint:** `POST /api/documents/{id}/versions`
* **Access:** Document Owner, or users with `EDIT` permission.
* **Content-Type:** `multipart/form-data`
* **Parameters:** `file`, `changeSummary`

### 2.8 Delete Document
* **Endpoint:** `DELETE /api/documents/{id}`
* **Access:** Document Owner only (or `ROLE_ADMIN`).

---

## 3. Permissions & Instant Revocation (`/api/permissions`)

### 3.1 Grant Document Permission
* **Endpoint:** `POST /api/permissions/documents/{id}/grant`
* **Access:** Document Owner only
* **Request Body:**
```json
{
  "username": "viewer",
  "permissionType": "READ",
  "expiresAt": "2026-12-01T00:00:00"
}
```

### 3.2 Revoke Permission Immediately
* **Endpoint:** `DELETE /api/permissions/documents/{id}/revoke/{permissionId}`
* **Access:** Document Owner only
* **Behavior:** Zero-grace period. The permission record is set to `active=false`, immediately terminating subsequent reads or downloads.

### 3.3 List Permissions on Document
* **Endpoint:** `GET /api/permissions/documents/{id}`
* **Access:** Document Owner or Admin

---

## 4. Access Requests Inbox (`/api/requests`)

### 4.1 Submit Access Request
* **Endpoint:** `POST /api/requests`
* **Access:** Authenticated
* **Request Body:**
```json
{
  "documentId": 1,
  "requestedPermission": "READ",
  "reason": "Required for compliance audit review."
}
```

### 4.2 Get Pending Requests (For Owners)
* **Endpoint:** `GET /api/requests/pending`
* **Access:** Authenticated (returns requests for caller's owned documents)

### 4.3 Review Request (Approve or Reject)
* **Endpoint:** `POST /api/requests/{id}/review?approve=true`
* **Access:** Document Owner
* **Behavior:** If approved, automatically provisions active `DocumentPermission` and logs audit entry.

---

## 5. Security Operations & Tamper Verification (`/api/admin`, `/api/tamper`)

### 5.1 Query Immutable Audit Ledger
* **Endpoint:** `GET /api/admin/audit-logs`
* **Access:** `ROLE_ADMIN` only
* **Response Sample:**
```json
[
  {
    "id": 14,
    "timestamp": "2026-11-05T21:55:00",
    "userId": 2,
    "username": "owner",
    "userRole": "ROLE_OWNER",
    "action": "PERMISSION_GRANTED",
    "resourceType": "PERMISSION",
    "resourceId": "4",
    "details": "Granted READ permission to user 'viewer' on document 'Strategic M&A Agreement'",
    "ipAddress": "127.0.0.1",
    "success": true,
    "previousHash": "a4f8c9b2e3d1...",
    "currentHash": "7b8e19d4f0c2..."
  }
]
```

### 5.2 Verify Cryptographic Hash Chain
* **Endpoint:** `GET /api/tamper/verify`
* **Access:** `ROLE_ADMIN` only
* **Response (Tamper-Free):**
```json
{
  "valid": true,
  "totalRecordsChecked": 45,
  "brokenRecordId": null,
  "details": "Cryptographic integrity verified. All 45 audit records are tamper-free and correctly chained.",
  "checkedAt": "2026-11-05T21:58:30"
}
```
* **Response (Tampering Detected):**
```json
{
  "valid": false,
  "totalRecordsChecked": 45,
  "brokenRecordId": 12,
  "details": "Data tampering detected at Record ID 12! Record hash mismatch. Stored: a8e9..., Computed: 1b4f...",
  "checkedAt": "2026-11-05T21:58:35"
}
```

### 5.3 Get Security Analytics & Anomaly Detection
* **Endpoint:** `GET /api/admin/stats`
* **Access:** `ROLE_ADMIN` only
* **Response:**
```json
{
  "totalDocuments": 12,
  "totalAuditLogs": 48,
  "failedLogins24h": 0,
  "unauthorizedAccessAttempts24h": 1,
  "activePermissions": 4,
  "tamperDetected": false,
  "suspiciousActivityFlag": false
}
```
