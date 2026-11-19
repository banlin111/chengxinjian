-- The original key remains on the event. Record alternate keys acknowledged for the same event.
CREATE TABLE playback_event_retry_key (
    playback_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    PRIMARY KEY(playback_id,request_key),
    FOREIGN KEY(playback_id,sequence_no) REFERENCES lesson_playback_event(playback_id,sequence_no) ON DELETE CASCADE
);
