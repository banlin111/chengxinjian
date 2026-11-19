CREATE TABLE practice_audio (
    id VARCHAR(36) PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000),
    category_id VARCHAR(36) NOT NULL,
    teacher_id VARCHAR(36),
    cover_asset_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    access_policy VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(category_id) REFERENCES content_category(id),
    FOREIGN KEY(teacher_id) REFERENCES content_teacher(id),
    FOREIGN KEY(cover_asset_id) REFERENCES media_asset(id),
    FOREIGN KEY(asset_id) REFERENCES media_asset(id)
);
CREATE INDEX ix_practice_audio_list ON practice_audio(kind,status,created_at,id);
ALTER TABLE practice_session ADD COLUMN kind VARCHAR(12) NOT NULL DEFAULT 'timer';
CREATE TABLE practice_audio_track (
    session_id VARCHAR(36) NOT NULL,
    audio_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    track_no INT NOT NULL,
    volume DOUBLE NOT NULL,
    PRIMARY KEY(session_id,audio_id),
    FOREIGN KEY(session_id) REFERENCES practice_session(id),
    FOREIGN KEY(audio_id) REFERENCES practice_audio(id),
    FOREIGN KEY(asset_id) REFERENCES media_asset(id)
);
ALTER TABLE lesson_playback MODIFY COLUMN lesson_id VARCHAR(36) NULL;
ALTER TABLE lesson_playback ADD COLUMN audio_id VARCHAR(36);
ALTER TABLE lesson_playback ADD COLUMN practice_id VARCHAR(36);
ALTER TABLE lesson_playback ADD COLUMN audio_seconds INT NOT NULL DEFAULT 0;
ALTER TABLE lesson_playback ADD FOREIGN KEY(audio_id) REFERENCES practice_audio(id);
ALTER TABLE lesson_playback ADD FOREIGN KEY(practice_id) REFERENCES practice_session(id);
ALTER TABLE lesson_playback ADD CHECK ((lesson_id IS NOT NULL AND audio_id IS NULL AND practice_id IS NULL) OR
    (lesson_id IS NULL AND audio_id IS NOT NULL));
CREATE INDEX ix_practice_audio_resume ON lesson_playback(user_id,audio_id,asset_id,practice_id,last_event_at);
CREATE TABLE practice_played_interval (
    playback_id VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    start_sec BIGINT NOT NULL,
    end_sec BIGINT NOT NULL,
    PRIMARY KEY(playback_id,sequence_no),
    FOREIGN KEY(playback_id) REFERENCES lesson_playback(id),
    FOREIGN KEY(session_id) REFERENCES practice_session(id),
    CHECK(end_sec>start_sec)
);
CREATE INDEX ix_practice_audio_time ON practice_played_interval(session_id,end_sec);
ALTER TABLE practice_event ADD COLUMN claim_start_sec BIGINT;
ALTER TABLE practice_event ADD COLUMN claim_end_sec BIGINT;
CREATE INDEX ix_practice_claim ON practice_event(session_id,claim_end_sec);
