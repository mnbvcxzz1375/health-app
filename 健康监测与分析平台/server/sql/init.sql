CREATE DATABASE IF NOT EXISTS health_monitoring DEFAULT CHARACTER SET utf8mb4;
USE health_monitoring;

-- =============================================================
-- init.sql  —  Mirror of Flyway migrations (single source of truth)
-- Tables + seed data aligned with:
--   V1__schema.sql, V2__seed.sql, V3__context_memory.sql
-- =============================================================

DROP TABLE IF EXISTS medication_alarm_items;
DROP TABLE IF EXISTS medication_alarm_groups;
DROP TABLE IF EXISTS medication_reminders;
DROP TABLE IF EXISTS medications;
DROP TABLE IF EXISTS user_context_memory;
DROP TABLE IF EXISTS rehab_plan_reminders;
DROP TABLE IF EXISTS rehab_plan_settings;
DROP TABLE IF EXISTS rehab_reminders;
DROP TABLE IF EXISTS rehab_week_stats;
DROP TABLE IF EXISTS rehab_plan_items;
DROP TABLE IF EXISTS rehab_exercises;
DROP TABLE IF EXISTS analyze_tasks;
DROP TABLE IF EXISTS devices;
DROP TABLE IF EXISTS home_summary;
DROP TABLE IF EXISTS monitor_records;
DROP TABLE IF EXISTS user_settings;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS auth_sessions;
DROP TABLE IF EXISTS auth_users;

-- ── V1 tables ──────────────────────────────────────────────────

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

-- ── V4 table: medication intake tracking ────────────────────────

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

-- ── V3 table ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_context_memory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  category VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_ucm_user (user_id),
  INDEX idx_ucm_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── V2 seed data (INSERT IGNORE to match Flyway) ──────────────

INSERT IGNORE INTO auth_users (id, name, email, password_hash, created_at)
VALUES (1, '演示用户', 'liming@example.com', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', NOW());

INSERT IGNORE INTO user_profiles (id, name, email, avatar_url, risk_score, risk_level)
VALUES (1, '李明', 'liming@example.com', NULL, 18, '低风险');

INSERT IGNORE INTO user_settings (user_id, age, gender, height, weight, focus, goals_json, daily_summary, risk_alert, rehab_reminder)
VALUES (1, 28, 'male', 172, 65, '改善久坐带来的腰背不适', '["姿势改善","恢复放松"]', 1, 1, 1);

INSERT IGNORE INTO home_summary (
  user_id, summary_date, health_score, status_badge, status_badge_variant, status_summary,
  steps_target, steps_now, hr_value, hr_badge, hr_badge_variant, hr_hint,
  stress_value, stress_badge, stress_badge_variant, stress_hint,
  hydration_ml, hydration_target_ml, hydration_badge, hydration_badge_variant, hydration_hint,
  suggestion_1, suggestion_2, suggestion_3
) VALUES (
  1, CURDATE(), 78, '需关注', 'warning', '建议今天优先补水，并保持中低强度训练。',
  8000, 4520, 72, '正常', 'success', '静息心率较昨日偏高，建议关注恢复状态。',
  63, '偏高', 'warning', '建议安排 5 分钟放松休息。',
  1100, 1700, '不足', 'danger', '近 24 小时饮水量仍未达标。',
  '今天分 3 次小口补水更合适。', '今天可选择 20 到 30 分钟低强度有氧。', '今晚睡前 30 分钟尽量避免屏幕刺激。'
);

INSERT IGNORE INTO monitor_records (recorded_at, hr, sleep_score, deep_sleep_hours, awake_times, stress_score) VALUES
  (NOW() - INTERVAL 6 HOUR, 69, 80, 1.7, 2, 56),
  (NOW() - INTERVAL 5 HOUR, 71, 81, 1.8, 2, 58),
  (NOW() - INTERVAL 4 HOUR, 73, 82, 1.9, 2, 60),
  (NOW() - INTERVAL 3 HOUR, 72, 83, 2.0, 1, 61),
  (NOW() - INTERVAL 2 HOUR, 74, 84, 2.0, 1, 59),
  (NOW() - INTERVAL 1 HOUR, 73, 83, 1.9, 1, 57),
  (NOW(), 72, 82, 1.9, 1, 55),
  (DATE_SUB(CURDATE(), INTERVAL 6 DAY) + INTERVAL 8 HOUR, 69, 78, 1.6, 3, 60),
  (DATE_SUB(CURDATE(), INTERVAL 5 DAY) + INTERVAL 8 HOUR, 71, 80, 1.7, 2, 58),
  (DATE_SUB(CURDATE(), INTERVAL 4 DAY) + INTERVAL 8 HOUR, 73, 79, 1.7, 2, 62),
  (DATE_SUB(CURDATE(), INTERVAL 3 DAY) + INTERVAL 8 HOUR, 75, 81, 1.8, 2, 65),
  (DATE_SUB(CURDATE(), INTERVAL 2 DAY) + INTERVAL 8 HOUR, 72, 83, 1.9, 1, 61),
  (DATE_SUB(CURDATE(), INTERVAL 1 DAY) + INTERVAL 8 HOUR, 70, 82, 1.8, 1, 57),
  (CURDATE() + INTERVAL 8 HOUR, 74, 84, 2.0, 1, 59),
  (DATE_SUB(CURDATE(), INTERVAL 5 MONTH) + INTERVAL 8 HOUR, 72, 79, 1.7, 2, 62),
  (DATE_SUB(CURDATE(), INTERVAL 4 MONTH) + INTERVAL 8 HOUR, 73, 80, 1.8, 2, 60),
  (DATE_SUB(CURDATE(), INTERVAL 3 MONTH) + INTERVAL 8 HOUR, 71, 81, 1.8, 2, 58),
  (DATE_SUB(CURDATE(), INTERVAL 2 MONTH) + INTERVAL 8 HOUR, 74, 82, 1.9, 1, 57),
  (DATE_SUB(CURDATE(), INTERVAL 1 MONTH) + INTERVAL 8 HOUR, 75, 83, 2.0, 1, 55),
  (CURDATE() + INTERVAL 8 HOUR, 73, 84, 2.0, 1, 56);

INSERT IGNORE INTO devices (user_id, label, name, brand, model, device_type, connected, battery, last_sync_at)
VALUES (1, 'Huawei WATCH', 'Huawei WATCH', 'Huawei', 'WATCH', 'watch', 1, 92, NOW());

INSERT IGNORE INTO rehab_exercises (id, name, category, duration, level, minutes, steps_json, caution, focus, benefits_json, video_minutes) VALUES
  (1, '鸟狗式', '核心稳定', '3 组 × 12 次', '基础', 8, '["保持脊柱中立位","对侧手脚伸直","动作缓慢并控制回位"]', '如果下背部出现明显刺痛请立即停止。', '核心稳定与抗旋转控制', '["提升躯干稳定性","改善动作控制","降低代偿风险"]', 6),
  (2, '死虫式', '核心稳定', '3 组 × 10 次', '基础', 8, '["腰背贴地，保持腹压","对侧手脚缓慢伸展","伸展时呼气，回位时吸气"]', '动作过程中避免憋气。', '核心抗伸展控制', '["增强腹部控制","改善骨盆稳定","减轻下背部负担"]', 5),
  (3, '髂腰肌拉伸', '灵活性', '每侧 2 组 × 30 秒', '基础', 6, '["采用跪姿弓步位","骨盆轻微后倾","左右两侧均匀拉伸"]', '如有需要可在膝下垫软垫。', '髋屈肌放松与骨盆位置调整', '["缓解久坐僵硬","提升髋部活动度","辅助腰背舒适"]', 4),
  (4, '弹力带划船', '上背激活', '3 组 × 12 次', '进阶', 10, '["先完成肩胛后收与下压","肘部贴近身体向后拉","全程保持胸椎稳定"]', '如果肩部不适，请降低阻力或暂停。', '肩胛稳定与上背激活', '["改善含胸圆肩","增强上背耐力","提升姿势支撑"]', 7);

INSERT IGNORE INTO rehab_plan_items (user_id, exercise_id, scheduled_date, done) VALUES
  (1, 1, CURDATE(), 0),
  (1, 2, CURDATE(), 0),
  (1, 3, CURDATE(), 0),
  (1, 4, CURDATE(), 0);

INSERT IGNORE INTO rehab_week_stats (user_id, stat_date, minutes) VALUES
  (1, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 18),
  (1, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 22),
  (1, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 15),
  (1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 28),
  (1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 20),
  (1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 26),
  (1, CURDATE(), 30);

INSERT IGNORE INTO rehab_reminders (user_id, exercise_name, reminder_time, days_json, push_enabled)
VALUES (1, '鸟狗式', '08:00', '["mon","wed","fri"]', 1);

INSERT IGNORE INTO rehab_plan_settings (user_id, focus, frequency, duration, intensity, created_at, updated_at)
VALUES (1, '核心稳定与姿势改善', '每周 4 次', '单次 25 分钟', '低到中等强度', NOW(), NOW());

INSERT IGNORE INTO rehab_plan_reminders (user_id, reminder_time, days_json, push_enabled, created_at, updated_at)
VALUES (1, '08:00', '["mon","wed","fri"]', 1, NOW(), NOW());

INSERT IGNORE INTO medications (user_id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url, enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled, created_at, updated_at)
VALUES
  (1, '降压药', '小白片', 1, '片', '饭后', '避免与牛奶同服', '', 0, 0, 'http://localhost:8000/ocr', 'http://localhost:8000/yolo', 1, NOW(), NOW()),
  (1, '钙片', '补充剂', 2, '片', '随餐', '与咖啡间隔 1 小时', '', 0, 0, 'http://localhost:8000/ocr', 'http://localhost:8000/yolo', 1, NOW(), NOW());

INSERT IGNORE INTO medication_reminders (medication_id, user_id, reminder_time, enabled, created_at)
VALUES
  (1, 1, '08:00', 1, NOW()),
  (1, 1, '20:00', 1, NOW()),
  (2, 1, '12:00', 1, NOW());

INSERT IGNORE INTO medication_alarm_groups (id, user_id, alarm_time, enabled, created_at, updated_at)
VALUES
  (1, 1, '08:00', 1, NOW(), NOW()),
  (2, 1, '12:00', 1, NOW(), NOW()),
  (3, 1, '20:00', 1, NOW(), NOW());

INSERT IGNORE INTO medication_alarm_items (alarm_id, medication_id, sort_order, created_at)
VALUES
  (1, 1, 0, NOW()),
  (2, 2, 0, NOW()),
  (3, 1, 0, NOW());
