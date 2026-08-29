-- Lets a user choose to VIEW a holding converted into a different currency than the one it
-- actually trades in (e.g. view an AAPL position, which is natively USD, converted to INR).
-- The stock's native currency (stocks.currency) and the position's buy_price/current price
-- are NEVER altered by this — they always stay in the real, native currency. This column only
-- controls what currency the frontend converts TO for display, via a live exchange rate.
ALTER TABLE investment_lots ADD COLUMN display_currency VARCHAR(10) NULL;
