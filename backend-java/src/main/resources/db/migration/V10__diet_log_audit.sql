CREATE TABLE IF NOT EXISTS diet_log_audits (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  diet_log_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  action VARCHAR(16) NOT NULL,
  before_json TEXT,
  after_json TEXT,
  reason VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_diet_log_audit_log (diet_log_id, created_at),
  INDEX idx_diet_log_audit_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
