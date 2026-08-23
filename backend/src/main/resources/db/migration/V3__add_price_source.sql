-- Tracks whether stocks.last_price came from the API or was entered manually, so the UI can
-- show a "Live" vs "Manual" badge instead of implying every price is real-time.
ALTER TABLE stocks ADD COLUMN price_source ENUM('API', 'MANUAL') DEFAULT NULL;
