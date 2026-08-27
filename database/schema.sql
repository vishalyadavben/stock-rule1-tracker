-- ⚠️ SUPERSEDED — kept only for historical reference.
-- Schema changes are now managed by Flyway migrations in
-- backend/src/main/resources/db/migration/*.sql — that's the real source of truth.
-- This file is no longer run by docker-compose and should not be edited or re-run manually.

-- Rule #1 Investing Tracker — MySQL schema (original, pre-Flyway)

CREATE DATABASE IF NOT EXISTS rule1_tracker;
USE rule1_tracker;

-- ==========================================================
-- USERS
-- ==========================================================
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================================
-- STOCKS (master list of tickers, shared across all users)
-- ==========================================================
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

-- ==========================================================
-- WATCHLIST (stocks a user is tracking, invested or not)
-- ==========================================================
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

-- ==========================================================
-- INVESTMENTS / LOTS
-- Every buy is a "lot". A lot can later be (partially) sold.
-- Full history preserved even after exit — nothing is deleted.
-- ==========================================================
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

-- Every sell (full or partial exit) — this is your "history after exit"
CREATE TABLE investment_exits (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id              BIGINT NOT NULL,
    quantity_sold       DECIMAL(18,6) NOT NULL,
    sell_price          DECIMAL(18,4) NOT NULL,
    sell_date           DATETIME NOT NULL,
    realized_gain       DECIMAL(18,4),        -- computed at insert time
    realized_gain_pct   DECIMAL(9,4),
    notes               TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (lot_id) REFERENCES investment_lots(id) ON DELETE CASCADE
);

-- ==========================================================
-- BIG FIVE METRICS (fetched via API OR manually entered)
-- One row per stock per fiscal year, per source
-- ==========================================================
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

-- ==========================================================
-- CHECKLIST (the qualitative Four-Ms items from the notes)
-- Master list of checklist questions (seeded, editable)
-- ==========================================================
CREATE TABLE checklist_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    category        VARCHAR(50) NOT NULL,   -- MEANING, MOAT, MANAGEMENT, MARGIN_OF_SAFETY
    prompt          TEXT NOT NULL,
    display_order   INT DEFAULT 0
);

-- Per-user, per-stock answers to the checklist
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

-- ==========================================================
-- STICKER PRICE CALCULATIONS (snapshot each time it's calculated)
-- ==========================================================
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
    margin_of_safety_price  DECIMAL(18,4),   -- 50% of sticker price by default
    calculated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE
);

-- ==========================================================
-- OVERALL SCORE (1-10 rating combining checklist + Big Five pass/fail)
-- ==========================================================
CREATE TABLE business_scores (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    stock_id        BIGINT NOT NULL,
    score           DECIMAL(3,1) NOT NULL,   -- 1.0 - 10.0
    breakdown_json  JSON,                    -- stores component scores for transparency
    calculated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE
);

-- ==========================================================
-- USER NOTES (freeform personal notes, not tied to any stock)
-- ==========================================================
CREATE TABLE user_notes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ==========================================================
-- SEED: default checklist items pulled straight from your notes
-- ==========================================================
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
