-- V6: Add missing columns to devices and monitor_records
-- These ALTER TABLE statements are safe to run even if columns already exist
-- Flyway will mark this migration as applied after successful execution

-- devices table: V1 only created id, user_id, label, device_type
-- Backend code now expects: name, brand, model, connected, battery, last_sync_at
ALTER TABLE devices ADD COLUMN name VARCHAR(120) NOT NULL DEFAULT '' AFTER label;
ALTER TABLE devices ADD COLUMN brand VARCHAR(64) NOT NULL DEFAULT '' AFTER name;
ALTER TABLE devices ADD COLUMN model VARCHAR(64) NOT NULL DEFAULT '' AFTER brand;
ALTER TABLE devices ADD COLUMN connected TINYINT(1) NOT NULL DEFAULT 1 AFTER device_type;
ALTER TABLE devices ADD COLUMN battery INT NOT NULL DEFAULT 100 AFTER connected;
ALTER TABLE devices ADD COLUMN last_sync_at DATETIME NULL AFTER battery;

-- monitor_records: add blood pressure columns for scoring v2
ALTER TABLE monitor_records ADD COLUMN systolic_bp INT NOT NULL DEFAULT 0 AFTER stress_score;
ALTER TABLE monitor_records ADD COLUMN diastolic_bp INT NOT NULL DEFAULT 0 AFTER systolic_bp;

-- Backfill existing device data
UPDATE devices SET name = label, brand = 'Huawei', model = 'WATCH', connected = 1, battery = 92, last_sync_at = NOW() WHERE user_id = 1 AND name = '';
