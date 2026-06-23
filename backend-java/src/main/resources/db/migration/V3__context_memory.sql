CREATE TABLE IF NOT EXISTS user_context_memory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  category VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_ucm_user (user_id),
  INDEX idx_ucm_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4