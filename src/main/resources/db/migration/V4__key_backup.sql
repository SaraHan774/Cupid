-- ================================================================
-- Key Backup Database Migration
-- Version: 4.0
-- Date: 2025-11-02
-- Description: Creates table for Signal Protocol key backup/recovery
-- ================================================================

-- ================================================================
-- 1. Create key_backups table
-- ================================================================

CREATE TABLE IF NOT EXISTS key_backups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- 암호화된 백업 데이터 (AES-256-GCM)
    -- 백업 비밀번호로 암호화된 Signal Protocol 키 데이터
    encrypted_backup_data TEXT NOT NULL,
    
    -- 백업 데이터 해시 (SHA-256, 무결성 검증용)
    backup_hash VARCHAR(64) NOT NULL,
    
    -- 백업 생성 시점
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 백업 만료 시간 (기본 90일, NULL이면 만료되지 않음)
    expires_at TIMESTAMP,
    
    -- 백업 메타데이터 (JSON)
    -- 예: {"device_name": "iPhone 13", "app_version": "1.0.0"}
    metadata TEXT,
    
    -- 백업이 사용되었는지 여부 (복구 시 true로 설정)
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- 백업 사용 시점
    used_at TIMESTAMP,
    
    CONSTRAINT fk_key_backups_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_key_backups_user_id ON key_backups(user_id);
CREATE INDEX idx_key_backups_expires_at ON key_backups(expires_at);
CREATE INDEX idx_key_backups_created_at ON key_backups(created_at);
CREATE INDEX idx_key_backups_is_used ON key_backups(is_used) WHERE is_used = FALSE;

-- 컬럼 설명
COMMENT ON TABLE key_backups IS 'Signal Protocol 키 백업 저장소';
COMMENT ON COLUMN key_backups.encrypted_backup_data IS '백업 비밀번호로 암호화된 Signal Protocol 키 데이터 (AES-256-GCM)';
COMMENT ON COLUMN key_backups.backup_hash IS '백업 데이터의 SHA-256 해시 (무결성 검증용)';
COMMENT ON COLUMN key_backups.expires_at IS '백업 만료 시간 (기본 90일)';
COMMENT ON COLUMN key_backups.metadata IS '백업 메타데이터 (JSON 형식)';
COMMENT ON COLUMN key_backups.is_used IS '백업이 복구에 사용되었는지 여부';

-- ================================================================
-- 2. Create cleanup function for expired backups
-- ================================================================

CREATE OR REPLACE FUNCTION cleanup_expired_key_backups()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM key_backups
    WHERE expires_at IS NOT NULL 
      AND expires_at < NOW();

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_key_backups() IS '만료된 키 백업 삭제';

-- ================================================================
-- 3. Create function to get backup statistics
-- ================================================================

CREATE OR REPLACE FUNCTION get_key_backup_stats(p_user_id UUID)
RETURNS TABLE (
    total_backups INTEGER,
    active_backups INTEGER,
    expired_backups INTEGER,
    used_backups INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*)::INTEGER AS total_backups,
        COUNT(*) FILTER (WHERE is_used = FALSE AND (expires_at IS NULL OR expires_at > NOW()))::INTEGER AS active_backups,
        COUNT(*) FILTER (WHERE expires_at IS NOT NULL AND expires_at <= NOW())::INTEGER AS expired_backups,
        COUNT(*) FILTER (WHERE is_used = TRUE)::INTEGER AS used_backups
    FROM key_backups
    WHERE user_id = p_user_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_key_backup_stats(UUID) IS '사용자의 키 백업 통계 조회';

-- ================================================================
-- 4. Log migration completion
-- ================================================================

DO $$
BEGIN
    RAISE NOTICE 'Key backup tables created successfully';
    RAISE NOTICE 'Table: key_backups';
    RAISE NOTICE 'Functions: cleanup_expired_key_backups(), get_key_backup_stats()';
END $$;

-- ================================================================
-- END OF MIGRATION
-- ================================================================

