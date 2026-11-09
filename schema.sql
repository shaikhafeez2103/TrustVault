-- =====================================================================
-- TrustVault Database Schema (MySQL 8.0+)
-- Secure Document Sharing Platform with Cryptographic Audit Trail
-- =====================================================================

CREATE DATABASE IF NOT EXISTS trustvault_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE trustvault_db;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oauth_provider VARCHAR(30) DEFAULT 'LOCAL',
    oauth_provider_id VARCHAR(120) NULL,
    INDEX idx_user_username (username),
    INDEX idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Documents Table
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_checksum VARCHAR(64) NOT NULL,
    current_version INT NOT NULL DEFAULT 1,
    owner_id BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_doc_owner (owner_id),
    INDEX idx_doc_category (category),
    INDEX idx_doc_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Document Versions Table
CREATE TABLE IF NOT EXISTS document_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_checksum VARCHAR(64) NOT NULL,
    change_summary VARCHAR(255),
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_version_doc (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Document Permissions Table
CREATE TABLE IF NOT EXISTS document_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    granted_by BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at DATETIME NULL,
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_perm_doc_user (document_id, user_id),
    INDEX idx_perm_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Access Requests Table
CREATE TABLE IF NOT EXISTS access_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    requested_permission VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_req_doc (document_id),
    INDEX idx_req_user (requester_id),
    INDEX idx_req_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Audit Logs Table (Tamper-Evident SHA-256 Hash Chained Ledger)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NULL,
    username VARCHAR(100) NOT NULL,
    user_role VARCHAR(30),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(50),
    details VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    current_hash VARCHAR(64) NOT NULL,
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_username (username),
    INDEX idx_audit_action (action),
    INDEX idx_audit_res (resource_type, resource_id),
    INDEX idx_audit_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
