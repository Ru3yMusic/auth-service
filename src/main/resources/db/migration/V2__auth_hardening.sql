-- V2: Auth hardening — OTP attempt tracking + locked flag
-- Also extends code column to hold SHA-256 hex (64 chars) and cleans stale records.

ALTER TABLE email_verifications ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE email_verifications ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE email_verifications ALTER COLUMN code TYPE VARCHAR(64);
DELETE FROM email_verifications WHERE expires_at < NOW();
