-- ================================================================
-- Fix user_keys table: Remove old identity_key column
-- Version: 5.0
-- Date: 2025-11-02
-- Description: Removes duplicate identity_key column and ensures identity_public_key exists
-- ================================================================

-- Drop the old identity_key column if it exists
ALTER TABLE user_keys
DROP COLUMN IF EXISTS identity_key;

-- Ensure identity_public_key column exists with correct constraints
DO $$
BEGIN
    -- Check if identity_public_key column exists
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'user_keys'
        AND column_name = 'identity_public_key'
    ) THEN
        -- Add identity_public_key column if it doesn't exist
        ALTER TABLE user_keys
        ADD COLUMN identity_public_key TEXT NOT NULL DEFAULT '';
    END IF;
END $$;

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE 'Fixed user_keys table: removed identity_key column, ensured identity_public_key exists';
END $$;
