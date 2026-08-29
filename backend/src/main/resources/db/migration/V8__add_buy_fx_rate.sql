-- Locks in the FX rate (native currency -> the other supported currency) as of the buy date,
-- so gain/loss shown in a converted currency reflects proper accounting treatment: cost basis
-- converted at the HISTORICAL rate, current value converted at the LIVE rate. This correctly
-- includes both the stock's own price movement and currency movement since purchase, rather
-- than applying today's rate uniformly to everything (which understates true economic return).
ALTER TABLE investment_lots ADD COLUMN buy_fx_rate DECIMAL(18,6) NULL;
ALTER TABLE investment_lots ADD COLUMN buy_fx_rate_to_currency VARCHAR(10) NULL;
