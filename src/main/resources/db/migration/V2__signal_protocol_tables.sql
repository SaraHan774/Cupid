-- ================================================================
-- Signal Protocol Database Migration
-- Version: 2.0
-- Date: 2025-01-19
-- Description: Creates tables for Signal Protocol E2E encryption
-- ================================================================

-- ================================================================
-- 1. Modify existing user_keys table to add missing columns
-- ================================================================

-- Add new columns for private key encryption and protocol data
ALTER TABLE user_keys
ADD COLUMN IF NOT EXISTS identity_private_key_encrypted TEXT NOT NULL DEFAULT '',
ADD COLUMN IF NOT EXISTS device_id INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS registration_id INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS signed_pre_key TEXT NOT NULL DEFAULT '',
ADD COLUMN IF NOT EXISTS pre_key_signature TEXT NOT NULL DEFAULT '';

-- Rename old columns for clarity (if they exist)
ALTER TABLE user_keys
RENAME COLUMN IF EXISTS identity_key TO identity_public_key;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_keys_device_id ON user_keys(device_id);

-- Add comments
COMMENT ON COLUMN user_keys.identity_public_key IS 'Public identity key (Base64 encoded, Curve25519)';
COMMENT ON COLUMN user_keys.identity_private_key_encrypted IS 'Private identity key (Base64 encoded, AES-256-GCM encrypted with user password)';
COMMENT ON COLUMN user_keys.device_id IS 'Device ID for multi-device support (default: 1)';
COMMENT ON COLUMN user_keys.registration_id IS 'Random 14-bit registration ID for X3DH protocol';
COMMENT ON COLUMN user_keys.signed_pre_key IS 'Current signed pre-key (Base64 encoded, serialized SignedPreKeyRecord)';
COMMENT ON COLUMN user_keys.pre_key_signature IS 'Signature of signed pre-key by identity key (Base64 encoded)';

-- ================================================================
-- 2. Create signal_sessions table (Double Ratchet state storage)
-- ================================================================

CREATE TABLE IF NOT EXISTS signal_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address_name VARCHAR(500) NOT NULL,
    address_device_id INTEGER NOT NULL,
    session_record TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT uq_signal_sessions_address UNIQUE (user_id, address_name, address_device_id)
);

CREATE INDEX idx_signal_sessions_user_id ON signal_sessions(user_id);
CREATE INDEX idx_signal_sessions_address ON signal_sessions(address_name, address_device_id);
CREATE INDEX idx_signal_sessions_created_at ON signal_sessions(created_at);
CREATE INDEX idx_signal_sessions_last_used ON signal_sessions(last_used_at);

COMMENT ON TABLE signal_sessions IS 'Signal Protocol session state storage (Double Ratchet)';
COMMENT ON COLUMN signal_sessions.address_name IS 'Remote party address (usually their user ID)';
COMMENT ON COLUMN signal_sessions.address_device_id IS 'Remote party device ID';
COMMENT ON COLUMN signal_sessions.session_record IS 'Encrypted session record (Base64 encoded, contains ratchet state)';
COMMENT ON COLUMN signal_sessions.last_used_at IS 'Last time this session was used for encryption/decryption';

-- ================================================================
-- 3. Create signal_identities table (MITM attack detection)
-- ================================================================

CREATE TYPE trust_level AS ENUM ('UNTRUSTED', 'TRUSTED', 'CHANGED');

CREATE TABLE IF NOT EXISTS signal_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address_name VARCHAR(500) NOT NULL,
    address_device_id INTEGER NOT NULL,
    identity_key TEXT NOT NULL,
    trust_level trust_level NOT NULL DEFAULT 'UNTRUSTED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMP,
    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_signal_identities_address UNIQUE (user_id, address_name, address_device_id)
);

CREATE INDEX idx_signal_identities_user_id ON signal_identities(user_id);
CREATE INDEX idx_signal_identities_address ON signal_identities(address_name, address_device_id);
CREATE INDEX idx_signal_identities_trust_level ON signal_identities(trust_level);

COMMENT ON TABLE signal_identities IS 'Identity key verification for MITM attack prevention';
COMMENT ON COLUMN signal_identities.identity_key IS 'Remote party identity public key (Base64 encoded)';
COMMENT ON COLUMN signal_identities.trust_level IS 'Trust level: UNTRUSTED (default), TRUSTED (verified), CHANGED (potential MITM)';
COMMENT ON COLUMN signal_identities.verified_at IS 'When this identity was verified by the user';
COMMENT ON COLUMN signal_identities.first_seen_at IS 'First time this identity was seen (for tracking key changes)';

-- ================================================================
-- 4. Create signal_pre_keys table (One-time pre-keys for X3DH)
-- ================================================================

CREATE TABLE IF NOT EXISTS signal_pre_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pre_key_id INTEGER NOT NULL,
    public_key TEXT NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL DEFAULT NOW() + INTERVAL '90 days',

    CONSTRAINT uq_signal_pre_keys_key_id UNIQUE (user_id, pre_key_id)
);

CREATE INDEX idx_signal_pre_keys_user_id ON signal_pre_keys(user_id);
CREATE INDEX idx_signal_pre_keys_is_used ON signal_pre_keys(is_used);
CREATE INDEX idx_signal_pre_keys_created_at ON signal_pre_keys(created_at);
CREATE INDEX idx_signal_pre_keys_expires_at ON signal_pre_keys(expires_at);

COMMENT ON TABLE signal_pre_keys IS 'One-time pre-keys for X3DH key agreement';
COMMENT ON COLUMN signal_pre_keys.pre_key_id IS 'Pre-key ID (unique per user)';
COMMENT ON COLUMN signal_pre_keys.public_key IS 'Pre-key public key (Base64 encoded)';
COMMENT ON COLUMN signal_pre_keys.private_key_encrypted IS 'Pre-key private key (Base64 encoded, AES-256-GCM encrypted)';
COMMENT ON COLUMN signal_pre_keys.is_used IS 'Whether this one-time pre-key has been used (MUST be single-use)';
COMMENT ON COLUMN signal_pre_keys.used_at IS 'When this pre-key was used';
COMMENT ON COLUMN signal_pre_keys.expires_at IS 'Expiration time (recommended: 30-90 days)';

-- ================================================================
-- 5. Create signal_signed_pre_keys table (Signed pre-keys for X3DH)
-- ================================================================

CREATE TABLE IF NOT EXISTS signal_signed_pre_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    signed_pre_key_id INTEGER NOT NULL,
    public_key TEXT NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    signature TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL DEFAULT NOW() + INTERVAL '30 days',

    CONSTRAINT uq_signal_signed_pre_keys_key_id UNIQUE (user_id, signed_pre_key_id)
);

CREATE INDEX idx_signal_signed_pre_keys_user_id ON signal_signed_pre_keys(user_id);
CREATE INDEX idx_signal_signed_pre_keys_is_active ON signal_signed_pre_keys(is_active);
CREATE INDEX idx_signal_signed_pre_keys_created_at ON signal_signed_pre_keys(created_at);
CREATE INDEX idx_signal_signed_pre_keys_expires_at ON signal_signed_pre_keys(expires_at);

COMMENT ON TABLE signal_signed_pre_keys IS 'Signed pre-keys for X3DH key agreement';
COMMENT ON COLUMN signal_signed_pre_keys.signed_pre_key_id IS 'Signed pre-key ID';
COMMENT ON COLUMN signal_signed_pre_keys.public_key IS 'Signed pre-key public key (Base64 encoded)';
COMMENT ON COLUMN signal_signed_pre_keys.private_key_encrypted IS 'Signed pre-key private key (Base64 encoded, encrypted)';
COMMENT ON COLUMN signal_signed_pre_keys.signature IS 'Signature by identity key (Base64 encoded)';
COMMENT ON COLUMN signal_signed_pre_keys.timestamp IS 'Generation timestamp (milliseconds since epoch)';
COMMENT ON COLUMN signal_signed_pre_keys.is_active IS 'Whether this is the currently active signed pre-key';
COMMENT ON COLUMN signal_signed_pre_keys.expires_at IS 'Expiration time (recommended: weekly or monthly rotation)';

-- ================================================================
-- 6. Create cleanup functions and scheduled jobs
-- ================================================================

-- Function to clean up expired pre-keys
CREATE OR REPLACE FUNCTION cleanup_expired_pre_keys()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM signal_pre_keys
    WHERE expires_at < NOW();

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_pre_keys() IS 'Deletes expired one-time pre-keys';

-- Function to clean up old used pre-keys (keep for 7 days after use)
CREATE OR REPLACE FUNCTION cleanup_old_used_pre_keys()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM signal_pre_keys
    WHERE is_used = TRUE
      AND used_at < NOW() - INTERVAL '7 days';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_used_pre_keys() IS 'Deletes used one-time pre-keys older than 7 days';

-- Function to clean up inactive sessions (not used in 30 days)
CREATE OR REPLACE FUNCTION cleanup_inactive_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM signal_sessions
    WHERE last_used_at < NOW() - INTERVAL '30 days';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_inactive_sessions() IS 'Deletes sessions not used in 30 days';

-- Function to get pre-key statistics
CREATE OR REPLACE FUNCTION get_pre_key_stats(p_user_id UUID)
RETURNS TABLE (
    total_pre_keys INTEGER,
    available_pre_keys INTEGER,
    used_pre_keys INTEGER,
    expired_pre_keys INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*)::INTEGER AS total_pre_keys,
        COUNT(*) FILTER (WHERE is_used = FALSE AND expires_at > NOW())::INTEGER AS available_pre_keys,
        COUNT(*) FILTER (WHERE is_used = TRUE)::INTEGER AS used_pre_keys,
        COUNT(*) FILTER (WHERE expires_at <= NOW())::INTEGER AS expired_pre_keys
    FROM signal_pre_keys
    WHERE user_id = p_user_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_pre_key_stats(UUID) IS 'Returns pre-key statistics for a user';

-- ================================================================
-- 7. Insert default data and verify migration
-- ================================================================

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE 'Signal Protocol tables created successfully';
    RAISE NOTICE 'Tables: signal_sessions, signal_identities, signal_pre_keys, signal_signed_pre_keys';
    RAISE NOTICE 'Functions: cleanup_expired_pre_keys(), cleanup_old_used_pre_keys(), cleanup_inactive_sessions(), get_pre_key_stats()';
END $$;

-- ================================================================
-- 8. Security grants (adjust based on your user roles)
-- ================================================================

-- GRANT SELECT, INSERT, UPDATE, DELETE ON signal_sessions TO your_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON signal_identities TO your_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON signal_pre_keys TO your_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON signal_signed_pre_keys TO your_app_user;
-- GRANT EXECUTE ON FUNCTION cleanup_expired_pre_keys() TO your_app_user;
-- GRANT EXECUTE ON FUNCTION cleanup_old_used_pre_keys() TO your_app_user;
-- GRANT EXECUTE ON FUNCTION cleanup_inactive_sessions() TO your_app_user;
-- GRANT EXECUTE ON FUNCTION get_pre_key_stats(UUID) TO your_app_user;

-- ================================================================
-- END OF MIGRATION
-- ================================================================
