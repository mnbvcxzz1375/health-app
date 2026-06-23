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

    // DDI knowledge base
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS ddi_knowledge (
          id INT AUTO_INCREMENT PRIMARY KEY,
          drug_a VARCHAR(120) NOT NULL,
          drug_b VARCHAR(120) NOT NULL,
          severity VARCHAR(16) NOT NULL DEFAULT 'moderate',
          description TEXT NOT NULL,
          recommendation TEXT NOT NULL,
          source VARCHAR(255) DEFAULT '',
          INDEX idx_ddi_drug_a (drug_a),
          INDEX idx_ddi_drug_b (drug_b)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // Seed DDI data
    jdbcTemplate.execute(
        """
        INSERT IGNORE INTO ddi_knowledge (drug_a, drug_b, severity, description, recommendation) VALUES
        ('降压药', '钙片', 'low', '钙片可能轻微影响降压药吸收', '建议间隔 2 小时服用'),
        ('降压药', '降压药', 'high', '同类降压药重复使用可能导致低血压', '请确认是否为同一药物的不同名称'),
        ('阿莫西林', '华法林', 'high', '阿莫西林可能增强华法林的抗凝效果，增加出血风险', '需密切监测 INR 值，必要时调整华法林剂量'),
        ('阿司匹林', '华法林', 'high', '两者合用显著增加消化道出血风险', '避免合用，如必须合用需加用胃黏膜保护剂'),
        ('布洛芬', '降压药', 'moderate', 'NSAIDs 可能减弱降压药效果并增加肾脏负担', '建议使用对乙酰氨基酚替代，或密切监测血压'),
        ('他汀类', '红霉素', 'moderate', '红霉素抑制他汀代谢，增加横纹肌溶解风险', '暂停他汀或换用阿奇霉素'),
        ('二甲双胍', '碘造影剂', 'high', '合用可能导致乳酸酸中毒', '造影前 48 小时停用二甲双胍，造影后 48 小时恢复'),
        ('感冒药', '降压药', 'moderate', '部分感冒药含伪麻黄碱可升高血压', '选择不含减充血剂的感冒药，或咨询药师'),
        ('安眠药', '抗过敏药', 'moderate', '两者均有镇静作用，合用加重嗜睡', '避免同时服用，调整服药时间')
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