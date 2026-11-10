CREATE TABLE lesson_trial (
    asset_id VARCHAR(36) NOT NULL, seconds INT NOT NULL, state VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NOT NULL,
    lease_token VARCHAR(36), object_key VARCHAR(255), error_code VARCHAR(64),
    PRIMARY KEY(asset_id,seconds), FOREIGN KEY (asset_id) REFERENCES media_asset(id)
);
CREATE INDEX idx_trial_due ON lesson_trial(state,next_attempt_at);
CREATE TABLE lesson_playback (
    id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, lesson_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL,
    access_reason VARCHAR(16) NOT NULL, allowed_seconds INT NOT NULL, expires_at TIMESTAMP(6) NOT NULL,
    sequence_no INT NOT NULL DEFAULT 0, position_seconds INT NOT NULL DEFAULT 0, last_event_at TIMESTAMP(6) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL,
    UNIQUE(user_id,idempotency_key), FOREIGN KEY(user_id) REFERENCES app_user(id),
    FOREIGN KEY(lesson_id) REFERENCES content_lesson(id), FOREIGN KEY(asset_id) REFERENCES media_asset(id)
);
CREATE TABLE lesson_progress (
    user_id VARCHAR(36) NOT NULL, lesson_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL,
    position_seconds INT NOT NULL, credited_seconds INT NOT NULL, completed BOOLEAN NOT NULL,
    last_played_at TIMESTAMP(6) NOT NULL, PRIMARY KEY(user_id,lesson_id),
    FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(lesson_id) REFERENCES content_lesson(id),
    FOREIGN KEY(asset_id) REFERENCES media_asset(id)
);
CREATE TABLE lesson_playback_event (
    playback_id VARCHAR(36) NOT NULL, event_id VARCHAR(36) NOT NULL, sequence_no INT NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL, ack_json VARCHAR(1024) NOT NULL,
    PRIMARY KEY(playback_id,event_id), UNIQUE(playback_id,sequence_no), UNIQUE(playback_id,idempotency_key),
    FOREIGN KEY(playback_id) REFERENCES lesson_playback(id)
);
CREATE TABLE content_reference_history (
    lesson_id VARCHAR(36), course_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL,
    teacher_id VARCHAR(36) NOT NULL, category_id VARCHAR(36) NOT NULL, revision INT NOT NULL,
    FOREIGN KEY(lesson_id) REFERENCES content_lesson(id), FOREIGN KEY(course_id) REFERENCES content_course(id),
    FOREIGN KEY(asset_id) REFERENCES media_asset(id), FOREIGN KEY(teacher_id) REFERENCES content_teacher(id),
    FOREIGN KEY(category_id) REFERENCES content_category(id)
);
CREATE TABLE playback_ticket_snapshot (
    playback_id VARCHAR(36) NOT NULL, operation VARCHAR(8) NOT NULL, idempotency_key VARCHAR(36) NOT NULL,
    response_json TEXT NOT NULL, expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY(playback_id,operation,idempotency_key), FOREIGN KEY(playback_id) REFERENCES lesson_playback(id)
);
