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
