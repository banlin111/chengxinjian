CREATE TABLE practice_checkin (
    user_id VARCHAR(36) NOT NULL,
    business_day DATE NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    credited_seconds INT NOT NULL,
    streak_days INT NOT NULL,
    rule_version VARCHAR(80) NOT NULL,
    threshold_seconds INT NOT NULL,
    PRIMARY KEY (user_id, business_day),
    FOREIGN KEY (session_id) REFERENCES practice_session(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE practice_checkin_request (
    user_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    business_day DATE NOT NULL,
    was_checked_in BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, request_key),
    FOREIGN KEY (user_id, business_day) REFERENCES practice_checkin(user_id, business_day),
    FOREIGN KEY (session_id) REFERENCES practice_session(id)
);
