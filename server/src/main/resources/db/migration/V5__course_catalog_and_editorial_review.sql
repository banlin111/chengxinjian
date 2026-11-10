ALTER TABLE media_asset ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'pending';
CREATE TABLE editorial_image (
    asset_id VARCHAR(36) PRIMARY KEY,
    image_bytes MEDIUMBLOB NOT NULL,
    FOREIGN KEY (asset_id) REFERENCES media_asset(id)
);
CREATE TABLE content_course (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL, summary VARCHAR(1000) NOT NULL, description TEXT NOT NULL,
    cover_asset_id VARCHAR(36) NOT NULL, category_id VARCHAR(36) NOT NULL, teacher_id VARCHAR(36) NOT NULL,
    difficulty VARCHAR(16) NOT NULL, access_policy VARCHAR(24) NOT NULL,
    price_fen INT NOT NULL, original_price_fen INT, status VARCHAR(16) NOT NULL,
    version INT NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (cover_asset_id) REFERENCES media_asset(id),
    FOREIGN KEY (category_id) REFERENCES content_category(id),
    FOREIGN KEY (teacher_id) REFERENCES content_teacher(id)
);
CREATE INDEX idx_course_catalog ON content_course(status,created_at,id);
CREATE TABLE content_lesson (
    id VARCHAR(36) PRIMARY KEY, course_id VARCHAR(36) NOT NULL, title VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL, asset_id VARCHAR(36) NOT NULL, trial_seconds INT NOT NULL,
    status VARCHAR(16) NOT NULL, version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (course_id) REFERENCES content_course(id),
    FOREIGN KEY (asset_id) REFERENCES media_asset(id)
);
CREATE INDEX idx_lesson_catalog ON content_lesson(course_id,status,sort_order,id);
CREATE TABLE entitlement_grant (
    id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, kind VARCHAR(16) NOT NULL,
    target_id VARCHAR(36) NOT NULL, course_id VARCHAR(36), source VARCHAR(16) NOT NULL, source_id VARCHAR(64) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL, expires_at TIMESTAMP(6), revoked_at TIMESTAMP(6),
    UNIQUE (source, source_id, kind, target_id, user_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id), FOREIGN KEY (course_id) REFERENCES content_course(id),
    CHECK ((kind='course' AND course_id IS NOT NULL AND target_id=course_id) OR (kind='membership' AND course_id IS NULL)),
    CHECK (source IN ('admin','payment','reward')), CHECK (expires_at IS NULL OR expires_at>starts_at)
);
CREATE INDEX idx_entitlement_user ON entitlement_grant(user_id,kind,target_id);
INSERT INTO permission(code,name,module) VALUES ('moderation:review','审核编辑素材','moderation');
