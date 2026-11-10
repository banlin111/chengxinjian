ALTER TABLE media_asset ADD COLUMN quarantine_key VARCHAR(255);
ALTER TABLE media_asset ADD COLUMN upload_expires_at TIMESTAMP(6);
ALTER TABLE media_asset ADD COLUMN quarantine_cleaned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE media_asset ADD COLUMN failure_code VARCHAR(64);
CREATE TABLE media_upload_request (
    actor_id VARCHAR(36) NOT NULL,
    audience VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    asset_id VARCHAR(36) NOT NULL UNIQUE,
    ticket_json TEXT,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP(6),
    PRIMARY KEY(actor_id,audience,idempotency_key),
    FOREIGN KEY(asset_id) REFERENCES media_asset(id)
);
CREATE TABLE media_processing_job (
    asset_id VARCHAR(36) PRIMARY KEY,
    state VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP(6),
    last_error VARCHAR(64),
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(asset_id) REFERENCES media_asset(id)
);
CREATE INDEX idx_media_jobs_due ON media_processing_job(state,next_attempt_at,lease_until);
CREATE INDEX idx_media_cleanup ON media_asset(quarantine_cleaned,upload_expires_at);
