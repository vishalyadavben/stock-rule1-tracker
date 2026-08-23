-- Flyway migration V2: personal notes feature.
-- This is the exact kind of change that used to force `docker compose down -v` — from now
-- on, a new numbered file here is all a schema change needs. Flyway applies it automatically
-- on next backend startup, against your EXISTING data, no wipe required.

CREATE TABLE user_notes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
