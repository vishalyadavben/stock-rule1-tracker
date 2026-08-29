-- Lets a user share their checklist/sticker-price/score analysis for a stock with another
-- user by email, at VIEW or EDIT permission. shared_with_user_id starts null (the recipient
-- may not have an account yet) and gets linked automatically the first time that email logs
-- in or registers — see AuthController.
CREATE TABLE stock_shares (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id            BIGINT NOT NULL,
    owner_user_id       BIGINT NOT NULL,
    shared_with_email   VARCHAR(255) NOT NULL,
    shared_with_user_id BIGINT NULL,
    permission          ENUM('VIEW', 'EDIT') NOT NULL DEFAULT 'VIEW',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (shared_with_user_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uq_stock_owner_email (stock_id, owner_user_id, shared_with_email)
);
