CREATE TABLE admin_account (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    data_scope VARCHAR(32) NOT NULL,
    teacher_id VARCHAR(36),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE admin_role (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);
CREATE TABLE permission (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    module VARCHAR(32) NOT NULL
);
CREATE TABLE admin_account_role (
    account_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (account_id, role_id),
    FOREIGN KEY (account_id) REFERENCES admin_account(id),
    FOREIGN KEY (role_id) REFERENCES admin_role(id)
);
CREATE TABLE admin_role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    FOREIGN KEY (role_id) REFERENCES admin_role(id),
    FOREIGN KEY (permission_code) REFERENCES permission(code)
);
CREATE TABLE auth_session (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    audience VARCHAR(16) NOT NULL,
    access_hash CHAR(64) NOT NULL UNIQUE,
    refresh_hash CHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMP(6) NOT NULL,
    refresh_expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES admin_account(id)
);
CREATE INDEX idx_session_account ON auth_session(account_id, audience);
CREATE TABLE login_throttle (
    throttle_key CHAR(64) PRIMARY KEY,
    failed_count INT NOT NULL,
    window_started_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE content_category (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_category_catalog ON content_category(kind, status, sort_order);
CREATE TABLE idempotency_record (
    actor_id VARCHAR(36) NOT NULL,
    operation VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_id VARCHAR(36),
    result_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (actor_id, operation, idempotency_key)
);
CREATE TABLE audit_log (
    id VARCHAR(36) PRIMARY KEY,
    operator_id VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    reason VARCHAR(500),
    request_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_audit_operator_time ON audit_log(operator_id, occurred_at);
INSERT INTO permission(code,name,module) VALUES ('content:read','查看内容','content');
INSERT INTO permission(code,name,module) VALUES ('content:write','维护内容','content');
INSERT INTO admin_role(id,name) VALUES ('role-content-manager','内容管理员');
INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager','content:read');
INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager','content:write');
