CREATE TABLE IF NOT EXISTS patient_memory_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  memory_tier VARCHAR(16) NOT NULL,
  memory_type VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  source VARCHAR(32) NOT NULL,
  safety_level VARCHAR(16) NOT NULL DEFAULT 'routine',
  confirmed_by_user BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  effective_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NULL,
  superseded_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_patient_memory_active (user_id, status, memory_tier, expires_at),
  INDEX idx_patient_memory_type (user_id, memory_type, status),
  INDEX idx_patient_memory_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
