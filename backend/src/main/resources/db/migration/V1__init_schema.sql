-- Flyway migration V1: original schema.
-- Flyway runs this against whatever database the JDBC URL already points to (created via
-- MYSQL_DATABASE in docker-compose, or manually on a hosted DB) — no CREATE DATABASE/USE
-- needed here, unlike the old schema.sql.

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stocks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker          VARCHAR(20) NOT NULL UNIQUE,
    company_name    VARCHAR(255),
    sector          VARCHAR(100),
    industry        VARCHAR(100),
    currency        VARCHAR(10) DEFAULT 'USD',
    last_price      DECIMAL(18,4),
    last_price_at   TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE watchlist_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    stock_id        BIGINT NOT NULL,
    added_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes           TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_stock (user_id, stock_id)
);

CREATE TABLE investment_lots (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    stock_id            BIGINT NOT NULL,
    quantity            DECIMAL(18,6) NOT NULL,
    buy_price           DECIMAL(18,4) NOT NULL,
    buy_date             DATETIME NOT NULL,
    status              ENUM('OPEN','CLOSED','PARTIAL') NOT NULL DEFAULT 'OPEN',
    remaining_quantity  DECIMAL(18,6) NOT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE
);

CREATE TABLE investment_exits (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id              BIGINT NOT NULL,
    quantity_sold       DECIMAL(18,6) NOT NULL,
    sell_price          DECIMAL(18,4) NOT NULL,
    sell_date           DATETIME NOT NULL,
    realized_gain       DECIMAL(18,4),
    realized_gain_pct   DECIMAL(9,4),
    notes               TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (lot_id) REFERENCES investment_lots(id) ON DELETE CASCADE
);

CREATE TABLE big_five_metrics (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id        BIGINT NOT NULL,
    fiscal_year     INT NOT NULL,
    source          ENUM('API','MANUAL') NOT NULL DEFAULT 'API',
    roic_pct        DECIMAL(9,4),
    sales           DECIMAL(20,2),
    eps             DECIMAL(12,4),
    equity          DECIMAL(20,2),
    free_cash_flow  DECIMAL(20,2),
    long_term_debt  DECIMAL(20,2),
    shares_out      DECIMAL(20,2),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    UNIQUE KEY uq_stock_year_source (stock_id, fiscal_year, source)
);

CREATE TABLE checklist_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    category        VARCHAR(50) NOT NULL,
    prompt          TEXT NOT NULL,
    display_order   INT DEFAULT 0
);

CREATE TABLE checklist_responses (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    stock_id            BIGINT NOT NULL,
    checklist_item_id   BIGINT NOT NULL,
    is_checked          BOOLEAN DEFAULT FALSE,
    free_text           TEXT,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    FOREIGN KEY (checklist_item_id) REFERENCES checklist_items(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_stock_item (user_id, stock_id, checklist_item_id)
);

CREATE TABLE sticker_price_calcs (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT NOT NULL,
    stock_id                BIGINT NOT NULL,
    current_eps             DECIMAL(12,4) NOT NULL,
    estimated_growth_pct    DECIMAL(9,4) NOT NULL,
    estimated_future_pe     DECIMAL(9,4) NOT NULL,
    min_acceptable_return   DECIMAL(9,4) NOT NULL,
    future_eps_10y          DECIMAL(12,4),
    future_price            DECIMAL(18,4),
    sticker_price            DECIMAL(18,4),
    margin_of_safety_price  DECIMAL(18,4),
    calculated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE
);

CREATE TABLE business_scores (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    stock_id        BIGINT NOT NULL,
    score           DECIMAL(3,1) NOT NULL,
    breakdown_json  JSON,
    calculated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE
);

INSERT INTO checklist_items (category, prompt, display_order) VALUES
('MEANING', 'Would I be willing to make this business the sole financial support of my family for 100 years?', 1),
('MEANING', 'Do I want to own the whole business (not just trade the stock)?', 2),
('MEANING', 'Do I understand what this company does and how it makes money?', 3),
('MEANING', 'Does it show up in my Passion / Talent / Money three-circle overlap?', 4),
('MOAT', 'Can this business defend itself against competitors long-term?', 5),
('MOAT', 'Can I reasonably predict this business 10 years out?', 6),
('MANAGEMENT', 'Do management act like long-term owners, not hired hands?', 7),
('MANAGEMENT', 'Is management honest and rational with capital allocation (buybacks only when cheap, sensible debt use)?', 8),
('MARGIN_OF_SAFETY', 'Do I know the intrinsic value (Sticker Price) of this business?', 9),
('MARGIN_OF_SAFETY', 'Can I buy it at or below the Margin-of-Safety price (50% of Sticker Price)?', 10),
('DEBT', 'Can long-term debt be paid off within 3 years of free cash flow?', 11);
