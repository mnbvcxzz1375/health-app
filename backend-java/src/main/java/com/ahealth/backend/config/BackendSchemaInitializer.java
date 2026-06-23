package com.ahealth.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BackendSchemaInitializer {
  private final JdbcTemplate jdbcTemplate;

  public BackendSchemaInitializer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  public void ensureSchema() {
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS medication_alarm_groups (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          alarm_time VARCHAR(8) NOT NULL,
          enabled TINYINT(1) NOT NULL DEFAULT 1,
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL,
          UNIQUE KEY uniq_med_alarm_user_time (user_id, alarm_time)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS medication_alarm_items (
          id INT AUTO_INCREMENT PRIMARY KEY,
          alarm_id INT NOT NULL,
          medication_id INT NOT NULL,
          sort_order INT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL,
          UNIQUE KEY uniq_med_alarm_item (alarm_id, medication_id),
          INDEX idx_med_alarm_item_alarm (alarm_id),
          INDEX idx_med_alarm_item_med (medication_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS rehab_plan_reminders (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          reminder_time VARCHAR(8) NOT NULL,
          days_json LONGTEXT,
          push_enabled TINYINT(1) NOT NULL DEFAULT 1,
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL,
          UNIQUE KEY uniq_rehab_plan_reminder_user (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS user_context_memory (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          category VARCHAR(64) NOT NULL,
          content TEXT NOT NULL,
          created_at DATETIME NOT NULL,
          INDEX idx_ucm_user (user_id),
          INDEX idx_ucm_category (category)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS medication_intake_records (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          medication_id INT NOT NULL,
          scheduled_time VARCHAR(8) NOT NULL,
          actual_time DATETIME NULL,
          status VARCHAR(16) NOT NULL DEFAULT 'pending',
          note TEXT NULL,
          created_at DATETIME NOT NULL,
          INDEX idx_mir_user (user_id),
          INDEX idx_mir_med (medication_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // Apple HealthKit 扩展列
    ensureColumn("rehab_exercises", "user_id", "ALTER TABLE rehab_exercises ADD COLUMN user_id INT NULL");
    ensureColumn("analyze_tasks", "report_json", "ALTER TABLE analyze_tasks ADD COLUMN report_json LONGTEXT");
    ensureColumn("analyze_tasks", "saved", "ALTER TABLE analyze_tasks ADD COLUMN saved TINYINT(1) NOT NULL DEFAULT 0");

    // monitor_records 扩展列 — Apple HealthKit 更多维度
    ensureColumn("monitor_records", "vo2_max", "ALTER TABLE monitor_records ADD COLUMN vo2_max DECIMAL(5,1) NULL");
    ensureColumn("monitor_records", "exercise_minutes", "ALTER TABLE monitor_records ADD COLUMN exercise_minutes INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "stand_hours", "ALTER TABLE monitor_records ADD COLUMN stand_hours INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "active_energy_kcal", "ALTER TABLE monitor_records ADD COLUMN active_energy_kcal INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "flights_climbed", "ALTER TABLE monitor_records ADD COLUMN flights_climbed INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "hrv_millis", "ALTER TABLE monitor_records ADD COLUMN hrv_millis INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "mindful_minutes", "ALTER TABLE monitor_records ADD COLUMN mindful_minutes INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "walking_hr_avg", "ALTER TABLE monitor_records ADD COLUMN walking_hr_avg INT NULL DEFAULT 0");
    ensureColumn("monitor_records", "steps", "ALTER TABLE monitor_records ADD COLUMN steps INT NULL DEFAULT 0");
  }

  private void ensureColumn(String tableName, String columnName, String sql) {
    Integer count = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = ?
          AND COLUMN_NAME = ?
        """,
        Integer.class,
        tableName,
        columnName
    );
    if (count == null || count == 0) {
      jdbcTemplate.execute(sql);
    }
  }
}