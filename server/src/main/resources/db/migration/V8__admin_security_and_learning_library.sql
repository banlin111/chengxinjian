-- Additive migration: V1-V7 remain immutable.
CREATE TABLE admin_security_control (
    id INT PRIMARY KEY, bootstrap_completed BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO admin_security_control(id,bootstrap_completed) VALUES (1,FALSE);
ALTER TABLE admin_role ADD COLUMN data_scope VARCHAR(32) NOT NULL DEFAULT 'all';
ALTER TABLE admin_role ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE admin_role ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE admin_role ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
INSERT INTO permission(code,name,module) VALUES ('rbac:read','查看后台权限','rbac');
INSERT INTO permission(code,name,module) VALUES ('rbac:write','维护后台权限','rbac');
INSERT INTO permission(code,name,module) VALUES ('audit:read','查看审计记录','audit');
CREATE TABLE captcha_consumption (
    ticket_hash CHAR(64) PRIMARY KEY, consumed_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_captcha_consumed ON captcha_consumption(consumed_at);
CREATE TABLE user_content_relation (
    user_id VARCHAR(36) NOT NULL, kind VARCHAR(16) NOT NULL, target_id VARCHAR(36) NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE, liked BOOLEAN NOT NULL DEFAULT FALSE,
    saved_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY(user_id,kind,target_id), FOREIGN KEY(user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_relation_target_likes ON user_content_relation(kind,target_id,liked);
CREATE INDEX idx_relation_library ON user_content_relation(user_id,favorite,saved_at);
CREATE INDEX idx_learning_recent ON lesson_progress(user_id,last_played_at);
