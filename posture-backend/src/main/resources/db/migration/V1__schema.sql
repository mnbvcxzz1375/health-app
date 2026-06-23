CREATE TABLE IF NOT EXISTS posture_jobs (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  exercise_type VARCHAR(32) NOT NULL,
  camera_view VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  fail_reason TEXT NULL,
  video_path VARCHAR(512) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  analysis_json LONGTEXT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_posture_jobs_user_created (user_id, created_at),
  INDEX idx_posture_jobs_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
