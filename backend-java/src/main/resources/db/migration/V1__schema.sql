CREATE TABLE auth_users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  email VARCHAR(128) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_sessions (
  token VARCHAR(128) PRIMARY KEY,
  user_id INT NOT NULL,
  created_at DATETIME NOT NULL,
  last_active DATETIME NOT NULL,
  INDEX idx_auth_sessions_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_profiles (
  id INT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  email VARCHAR(128) NOT NULL,
  avatar_url LONGTEXT NULL,
  risk_score INT NOT NULL DEFAULT 18,
  risk_level VARCHAR(16) NOT NULL DEFAULT '低风险'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_settings (
  user_id INT PRIMARY KEY,
  age INT NOT NULL,
  gender VARCHAR(16) NOT NULL,
  height INT NOT NULL,
  weight INT NOT NULL,
  focus TEXT,
  goals_json TEXT,
  daily_summary TINYINT(1) NOT NULL DEFAULT 1,
  risk_alert TINYINT(1) NOT NULL DEFAULT 1,
  rehab_reminder TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE home_summary (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  summary_date DATE NOT NULL,
  health_score INT NOT NULL,
  status_badge VARCHAR(20) NOT NULL,
  status_badge_variant VARCHAR(20) NOT NULL,
  status_summary VARCHAR(255) NOT NULL,
  steps_target INT NOT NULL,
  steps_now INT NOT NULL,
  hr_value INT NOT NULL,
  hr_badge VARCHAR(20) NOT NULL,
  hr_badge_variant VARCHAR(20) NOT NULL,
  hr_hint VARCHAR(120) NOT NULL,
  stress_value INT NOT NULL,
  stress_badge VARCHAR(20) NOT NULL,
  stress_badge_variant VARCHAR(20) NOT NULL,
  stress_hint VARCHAR(120) NOT NULL,
  hydration_ml INT NOT NULL,
  hydration_target_ml INT NOT NULL,
  hydration_badge VARCHAR(20) NOT NULL,
  hydration_badge_variant VARCHAR(20) NOT NULL,
  hydration_hint VARCHAR(120) NOT NULL,
  suggestion_1 VARCHAR(255) NOT NULL,
  suggestion_2 VARCHAR(255) NOT NULL,
  suggestion_3 VARCHAR(255) NOT NULL,
  UNIQUE KEY uniq_home_summary_user_day (user_id, summary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE monitor_records (
  id INT AUTO_INCREMENT PRIMARY KEY,
  recorded_at DATETIME NOT NULL,
  hr INT NOT NULL,
  sleep_score INT NOT NULL,
  deep_sleep_hours DECIMAL(3, 1) NOT NULL,
  awake_times INT NOT NULL,
  stress_score INT NOT NULL,
  INDEX idx_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE devices (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  label VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL DEFAULT '',
  brand VARCHAR(64) NOT NULL DEFAULT '',
  model VARCHAR(64) NOT NULL DEFAULT '',
  device_type VARCHAR(32) NOT NULL,
  connected TINYINT(1) NOT NULL DEFAULT 1,
  battery INT NOT NULL DEFAULT 100,
  last_sync_at DATETIME NULL,
  INDEX idx_device_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analyze_tasks (
  id VARCHAR(64) PRIMARY KEY,
  user_id INT NOT NULL,
  type VARCHAR(16) NOT NULL,
  file_name VARCHAR(255) DEFAULT '',
  text_content LONGTEXT,
  status VARCHAR(16) NOT NULL,
  points_json LONGTEXT,
  advice_json LONGTEXT,
  report_json LONGTEXT,
  saved TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_analyze_tasks_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rehab_exercises (
  id INT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  category VARCHAR(64) NOT NULL,
  duration VARCHAR(64) NOT NULL,
  level VARCHAR(16) NOT NULL,
  minutes INT NOT NULL,
  steps_json LONGTEXT,
  caution TEXT,
  focus VARCHAR(120),
  benefits_json LONGTEXT,
  video_minutes INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rehab_plan_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  exercise_id INT NOT NULL,
  scheduled_date DATE NOT NULL,
  done TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_rehab_plan_user_day (user_id, scheduled_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rehab_week_stats (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  stat_date DATE NOT NULL,
  minutes INT NOT NULL,
  INDEX idx_rehab_week_user_day (user_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rehab_reminders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  exercise_name VARCHAR(64) NOT NULL,
  reminder_time VARCHAR(8) NOT NULL,
  days_json LONGTEXT,
  push_enabled TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uniq_rehab_reminder (user_id, exercise_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rehab_plan_settings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  focus VARCHAR(120) NOT NULL DEFAULT '',
  frequency VARCHAR(64) NOT NULL DEFAULT '',
  duration VARCHAR(64) NOT NULL DEFAULT '',
  intensity VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uniq_rehab_plan_settings_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rehab_plan_reminders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  reminder_time VARCHAR(8) NOT NULL,
  days_json LONGTEXT,
  push_enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uniq_rehab_plan_reminder_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medications (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  name VARCHAR(120) NOT NULL,
  alias VARCHAR(120) DEFAULT '',
  dosage_value INT NOT NULL DEFAULT 1,
  dosage_unit VARCHAR(16) NOT NULL DEFAULT '片',
  usage_label VARCHAR(32) NOT NULL DEFAULT '饭后',
  notes TEXT,
  photo_url LONGTEXT,
  enable_ocr TINYINT(1) NOT NULL DEFAULT 0,
  enable_yolo TINYINT(1) NOT NULL DEFAULT 0,
  ocr_endpoint VARCHAR(255) DEFAULT '',
  yolo_endpoint VARCHAR(255) DEFAULT '',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_med_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medication_reminders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  medication_id INT NOT NULL,
  user_id INT NOT NULL,
  reminder_time VARCHAR(8) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  INDEX idx_med_reminder_user (user_id),
  INDEX idx_med_reminder_med (medication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medication_alarm_groups (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  alarm_time VARCHAR(8) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uniq_med_alarm_user_time (user_id, alarm_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medication_alarm_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  alarm_id INT NOT NULL,
  medication_id INT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_med_alarm_item (alarm_id, medication_id),
  INDEX idx_med_alarm_item_alarm (alarm_id),
  INDEX idx_med_alarm_item_med (medication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
