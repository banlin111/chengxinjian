CREATE TABLE mix_preset (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id VARCHAR(36),
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(owner_user_id) REFERENCES app_user(id),
    CHECK (owner_user_id IS NULL OR status IN ('draft','archived'))
);
CREATE INDEX ix_mix_owner_status ON mix_preset(owner_user_id,status,updated_at,id);
CREATE TABLE mix_preset_track (
    preset_id VARCHAR(36) NOT NULL,
    track_no INT NOT NULL,
    noise_id VARCHAR(36) NOT NULL,
    volume DOUBLE NOT NULL,
    PRIMARY KEY(preset_id,track_no),
    UNIQUE(preset_id,noise_id),
    FOREIGN KEY(preset_id) REFERENCES mix_preset(id) ON DELETE CASCADE,
    FOREIGN KEY(noise_id) REFERENCES practice_audio(id),
    CHECK (track_no BETWEEN 0 AND 4 AND volume BETWEEN 0 AND 1)
);
