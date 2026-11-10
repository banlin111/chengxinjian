-- Metadata only: no seeded media, no fake upload service or public paid-media URLs.
CREATE TABLE media_asset (
    id VARCHAR(36) PRIMARY KEY,
    purpose VARCHAR(24) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_seconds INT,
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    owner_user_id VARCHAR(36),
    owner_admin_id VARCHAR(36),
    public_url VARCHAR(2048),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
    FOREIGN KEY (owner_admin_id) REFERENCES admin_account(id),
    CHECK ((owner_user_id IS NULL AND owner_admin_id IS NOT NULL) OR
           (owner_user_id IS NOT NULL AND owner_admin_id IS NULL)),
    CHECK (size_bytes > 0),
    CHECK (public_url IS NULL OR purpose IN ('avatar','cover'))
);
CREATE TABLE content_teacher (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    bio TEXT NOT NULL,
    credentials_json TEXT NOT NULL,
    avatar_asset_id VARCHAR(36),
    user_id VARCHAR(36) UNIQUE,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (avatar_asset_id) REFERENCES media_asset(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_teacher_catalog ON content_teacher(status,created_at);
CREATE TABLE teacher_follow (
    user_id VARCHAR(36) NOT NULL,
    teacher_id VARCHAR(36) NOT NULL,
    followed BOOLEAN NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id,teacher_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (teacher_id) REFERENCES content_teacher(id)
);
ALTER TABLE admin_account ADD CONSTRAINT fk_admin_teacher FOREIGN KEY (teacher_id) REFERENCES content_teacher(id);
