CREATE TABLE practice_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    create_key VARCHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    create_snapshot TEXT NOT NULL,
    status VARCHAR(12) NOT NULL,
    planned_seconds INT NOT NULL,
    credited_seconds INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    started_sec BIGINT NOT NULL,
    last_event_sec BIGINT NOT NULL,
    updated_sec BIGINT NOT NULL,
    ended_sec BIGINT,
    UNIQUE (user_id, create_key),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_practice_history ON practice_session(user_id, started_sec, id);
CREATE INDEX idx_practice_completed ON practice_session(user_id, status, ended_sec);

CREATE TABLE practice_event (
    session_id VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    action VARCHAR(12) NOT NULL,
    occurred_sec BIGINT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    snapshot TEXT NOT NULL,
    PRIMARY KEY (session_id, sequence_no),
    UNIQUE (session_id, event_id),
    FOREIGN KEY (session_id) REFERENCES practice_session(id)
);
CREATE TABLE practice_event_request (
    session_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    PRIMARY KEY (session_id, request_key),
    FOREIGN KEY (session_id, sequence_no) REFERENCES practice_event(session_id, sequence_no)
);

-- Half-open UTC intervals, split at Shanghai midnight. Writers lock the owner before reading overlaps.
CREATE TABLE practice_credit (
    user_id VARCHAR(36) NOT NULL,
    start_sec BIGINT NOT NULL,
    end_sec BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    business_day DATE NOT NULL,
    PRIMARY KEY (user_id, start_sec),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (session_id) REFERENCES practice_session(id),
    CHECK (end_sec > start_sec)
);
CREATE INDEX idx_practice_credit_day ON practice_credit(user_id, business_day);
