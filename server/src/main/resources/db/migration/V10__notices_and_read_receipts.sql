CREATE TABLE content_notice (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_notice_publication ON content_notice(status,published_at,id);
CREATE TABLE user_notice_read (
    user_id VARCHAR(36) NOT NULL,
    notice_id VARCHAR(36) NOT NULL,
    notice_version INT NOT NULL,
    read_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY(user_id,notice_id),
    FOREIGN KEY(user_id) REFERENCES app_user(id),
    FOREIGN KEY(notice_id) REFERENCES content_notice(id)
);
