CREATE TABLE practice_submission (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL UNIQUE,
    recording_hash CHAR(64) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    status VARCHAR(12) NOT NULL,
    review_reason VARCHAR(500),
    version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE(user_id,recording_hash),
    FOREIGN KEY(user_id) REFERENCES app_user(id),
    FOREIGN KEY(session_id) REFERENCES practice_session(id),
    FOREIGN KEY(asset_id) REFERENCES media_asset(id),
    CHECK (status IN ('pending','approved','rejected'))
);
CREATE INDEX ix_submission_session ON practice_submission(session_id,status);
CREATE INDEX ix_submission_review ON practice_submission(status,created_at,id);
CREATE TABLE recording_reward_rule (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    course_id VARCHAR(36) NOT NULL,
    cover_asset_id VARCHAR(36) NOT NULL,
    stock INT,
    issued INT NOT NULL DEFAULT 0,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6),
    per_user_limit INT NOT NULL,
    status VARCHAR(12) NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CHECK (issued>=0 AND (stock IS NULL OR stock>=issued) AND per_user_limit>0),
    FOREIGN KEY(course_id) REFERENCES content_course(id),
    FOREIGN KEY(cover_asset_id) REFERENCES media_asset(id)
);
CREATE TABLE recording_reward_claim (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    rule_id VARCHAR(36) NOT NULL,
    rule_version INT NOT NULL,
    submission_id VARCHAR(36) NOT NULL UNIQUE,
    session_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(36) NOT NULL,
    entitlement_id VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE(user_id,rule_id,session_id),
    UNIQUE(user_id,course_id),
    FOREIGN KEY(user_id) REFERENCES app_user(id),
    FOREIGN KEY(rule_id) REFERENCES recording_reward_rule(id),
    FOREIGN KEY(submission_id) REFERENCES practice_submission(id),
    FOREIGN KEY(session_id) REFERENCES practice_session(id),
    FOREIGN KEY(course_id) REFERENCES content_course(id),
    FOREIGN KEY(entitlement_id) REFERENCES entitlement_grant(id)
);
INSERT INTO permission(code,name,module) VALUES ('rewards:manage','配置与发布录制奖励规则','rewards');
