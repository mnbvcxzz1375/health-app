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
