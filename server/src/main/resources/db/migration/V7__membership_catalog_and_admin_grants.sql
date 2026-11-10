ALTER TABLE entitlement_grant MODIFY COLUMN expires_at DATETIME(6);
CREATE TABLE membership_plan (
    id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL, period_kind VARCHAR(16) NOT NULL,
    duration_days INT, price_fen INT NOT NULL, original_price_fen INT, benefits_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL, version INT NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    CHECK ((period_kind='lifetime' AND duration_days IS NULL) OR
        (period_kind IN ('month','quarter','year') AND duration_days IS NOT NULL AND duration_days>0))
);
CREATE TABLE admin_grant (
    id VARCHAR(36) PRIMARY KEY, entitlement_id VARCHAR(36) NOT NULL UNIQUE, operator_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36), reason VARCHAR(500) NOT NULL, version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(entitlement_id) REFERENCES entitlement_grant(id), FOREIGN KEY(plan_id) REFERENCES membership_plan(id),
    FOREIGN KEY(operator_id) REFERENCES admin_account(id)
);
INSERT INTO permission(code,name,module) VALUES ('users:read','查看用户权益','users'),('users:write','管理用户权益','users');
