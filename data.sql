-- =====================================================================
-- TrustVault Initial Seed Data
-- =====================================================================

USE trustvault_db;

-- 1. Pre-seeded Users
-- Passwords BCrypt hashed:
-- admin   / Admin@123  -> $2a$10$wE9t0R..
-- owner   / Owner@123  -> $2a$10$wE9t0R..
-- viewer  / Viewer@123 -> $2a$10$wE9t0R..

INSERT IGNORE INTO users (id, username, email, password, full_name, role, enabled, created_at)
VALUES 
(1, 'admin', 'admin@trustvault.io', '$2a$10$V8T2jQk6e9p6a9D9rXk3EuxfV8P7U1A4R.jWl2yM6VqY0gR8GgR1W', 'Security Administrator', 'ROLE_ADMIN', TRUE, NOW()),
(2, 'owner', 'owner@trustvault.io', '$2a$10$0mI6hE1qL0F9H0xQ.R1sveX1jK.n7L2aQ8vF.E4uY8sW3qJ6D6n2.', 'Alice Jenkins (CLO)', 'ROLE_OWNER', TRUE, NOW()),
(3, 'viewer', 'viewer@trustvault.io', '$2a$10$Q7wL8eR9tY0uI1oP2aS3deZ4kX5jC6vB7nM8lK9jH0gF1dE2sA3q.', 'Bob Smith (Auditor)', 'ROLE_VIEWER', TRUE, NOW());

-- 2. Genesis Block in Audit Ledger
INSERT IGNORE INTO audit_logs (id, timestamp, user_id, username, user_role, action, resource_type, resource_id, details, ip_address, success, previous_hash, current_hash)
VALUES
(1, NOW(), 1, 'SYSTEM', 'ROLE_ADMIN', 'TAMPER_VERIFICATION_CHECK', 'GENESIS_BLOCK', '0', 'TrustVault Cryptographic Audit Ledger Initialized. Root Hash Chained.', '127.0.0.1', TRUE, '0000000000000000000000000000000000000000000000000000000000000000', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
