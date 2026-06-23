-- =============================================================
-- 修复脚本：将现有数据库 schema 升级到最新版本
-- 执行方式：在 MySQL 客户端中运行此文件
-- 数据库：health_monitoring
-- =============================================================

USE health_monitoring;

-- 1. devices 表：补齐缺失列
SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'name'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE devices ADD COLUMN name VARCHAR(120) NOT NULL DEFAULT ""'' AFTER label',
  'SELECT "devices.name already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'brand'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE devices ADD COLUMN brand VARCHAR(64) NOT NULL DEFAULT "" AFTER name',
  'SELECT "devices.brand already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'model'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE devices ADD COLUMN model VARCHAR(64) NOT NULL DEFAULT "" AFTER brand',
  'SELECT "devices.model already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'connected'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE devices ADD COLUMN connected TINYINT(1) NOT NULL DEFAULT 1 AFTER device_type',
  'SELECT "devices.connected already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'battery'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE devices ADD COLUMN battery INT NOT NULL DEFAULT 100 AFTER connected',
  'SELECT "devices.battery already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'last_sync_at'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE devices ADD COLUMN last_sync_at DATETIME NULL AFTER battery',
  'SELECT "devices.last_sync_at already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. monitor_records 表：补齐血压列
SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'monitor_records' AND COLUMN_NAME = 'systolic_bp'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE monitor_records ADD COLUMN systolic_bp INT NOT NULL DEFAULT 0 AFTER stress_score',
  'SELECT "monitor_records.systolic_bp already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'monitor_records' AND COLUMN_NAME = 'diastolic_bp'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE monitor_records ADD COLUMN diastolic_bp INT NOT NULL DEFAULT 0 AFTER systolic_bp',
  'SELECT "monitor_records.diastolic_bp already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. medication_alarm_groups 表
CREATE TABLE IF NOT EXISTS medication_alarm_groups (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  alarm_time VARCHAR(8) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uniq_med_alarm_user_time (user_id, alarm_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. medication_alarm_items 表
CREATE TABLE IF NOT EXISTS medication_alarm_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  alarm_id INT NOT NULL,
  medication_id INT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_med_alarm_item (alarm_id, medication_id),
  INDEX idx_med_alarm_item_alarm (alarm_id),
  INDEX idx_med_alarm_item_med (medication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. user_context_memory 表
CREATE TABLE IF NOT EXISTS user_context_memory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  category VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_ucm_user (user_id),
  INDEX idx_ucm_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. medication_intake_log 表
CREATE TABLE IF NOT EXISTS medication_intake_log (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  alarm_id INT NOT NULL,
  intake_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  confirmed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_intake_user_alarm_date (user_id, alarm_id, intake_date),
  INDEX idx_intake_user_date (user_id, intake_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. home_summary 补齐缺失索引
SET @idx_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = 'health_monitoring' AND TABLE_NAME = 'home_summary' AND INDEX_NAME = 'uniq_home_summary_user_day'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE home_summary ADD UNIQUE KEY uniq_home_summary_user_day (user_id, summary_date)',
  'SELECT "home_summary index already exists" AS status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8. 更新 devices seed 数据（补全 name/brand/model 等字段）
UPDATE devices SET
  name = IF(name = '', label, name),
  brand = IF(brand = '', 'Huawei', brand),
  model = IF(model = '', 'WATCH', model),
  connected = IF(connected = 0, 1, connected),
  battery = IF(battery = 0, 92, battery),
  last_sync_at = IF(last_sync_at IS NULL, NOW(), last_sync_at)
WHERE user_id = 1 AND name = '';

-- 完成
SELECT 'Database schema upgrade complete!' AS status;
