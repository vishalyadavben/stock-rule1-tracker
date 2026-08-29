-- Same paper-money vs real-money distinction as regular holdings: paper IPO applications can
-- be deleted freely (they were never real money); real ones require password confirmation to
-- delete, and the flag itself can never be changed after creation — same reasoning as
-- investment_lots.is_paper_money.
ALTER TABLE ipo_applications ADD COLUMN is_paper_money BOOLEAN NOT NULL DEFAULT FALSE;
