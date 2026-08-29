-- Lets users override the Sticker Price holding period (default 10 years, per the 10-10 Rule)
-- instead of it always being hardcoded. Saved per calculation so history stays accurate to
-- whatever period was actually used at the time.
ALTER TABLE sticker_price_calcs ADD COLUMN years_to_hold INT NOT NULL DEFAULT 10;
