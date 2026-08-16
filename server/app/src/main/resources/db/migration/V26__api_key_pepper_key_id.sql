ALTER TABLE api_keys
    ADD COLUMN key_id VARCHAR(64) NULL AFTER key_hash;
