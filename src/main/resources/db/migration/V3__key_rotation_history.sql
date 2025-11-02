-- Key Rotation History Table
-- 키 회전 이력을 저장하는 테이블
-- 감사(Audit) 및 모니터링 목적

CREATE TABLE IF NOT EXISTS key_rotation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    rotation_type VARCHAR(50) NOT NULL,
    previous_key_id INTEGER,
    new_key_id INTEGER,
    keys_added INTEGER,
    success BOOLEAN NOT NULL DEFAULT true,
    error_message TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_key_rotation_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT check_rotation_type CHECK (rotation_type IN ('SIGNED_PRE_KEY', 'ONE_TIME_PRE_KEYS', 'BOTH'))
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_key_rotation_history_user_id ON key_rotation_history(user_id);
CREATE INDEX IF NOT EXISTS idx_key_rotation_history_created_at ON key_rotation_history(created_at);
CREATE INDEX IF NOT EXISTS idx_key_rotation_history_rotation_type ON key_rotation_history(rotation_type);

-- 설명 추가
COMMENT ON TABLE key_rotation_history IS '키 회전 이력 테이블 - 감사 및 모니터링 목적';
COMMENT ON COLUMN key_rotation_history.user_id IS '키를 회전한 사용자 ID';
COMMENT ON COLUMN key_rotation_history.rotation_type IS '회전 타입: SIGNED_PRE_KEY, ONE_TIME_PRE_KEYS, BOTH';
COMMENT ON COLUMN key_rotation_history.previous_key_id IS '이전 Signed Pre-Key ID';
COMMENT ON COLUMN key_rotation_history.new_key_id IS '새 Signed Pre-Key ID';
COMMENT ON COLUMN key_rotation_history.keys_added IS '추가된 One-Time Pre-Keys 개수';
COMMENT ON COLUMN key_rotation_history.success IS '회전 작업 성공 여부';
COMMENT ON COLUMN key_rotation_history.error_message IS '오류 메시지 (실패한 경우)';
COMMENT ON COLUMN key_rotation_history.execution_time_ms IS '회전 작업 실행 시간 (밀리초)';
COMMENT ON COLUMN key_rotation_history.created_at IS '회전 작업 생성 시각';

