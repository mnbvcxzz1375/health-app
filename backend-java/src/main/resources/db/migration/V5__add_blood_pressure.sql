-- Add blood pressure columns to monitor_records for health scoring v2
ALTER TABLE monitor_records
  ADD COLUMN IF NOT EXISTS systolic_bp INT NOT NULL DEFAULT 0 AFTER stress_score,
  ADD COLUMN IF NOT EXISTS diastolic_bp INT NOT NULL DEFAULT 0 AFTER systolic_bp;
