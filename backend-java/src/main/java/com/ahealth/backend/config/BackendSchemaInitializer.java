package com.ahealth.backend.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
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

    // consult_history for AI assistant persistent history
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS consult_history (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          request_id VARCHAR(64) NOT NULL,
          scene VARCHAR(32) NOT NULL DEFAULT 'assistant',
          question TEXT NOT NULL,
          answer TEXT NOT NULL,
          suggestions_json TEXT,
          disclaimer VARCHAR(255) DEFAULT '',
          knowledge_sources_json TEXT,
          model_used VARCHAR(64) DEFAULT '',
          created_at DATETIME NOT NULL,
          INDEX idx_consult_user (user_id),
          INDEX idx_consult_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // Apple HealthKit 扩展列
    ensureColumn("rehab_exercises", "user_id", "ALTER TABLE rehab_exercises ADD COLUMN user_id INT NULL");
    ensureColumn("analyze_tasks", "report_json", "ALTER TABLE analyze_tasks ADD COLUMN report_json LONGTEXT");
    ensureColumn("analyze_tasks", "saved", "ALTER TABLE analyze_tasks ADD COLUMN saved TINYINT(1) NOT NULL DEFAULT 0");
    ensureColumn("consult_history", "evidence_json", "ALTER TABLE consult_history ADD COLUMN evidence_json TEXT NULL");
    ensureColumn("consult_history", "safety_json", "ALTER TABLE consult_history ADD COLUMN safety_json TEXT NULL");

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

    // ===== 健康知识图谱新增表（药物 + 饮食 + 康复扩展） =====

    // 中药材库
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS tcm_herbs (
          id INT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(64) NOT NULL,
          pinyin VARCHAR(128),
          alias VARCHAR(256),
          nature VARCHAR(32),
          flavor VARCHAR(64),
          meridian VARCHAR(128),
          efficacy TEXT,
          contraindication TEXT,
          source VARCHAR(64),
          external_id VARCHAR(128),
          UNIQUE KEY uk_tcm_herb_name (name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 中药方剂
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS tcm_formulas (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          name VARCHAR(120) NOT NULL,
          diagnosis TEXT,
          prescribed_at DATE,
          notes TEXT,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_tcm_formula_user (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 方剂-药材多对多
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS formula_herbs (
          id INT AUTO_INCREMENT PRIMARY KEY,
          formula_id INT NOT NULL,
          herb_id INT,
          herb_name VARCHAR(64) NOT NULL,
          grams DECIMAL(8,2),
          role VARCHAR(32),
          UNIQUE KEY uk_formula_herb (formula_id, herb_name),
          INDEX idx_formula_herb_formula (formula_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 药品临床信息
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS drug_clinical_info (
          id INT AUTO_INCREMENT PRIMARY KEY,
          drug_name VARCHAR(120) NOT NULL,
          medicine_type VARCHAR(16) NOT NULL,
          ingredients TEXT,
          indications TEXT,
          side_effects TEXT,
          allergic_reactions TEXT,
          contraindicated_groups TEXT,
          contraindications TEXT,
          interactions TEXT,
          dietary_taboos TEXT,
          dosing_interval_minutes INT,
          source VARCHAR(255),
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          UNIQUE KEY uk_drug (drug_name, medicine_type)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 十八反十九畏
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS tcm_incompatibility (
          id INT AUTO_INCREMENT PRIMARY KEY,
          herb_a VARCHAR(64) NOT NULL,
          herb_b VARCHAR(64) NOT NULL,
          type VARCHAR(16) NOT NULL,
          description TEXT,
          source VARCHAR(128),
          INDEX idx_tcm_incompat_a (herb_a),
          INDEX idx_tcm_incompat_b (herb_b)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 中西药交互
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS tcm_wm_interaction (
          id INT AUTO_INCREMENT PRIMARY KEY,
          tcm_name VARCHAR(120) NOT NULL,
          wm_name VARCHAR(120) NOT NULL,
          severity VARCHAR(16) NOT NULL,
          interaction_type VARCHAR(64),
          recommended_interval_minutes INT,
          description TEXT,
          evidence_source VARCHAR(255),
          INDEX idx_tcm_wm_tcm (tcm_name),
          INDEX idx_tcm_wm_wm (wm_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 药物-食物交互
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS drug_food_interaction (
          id INT AUTO_INCREMENT PRIMARY KEY,
          drug_name VARCHAR(120) NOT NULL,
          food_category VARCHAR(64) NOT NULL,
          food_items TEXT,
          severity VARCHAR(16),
          description TEXT,
          source VARCHAR(255),
          INDEX idx_dfi_drug (drug_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 用户过敏史
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS user_allergies (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          allergen VARCHAR(120) NOT NULL,
          allergen_type VARCHAR(16),
          reaction TEXT,
          severity VARCHAR(16),
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_user_allergen (user_id, allergen),
          INDEX idx_allergy_user (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 食物营养成分表
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS food_items (
          id INT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(120) NOT NULL,
          category VARCHAR(64),
          calories_per_100g DECIMAL(8,2),
          protein_g DECIMAL(8,2),
          fat_g DECIMAL(8,2),
          carb_g DECIMAL(8,2),
          fiber_g DECIMAL(8,2),
          sodium_mg DECIMAL(8,2),
          potassium_mg DECIMAL(8,2),
          glycemic_index INT,
          tags VARCHAR(256),
          source VARCHAR(128),
          UNIQUE KEY uk_food_name (name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 饮食计划
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS diet_plans (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          plan_date DATE NOT NULL,
          goal_type VARCHAR(32),
          target_calories INT,
          target_protein_g INT,
          target_fat_g INT,
          target_carb_g INT,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_user_date (user_id, plan_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 餐次
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS diet_meals (
          id INT AUTO_INCREMENT PRIMARY KEY,
          diet_plan_id INT NOT NULL,
          meal_type VARCHAR(16) NOT NULL,
          food_item_id INT,
          food_name VARCHAR(120),
          quantity_g DECIMAL(8,2),
          calories DECIMAL(8,2),
          notes TEXT,
          INDEX idx_diet_meal_plan (diet_plan_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 用户饮食偏好
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS user_diet_preferences (
          user_id INT PRIMARY KEY,
          diet_style VARCHAR(32),
          disliked_foods TEXT,
          preferred_cuisine VARCHAR(64),
          daily_meal_count INT DEFAULT 3,
          avoid_spicy TINYINT(1) NOT NULL DEFAULT 0,
          avoid_cold TINYINT(1) NOT NULL DEFAULT 0,
          vegetarian TINYINT(1) NOT NULL DEFAULT 0,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 用户饮食日志（拍照识别/手动记录均落到当前用户）
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS diet_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_id BIGINT NOT NULL,
          food_name VARCHAR(120) NOT NULL,
          category VARCHAR(64),
          weight_grams DECIMAL(8,2) NOT NULL DEFAULT 0,
          calories DECIMAL(8,2) NOT NULL DEFAULT 0,
          protein_g DECIMAL(8,2) NOT NULL DEFAULT 0,
          carbs_g DECIMAL(8,2) NOT NULL DEFAULT 0,
          fat_g DECIMAL(8,2) NOT NULL DEFAULT 0,
          source VARCHAR(64),
          recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_diet_logs_user_time (user_id, recorded_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    jdbcTemplate.execute(
        """
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // ===== 扩展现有表 =====

    // medications 扩展（区分中西药、关联方剂和临床信息）
    ensureColumn("medications", "medicine_type", "ALTER TABLE medications ADD COLUMN medicine_type VARCHAR(16) DEFAULT 'western'");
    ensureColumn("medications", "formula_id", "ALTER TABLE medications ADD COLUMN formula_id INT NULL");
    ensureColumn("medications", "clinical_info_id", "ALTER TABLE medications ADD COLUMN clinical_info_id INT NULL");

    // ddi_knowledge 扩展（支持中药交互类型和间隔时间）
    ensureColumn("ddi_knowledge", "interaction_type", "ALTER TABLE ddi_knowledge ADD COLUMN interaction_type VARCHAR(32) DEFAULT 'wm_wm'");
    ensureColumn("ddi_knowledge", "recommended_interval_minutes", "ALTER TABLE ddi_knowledge ADD COLUMN recommended_interval_minutes INT NULL");

    // rehab_exercises 扩展（智能康复计划依赖）
    ensureColumn("rehab_exercises", "goal_type", "ALTER TABLE rehab_exercises ADD COLUMN goal_type VARCHAR(32) NULL");
    ensureColumn("rehab_exercises", "muscle_group", "ALTER TABLE rehab_exercises ADD COLUMN muscle_group VARCHAR(64) NULL");
    ensureColumn("rehab_exercises", "equipment", "ALTER TABLE rehab_exercises ADD COLUMN equipment VARCHAR(64) NULL");
    ensureColumn("rehab_exercises", "calories_burn_per_min", "ALTER TABLE rehab_exercises ADD COLUMN calories_burn_per_min INT NULL");
    ensureColumn("rehab_exercises", "bmi_range", "ALTER TABLE rehab_exercises ADD COLUMN bmi_range VARCHAR(32) NULL");

    // ===== 数据来源元信息表 =====
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS data_sources (
          id INT AUTO_INCREMENT PRIMARY KEY,
          source_name VARCHAR(64) NOT NULL,
          source_type VARCHAR(16) NOT NULL,
          target_table VARCHAR(64) NOT NULL,
          record_count INT NOT NULL DEFAULT 0,
          license VARCHAR(128),
          reference_url VARCHAR(255),
          citation TEXT,
          last_updated DATETIME,
          UNIQUE KEY uk_data_source (source_name, target_table)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // ===== 加载知识图谱种子数据 =====
    loadSeedSql("/db/seed/seed_knowledge.sql");
    loadSeedSql("/db/seed/seed_rehab_exercises.sql");
    loadSeedSql("/db/seed/seed_food_nutrition.sql");
    loadSeedSql("/db/seed/seed_data_sources.sql");

    // ===== LLM 智能化升级：4 张新表 =====

    // Prompt 模板表（版本管理 + 热更新）
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS prompt_templates (
          id INT AUTO_INCREMENT PRIMARY KEY,
          template_key VARCHAR(64) NOT NULL,
          scene VARCHAR(32) NOT NULL,
          content TEXT NOT NULL,
          variables_json TEXT,
          version INT NOT NULL DEFAULT 1,
          is_active TINYINT NOT NULL DEFAULT 1,
          description VARCHAR(255),
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_prompt_active (template_key, version),
          INDEX idx_prompt_key_active (template_key, is_active)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // LLM 响应缓存（精确 + 语义）
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS llm_response_cache (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          cache_key VARCHAR(128) NOT NULL,
          scene VARCHAR(32) NOT NULL,
          prompt_hash CHAR(64) NOT NULL,
          prompt_text TEXT NOT NULL,
          response_text TEXT NOT NULL,
          embedding_json TEXT,
          hit_count INT NOT NULL DEFAULT 0,
          expires_at DATETIME,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_cache_key (cache_key),
          INDEX idx_scene_expires (scene, expires_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // RAG 文档元信息（向量存 Redis，元信息存 MySQL）
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS rag_documents (
          id INT AUTO_INCREMENT PRIMARY KEY,
          doc_type VARCHAR(32) NOT NULL,
          source_table VARCHAR(64),
          source_id INT,
          title VARCHAR(255),
          chunk_text TEXT NOT NULL,
          chunk_index INT NOT NULL DEFAULT 0,
          token_count INT,
          redis_doc_id VARCHAR(128),
          metadata_json TEXT,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_doc_type_source (doc_type, source_table, source_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // Agent 工具调用审计日志
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS agent_tool_calls (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          session_id VARCHAR(64) NOT NULL,
          user_id INT,
          scene VARCHAR(32) NOT NULL,
          tool_name VARCHAR(64) NOT NULL,
          tool_input TEXT,
          tool_output TEXT,
          iteration INT NOT NULL,
          duration_ms INT,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_session (session_id),
          INDEX idx_scene_created (scene, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 加载 Prompt 模板种子数据
    loadSeedSql("/db/seed/seed_prompt_templates.sql");

    // ===== 答案模板层（高频问题秒级响应，避免不必要的 LLM 调用）=====
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS answer_templates (
          id INT AUTO_INCREMENT PRIMARY KEY,
          template_key VARCHAR(64) NOT NULL,
          scene VARCHAR(32) NOT NULL DEFAULT 'consult',
          category VARCHAR(64),
          keywords_json TEXT NOT NULL,
          pattern VARCHAR(255),
          template_text TEXT NOT NULL,
          variables_json TEXT,
          priority INT NOT NULL DEFAULT 0,
          is_active TINYINT(1) NOT NULL DEFAULT 1,
          hit_count INT NOT NULL DEFAULT 0,
          version INT NOT NULL DEFAULT 1,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          UNIQUE KEY uk_answer_key_version (template_key, version),
          INDEX idx_answer_scene (scene),
          INDEX idx_answer_active (is_active)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 修复 rag_documents 缺 UNIQUE KEY 的 Bug
    // （RagRedisRepository.upsert 用 INSERT ... ON DUPLICATE KEY UPDATE，需唯一约束）
    try {
      jdbcTemplate.execute("ALTER TABLE rag_documents ADD UNIQUE KEY uk_redis_doc_id (redis_doc_id)");
    } catch (Exception ignored) {
      // 已存在则忽略
    }

    // ===== 骨龄评估任务表 =====
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS bone_age_tasks (
          id VARCHAR(64) PRIMARY KEY,
          user_id INT NOT NULL,
          image_name VARCHAR(255) DEFAULT '',
          estimated_age FLOAT,
          confidence FLOAT,
          growth_plate_stage VARCHAR(64) DEFAULT '',
          indicators_json TEXT,
          source VARCHAR(32) DEFAULT 'local_model',
          created_at DATETIME NOT NULL,
          INDEX idx_bone_age_user (user_id),
          INDEX idx_bone_age_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // 加载答案模板种子数据
    loadSeedSql("/db/seed/seed_answer_templates.sql");

    // ===== 设备聚合平台：4 张新表 + monitor_records 扩展列 + 路由种子数据 =====

    // device_bindings：用户 × Provider 绑定 + OAuth token（AES-256 加密存储）
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS device_bindings (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          provider VARCHAR(32) NOT NULL,
          external_user_id VARCHAR(128) DEFAULT '',
          display_name VARCHAR(128) NOT NULL DEFAULT '',
          device_type VARCHAR(32) NOT NULL DEFAULT 'other',
          status VARCHAR(16) NOT NULL DEFAULT 'connected',
          access_token_enc VARBINARY(2048),
          refresh_token_enc VARBINARY(2048),
          token_expires_at DATETIME NULL,
          last_sync_at DATETIME NULL,
          last_sync_status VARCHAR(16) DEFAULT '',
          last_error TEXT,
          metadata_json TEXT,
          created_at DATETIME NOT NULL,
          updated_at DATETIME NOT NULL,
          UNIQUE KEY uniq_device_binding (user_id, provider),
          INDEX idx_device_binding_user (user_id),
          INDEX idx_device_binding_status (status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // device_sync_logs：同步日志
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS device_sync_logs (
          id INT AUTO_INCREMENT PRIMARY KEY,
          binding_id INT NOT NULL,
          user_id INT NOT NULL,
          sync_started_at DATETIME NOT NULL,
          sync_ended_at DATETIME NULL,
          status VARCHAR(16) NOT NULL,
          records_pulled INT DEFAULT 0,
          records_written INT DEFAULT 0,
          error_message TEXT,
          details_json TEXT,
          INDEX idx_device_sync_log_binding (binding_id),
          INDEX idx_device_sync_log_user (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // device_metric_routes：metric → device_type 路由配置
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS device_metric_routes (
          id INT AUTO_INCREMENT PRIMARY KEY,
          metric VARCHAR(32) NOT NULL,
          metric_label VARCHAR(64) NOT NULL,
          preferred_device_type VARCHAR(32) NOT NULL,
          fallback_device_type VARCHAR(32) DEFAULT 'manual',
          pillar VARCHAR(16) NOT NULL DEFAULT 'body',
          icon VARCHAR(64) DEFAULT '',
          sort_order INT DEFAULT 0,
          UNIQUE KEY uniq_device_metric_route (metric)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // device_sdk_keys：第三方 SDK API Key 管理
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS device_sdk_keys (
          id INT AUTO_INCREMENT PRIMARY KEY,
          api_key VARCHAR(128) NOT NULL UNIQUE,
          api_key_hash VARCHAR(128) NOT NULL,
          partner_name VARCHAR(128) NOT NULL,
          partner_contact VARCHAR(255) DEFAULT '',
          bound_user_id INT NULL,
          scopes VARCHAR(255) DEFAULT 'push',
          rate_limit_per_min INT DEFAULT 60,
          enabled TINYINT(1) NOT NULL DEFAULT 1,
          created_at DATETIME NOT NULL,
          last_used_at DATETIME NULL,
          INDEX idx_device_sdk_key_hash (api_key_hash)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );

    // monitor_records 扩展列：user_id + 8 个新维度（全部 nullable，不影响现有查询）
    // user_id 用于设备聚合平台按用户隔离数据；旧记录默认 0（兼容现有 MonitorService 全局查询）
    ensureColumn("monitor_records", "user_id", "ALTER TABLE monitor_records ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0");
    ensureColumn("monitor_records", "blood_glucose", "ALTER TABLE monitor_records ADD COLUMN blood_glucose DECIMAL(4,1) NULL");
    ensureColumn("monitor_records", "body_temperature", "ALTER TABLE monitor_records ADD COLUMN body_temperature DECIMAL(4,1) NULL");
    ensureColumn("monitor_records", "spo2", "ALTER TABLE monitor_records ADD COLUMN spo2 INT NULL");
    ensureColumn("monitor_records", "respiratory_rate", "ALTER TABLE monitor_records ADD COLUMN respiratory_rate INT NULL");
    ensureColumn("monitor_records", "weight_kg", "ALTER TABLE monitor_records ADD COLUMN weight_kg DECIMAL(5,1) NULL");
    ensureColumn("monitor_records", "height_cm", "ALTER TABLE monitor_records ADD COLUMN height_cm DECIMAL(5,1) NULL");
    ensureColumn("monitor_records", "bmi", "ALTER TABLE monitor_records ADD COLUMN bmi DECIMAL(4,1) NULL");
    ensureColumn("monitor_records", "sleep_rem_hours", "ALTER TABLE monitor_records ADD COLUMN sleep_rem_hours DECIMAL(4,2) NULL");

    // device_metric_routes 种子数据：18 条 metric → device_type 映射
    jdbcTemplate.execute(
        """
        INSERT IGNORE INTO device_metric_routes (metric, metric_label, preferred_device_type, fallback_device_type, pillar, icon, sort_order) VALUES
        ('weight', '体重', 'scale', 'manual', 'body', 'solar:scale-outline', 10),
        ('bmi', 'BMI', 'scale', 'manual', 'body', 'solar:chart-square-outline', 11),
        ('heart_rate', '心率', 'watch', 'manual', 'physical', 'solar:heart-pulse-outline', 20),
        ('hrv', '心率变异性', 'watch', 'manual', 'physical', 'solar:heart-broken-outline', 21),
        ('steps', '步数', 'watch', 'manual', 'physical', 'solar:walking-outline', 22),
        ('calories', '活动能量', 'watch', 'manual', 'physical', 'solar:fire-outline', 23),
        ('blood_pressure', '血压', 'bp_monitor', 'manual', 'body', 'solar:heart-rate-monitor-outline', 30),
        ('blood_glucose', '血糖', 'cgm', 'manual', 'body', 'solar:water-drop-outline', 31),
        ('sleep_duration', '睡眠时长', 'sleep_monitor', 'manual', 'sleep', 'solar:moon-stars-outline', 40),
        ('sleep_stage', '睡眠分期', 'sleep_monitor', 'manual', 'sleep', 'solar:bed-outline', 41),
        ('spo2', '血氧', 'pulse_ox', 'manual', 'body', 'solar:medical-kit-outline', 50),
        ('respiratory_rate', '呼吸频率', 'pulse_ox', 'manual', 'body', 'solar:air-outline', 51),
        ('body_temperature', '体温', 'thermometer', 'manual', 'body', 'solar:thermometer-outline', 60),
        ('exercise_minutes', '锻炼分钟', 'watch', 'manual', 'physical', 'solar:running-outline', 24),
        ('stand_hours', '站立小时', 'watch', 'manual', 'physical', 'solar:body-outline', 25),
        ('vo2_max', '最大摄氧量', 'watch', 'manual', 'physical', 'solar:airbuds-case-outline', 26),
        ('rehab_motion', '康复动作', 'rehab_sensor', 'manual', 'rehab', 'solar:physical-therapy-outline', 70),
        ('rom', '关节活动度', 'rehab_sensor', 'manual', 'rehab', 'solar:body-outline', 71)
        """
    );
  }

  /**
   * 加载 classpath 中的 SQL 种子文件，逐条执行 INSERT IGNORE 语句。
   * 单条失败仅打印日志，不中止启动。
   */
  private void loadSeedSql(String classpathResource) {
    try {
      ClassPathResource resource = new ClassPathResource(classpathResource);
      if (!resource.exists()) {
        return;
      }
      String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      // 按分号+换行分割，过滤注释行和空行
      for (String stmt : sql.split(";\\s*\\n")) {
        StringBuilder builder = new StringBuilder();
        for (String line : stmt.split("\\n")) {
          String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("--")) {
            continue;
          }
          builder.append(line).append('\n');
        }
        String statement = builder.toString().trim();
        if (statement.isEmpty()) {
          continue;
        }
        try {
          jdbcTemplate.execute(statement);
        } catch (Exception e) {
          // INSERT IGNORE 冲突、字段不匹配等是预期行为，仅打印不抛出
          System.err.println("[SchemaInit] 种子 SQL 跳过: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      System.err.println("[SchemaInit] 加载种子文件失败 " + classpathResource + ": " + e.getMessage());
    }
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
