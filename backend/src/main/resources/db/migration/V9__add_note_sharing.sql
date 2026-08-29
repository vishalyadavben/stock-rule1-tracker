-- Lets a user share one of their personal notes with another user by email, with VIEW or EDIT
-- permission — same pattern as stock_shares. shared_with_user_id starts NULL if the invited
-- email hasn't registered yet, and gets linked automatically at registration.
CREATE TABLE note_shares (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_id             BIGINT NOT NULL,
    owner_user_id       BIGINT NOT NULL,
    shared_with_email   VARCHAR(255) NOT NULL,
    shared_with_user_id BIGINT NULL,
    permission          ENUM('VIEW', 'EDIT') NOT NULL DEFAULT 'VIEW',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (note_id) REFERENCES user_notes(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (shared_with_user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_note_email (note_id, shared_with_email)
);
