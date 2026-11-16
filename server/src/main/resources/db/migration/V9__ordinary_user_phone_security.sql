ALTER TABLE app_user ADD COLUMN phone VARCHAR(11);
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_phone UNIQUE(phone);
ALTER TABLE app_user ADD COLUMN phone_version INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN teacher_role BOOLEAN NOT NULL DEFAULT FALSE;

-- Locks only the short phone ownership transfer, never an external call.
CREATE TABLE phone_binding_control (id INT PRIMARY KEY);
INSERT INTO phone_binding_control VALUES (1);

CREATE TABLE phone_reauth (
    token_hash CHAR(64) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    phone_version INT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_phone_reauth_expiry ON phone_reauth(expires_at);
CREATE TABLE phone_sms_challenge (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    phone VARCHAR(11) NOT NULL,
    phone_version INT NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    code_hash VARCHAR(80) NOT NULL,
    state VARCHAR(12) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_phone_sms_expiry ON phone_sms_challenge(expires_at);
CREATE INDEX idx_phone_sms_owner ON phone_sms_challenge(user_id,session_id,purpose);

-- Old grant operators do not automatically gain account banning or directory access.
INSERT INTO permission(code,name,module) VALUES
 ('user_directory:read','查看普通用户','users'),('user_directory:write','管理普通用户状态及角色','users');
