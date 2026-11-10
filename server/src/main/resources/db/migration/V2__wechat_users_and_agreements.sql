-- Independent user identity/session storage; V1 admin foreign keys remain unchanged.
CREATE TABLE app_user (
    id VARCHAR(36) PRIMARY KEY,
    nickname VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'active',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    last_login_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE wechat_identity (
    identity_hash CHAR(64) PRIMARY KEY,
    app_id VARBINARY(64) NOT NULL,
    open_id VARBINARY(128) NOT NULL,
    union_id VARBINARY(128),
    user_id VARCHAR(36),
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE (app_id, open_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_wechat_user ON wechat_identity(user_id);
CREATE TABLE user_auth_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    audience VARCHAR(16) NOT NULL DEFAULT 'user',
    access_hash CHAR(64) NOT NULL UNIQUE,
    refresh_hash CHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMP(6) NOT NULL,
    refresh_expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    CHECK (audience = 'user')
);
CREATE INDEX idx_user_session_user ON user_auth_session(user_id);
CREATE TABLE wechat_code_use (
    code_hash CHAR(64) PRIMARY KEY,
    used_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE agreement_version (
    agreement_type VARCHAR(16) NOT NULL,
    version VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    effective_at TIMESTAMP(6) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    test_only BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (agreement_type, version)
);
CREATE INDEX idx_agreement_current ON agreement_version(agreement_type, published, effective_at);
CREATE TABLE user_agreement_consent (
    user_id VARCHAR(36) NOT NULL,
    agreement_type VARCHAR(16) NOT NULL,
    agreement_version VARCHAR(64) NOT NULL,
    consented_at TIMESTAMP(6) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, agreement_type, agreement_version),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (agreement_type, agreement_version) REFERENCES agreement_version(agreement_type, version)
);
CREATE TABLE user_settings (
    user_id VARCHAR(36) PRIMARY KEY,
    settings_json TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
