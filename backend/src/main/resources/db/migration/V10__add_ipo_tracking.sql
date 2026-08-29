-- Tracks IPO applications. Links to the same `stocks` table as regular holdings (via stock_id)
-- so an IPO'd company automatically gets Big Five, checklist, and Sticker Price support for
-- free — no separate data model needed for that part.
-- Note: only the LAST 4 characters of PAN are stored, deliberately — PAN is sensitive
-- government ID data, and there's no need to hold the full number for what this is used for
-- (just letting you tell applications under different PANs apart).
CREATE TABLE ipo_applications (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    stock_id            BIGINT NOT NULL,
    status              ENUM('PENDING', 'ALLOTTED', 'NOT_ALLOTTED') NOT NULL DEFAULT 'PENDING',
    issue_price         DECIMAL(18,4) NOT NULL,
    quantity            DECIMAL(18,6) NULL,
    sell_price          DECIMAL(18,4) NULL,
    sell_date           DATETIME NULL,
    gmp                 DECIMAL(18,4) NULL,
    gmp_source          ENUM('API', 'MANUAL') NULL,
    pan_last4           VARCHAR(4) NULL,
    notes               TEXT,
    application_date    DATETIME NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE
);
