-- Paper-money lots are practice positions the user can delete; real-money lots never can.
-- Defaults to FALSE (real money) so existing lots are treated as real, not silently
-- reclassified as deletable practice trades.
ALTER TABLE investment_lots ADD COLUMN is_paper_money BOOLEAN NOT NULL DEFAULT FALSE;
