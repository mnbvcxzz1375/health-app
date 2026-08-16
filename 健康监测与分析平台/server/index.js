import cors from 'cors'
import crypto from 'crypto'
import dotenv from 'dotenv'
import express from 'express'
import multer from 'multer'
import mysql from 'mysql2/promise'
import { PDFParse } from 'pdf-parse'

dotenv.config()

const app = express()
app.use(cors())
app.use(express.json({ limit: '5mb' }))

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 },
})

const USER_ID = 1

const dbConfig = {
  host: process.env.DB_HOST ?? '127.0.0.1',
  port: Number(process.env.DB_PORT ?? 3306),
  user: process.env.DB_USER ?? 'root',
  password: process.env.DB_PASSWORD ?? '',
  database: process.env.DB_NAME ?? 'health_monitoring',
  connectionLimit: Number(process.env.DB_POOL_LIMIT ?? 10),
}

const pool = mysql.createPool(dbConfig)

const dashScopeApiKey = process.env.DASHSCOPE_API_KEY ?? process.env.QWEN_API_KEY ?? ''
const defaultDashScopeBaseUrl = /^sk-sp-/.test(dashScopeApiKey)
  ? 'https://coding.dashscope.aliyuncs.com/v1'
  : 'https://dashscope.aliyuncs.com/compatible-mode/v1'
const dashScopeBaseUrl = process.env.DASHSCOPE_BASE_URL ?? defaultDashScopeBaseUrl
const dashScopeVisionModel = process.env.DASHSCOPE_VISION_MODEL ?? 'qwen3.5-plus'
const dashScopeChatModel = process.env.DASHSCOPE_CHAT_MODEL ?? 'qwen3.5-plus'
const customUploadAnalyzeUrl = process.env.CUSTOM_UPLOAD_ANALYZE_URL ?? ''
const customMedicationRecognizeUrl = process.env.CUSTOM_MEDICATION_RECOGNIZE_URL ?? ''

const hashPassword = (password) => crypto.createHash('sha256').update(password).digest('hex')

const generateToken = () =>
  `token_${Math.random().toString(36).slice(2)}_${Date.now().toString(36)}`

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

const getAuthToken = (req) => {
  const header = req.headers.authorization
  if (!header || typeof header !== 'string') return ''
  if (!header.startsWith('Bearer ')) return ''
  return header.slice(7)
}

const ensureMedicationTables = async () => {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS medications (
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
    )`,
  )

  try {
    await pool.query(
      `ALTER TABLE medications
       CHANGE COLUMN usage usage_label VARCHAR(32) NOT NULL DEFAULT '饭后'`,
    )
  } catch {
    // 忽略：列已是 usage_label 或不存在旧列
  }

  await pool.query(
    `CREATE TABLE IF NOT EXISTS medication_reminders (
      id INT AUTO_INCREMENT PRIMARY KEY,
      medication_id INT NOT NULL,
      user_id INT NOT NULL,
      reminder_time VARCHAR(8) NOT NULL,
      enabled TINYINT(1) NOT NULL DEFAULT 1,
      created_at DATETIME NOT NULL,
      INDEX idx_med_reminder_user (user_id),
      INDEX idx_med_reminder_med (medication_id)
    )`,
  )

  await pool.query(
    `CREATE TABLE IF NOT EXISTS medication_alarm_groups (
      id INT AUTO_INCREMENT PRIMARY KEY,
      user_id INT NOT NULL,
      alarm_time VARCHAR(8) NOT NULL,
      enabled TINYINT(1) NOT NULL DEFAULT 1,
      created_at DATETIME NOT NULL,
      updated_at DATETIME NOT NULL,
      UNIQUE KEY uniq_med_alarm_user_time (user_id, alarm_time)
    )`,
  )

  await pool.query(
    `CREATE TABLE IF NOT EXISTS medication_alarm_items (
      id INT AUTO_INCREMENT PRIMARY KEY,
      alarm_id INT NOT NULL,
      medication_id INT NOT NULL,
      sort_order INT NOT NULL DEFAULT 0,
      created_at DATETIME NOT NULL,
      UNIQUE KEY uniq_med_alarm_item (alarm_id, medication_id),
      INDEX idx_med_alarm_item_alarm (alarm_id),
      INDEX idx_med_alarm_item_med (medication_id)
    )`,
  )

  await pool.query(
    `INSERT INTO medication_alarm_groups (user_id, alarm_time, enabled, created_at, updated_at)
     SELECT mr.user_id,
            mr.reminder_time,
            MAX(mr.enabled) AS enabled,
            MIN(mr.created_at) AS created_at,
            NOW() AS updated_at
     FROM medication_reminders mr
     LEFT JOIN medication_alarm_groups mag
       ON mag.user_id = mr.user_id
      AND mag.alarm_time = mr.reminder_time
     WHERE mag.id IS NULL
     GROUP BY mr.user_id, mr.reminder_time`,
  )

  await pool.query(
    `INSERT IGNORE INTO medication_alarm_items (alarm_id, medication_id, sort_order, created_at)
     SELECT mag.id,
            mr.medication_id,
            0 AS sort_order,
            NOW() AS created_at
     FROM medication_reminders mr
     JOIN medication_alarm_groups mag
       ON mag.user_id = mr.user_id
      AND mag.alarm_time = mr.reminder_time`,
  )
}

ensureMedicationTables().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure medication tables', error)
})

const ensureAuthTables = async () => {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS auth_users (
      id INT AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR(64) NOT NULL,
      email VARCHAR(128) NOT NULL UNIQUE,
      password_hash VARCHAR(128) NOT NULL,
      created_at DATETIME NOT NULL
    )`,
  )

  await pool.query(
    `CREATE TABLE IF NOT EXISTS auth_sessions (
      token VARCHAR(128) PRIMARY KEY,
      user_id INT NOT NULL,
      created_at DATETIME NOT NULL,
      last_active DATETIME NOT NULL,
      INDEX idx_auth_sessions_user (user_id)
    )`,
  )
}

ensureAuthTables().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure auth tables', error)
})

const ensureAnalyzeTaskColumns = async () => {
  try {
    await pool.query(`ALTER TABLE analyze_tasks ADD COLUMN report_json LONGTEXT NULL`)
  } catch {
    // ignore
  }

  try {
    await pool.query(`ALTER TABLE analyze_tasks ADD COLUMN saved TINYINT(1) NOT NULL DEFAULT 0`)
  } catch {
    // ignore
  }
}

ensureAnalyzeTaskColumns().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure analyze task columns', error)
})

const ensureDeviceTables = async () => {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS devices (
      id INT AUTO_INCREMENT PRIMARY KEY,
      user_id INT NOT NULL,
      label VARCHAR(64) NOT NULL,
      device_type VARCHAR(32) NOT NULL
    )`,
  )

  const alterStatements = [
    `ALTER TABLE devices ADD COLUMN name VARCHAR(120) NOT NULL DEFAULT ''`,
    `ALTER TABLE devices ADD COLUMN brand VARCHAR(64) NOT NULL DEFAULT ''`,
    `ALTER TABLE devices ADD COLUMN model VARCHAR(64) NOT NULL DEFAULT ''`,
    `ALTER TABLE devices ADD COLUMN connected TINYINT(1) NOT NULL DEFAULT 1`,
    `ALTER TABLE devices ADD COLUMN battery INT NOT NULL DEFAULT 100`,
    `ALTER TABLE devices ADD COLUMN last_sync_at DATETIME NULL`,
  ]

  for (const statement of alterStatements) {
    try {
      await pool.query(statement)
    } catch {
      // ignore existing columns
    }
  }

  try {
    await pool.query(`UPDATE devices SET name = label WHERE name = '' OR name IS NULL`)
  } catch {
    // ignore
  }
}

ensureDeviceTables().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure device tables', error)
})

const ensureRehabSettingsTable = async () => {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS rehab_plan_settings (
      id INT AUTO_INCREMENT PRIMARY KEY,
      user_id INT NOT NULL,
      focus VARCHAR(120) NOT NULL DEFAULT '',
      frequency VARCHAR(64) NOT NULL DEFAULT '',
      duration VARCHAR(64) NOT NULL DEFAULT '',
      intensity VARCHAR(64) NOT NULL DEFAULT '',
      created_at DATETIME NOT NULL,
      updated_at DATETIME NOT NULL,
      UNIQUE KEY uniq_rehab_plan_settings_user (user_id)
    )`,
  )
}

ensureRehabSettingsTable().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure rehab settings table', error)
})

const ensureRehabExerciseUserColumn = async () => {
  try {
    await pool.query(`ALTER TABLE rehab_exercises ADD COLUMN user_id INT NULL AFTER id`)
  } catch {
    // ignore
  }
}

ensureRehabExerciseUserColumn().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure rehab exercise user column', error)
})

const ensureRehabPlanReminderTable = async () => {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS rehab_plan_reminders (
      id INT AUTO_INCREMENT PRIMARY KEY,
      user_id INT NOT NULL,
      reminder_time VARCHAR(8) NOT NULL,
      days_json TEXT,
      push_enabled TINYINT(1) NOT NULL DEFAULT 1,
      created_at DATETIME NOT NULL,
      updated_at DATETIME NOT NULL,
      UNIQUE KEY uniq_rehab_plan_reminder_user (user_id)
    )`,
  )
}

ensureRehabPlanReminderTable().catch((error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to ensure rehab plan reminder table', error)
})

app.use(async (req, _res, next) => {
  const token = getAuthToken(req)
  if (!token) {
    req.userId = USER_ID
    next()
    return
  }

  try {
    const [[row]] = await pool.query(
      `SELECT user_id
       FROM auth_sessions
       WHERE token = ?
       LIMIT 1`,
      [token],
    )
    req.userId = row?.user_id ?? USER_ID
    if (row?.user_id) {
      await pool.query('UPDATE auth_sessions SET last_active = NOW() WHERE token = ?', [token])
    }
    next()
  } catch (error) {
    next(error)
  }
})

const metricColumnMap = {
  hr: 'hr',
  sleep: 'sleep_score',
  stress: 'stress_score',
}

const rangeConfigMap = {
  hour: {
    where: 'recorded_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)',
    format: '%H:%i',
  },
  day: {
    where: 'recorded_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)',
    format: '%m-%d',
  },
  month: {
    where: 'recorded_at >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH)',
    format: '%Y-%m',
  },
}

const parseJsonArray = (value) => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const dayLabelMap = {
  mon: '周一',
  tue: '周二',
  wed: '周三',
  thu: '周四',
  fri: '周五',
  sat: '周六',
  sun: '周日',
}

const buildInsight = (metric, values) => {
  if (!values.length) {
    return {
      insight: '暂无趋势数据，可先同步设备或稍后再试。',
      suggestion: '确保设备连接正常，并保持规律记录。',
    }
  }

  const avg = values.reduce((sum, val) => sum + val, 0) / values.length

  if (metric === 'hr') {
    if (avg >= 95) {
      return { insight: '心率偏高，波动幅度略大。', suggestion: '减少高强度训练，注意补水与恢复。' }
    }
    if (avg <= 60) {
      return { insight: '心率偏低，整体较为平稳。', suggestion: '注意热身，保持日常轻度活动。' }
    }
    return { insight: '心率维持在舒适区间。', suggestion: '维持当前运动节奏即可。' }
  }

  if (metric === 'sleep') {
    if (avg >= 85) {
      return { insight: '睡眠评分较高，恢复状态良好。', suggestion: '保持固定作息与晚间放松习惯。' }
    }
    if (avg <= 70) {
      return { insight: '睡眠评分偏低，恢复略不足。', suggestion: '减少晚间屏幕时间，适当提前入睡。' }
    }
    return { insight: '睡眠表现中等，波动不大。', suggestion: '继续关注入睡时间与睡前放松。' }
  }

  if (avg >= 70) {
    return { insight: '压力指数偏高，短期内有上升趋势。', suggestion: '建议增加深呼吸或冥想练习。' }
  }
  if (avg <= 50) {
    return { insight: '压力指数较低，状态稳定。', suggestion: '保持规律运动与休息节奏。' }
  }
  return { insight: '压力指数中等，可适当调整节奏。', suggestion: '工作间隙注意拉伸与放松。' }
}

const analyzeTypeLabelMap = {
  image: '影像资料',
  lab: '化验报告',
  text: '文字报告',
  symptom: '症状描述',
}

const uploadAnalysisSystemPrompt =
  '你是中文健康资料分析助手。' +
  '你只能基于用户上传的文字、图片和文件内容做健康管理辅助分析，不允许编造信息。' +
  '你不能替代医生诊断，不要输出 Markdown，不要输出代码块。' +
  '请只返回 JSON，固定结构为' +
  '{"title":"","summary":"","riskLevel":"","points":["","",""],"advice":["","",""],"rehabFocus":"","followUp":["","",""],"caution":""}。' +
  'riskLevel 只能是 低风险、中等风险、高风险 之一。' +
  'points、advice、followUp 每项返回 2 到 4 条中文短句。'
  '你是中文健康资料分析助手。' +
  '你只能基于用户上传的文字、图片和文件内容做健康管理辅助分析，不允许编造不存在的信息。' +
  '你不能替代医生诊断，不要输出 Markdown，不要输出代码块。' +
  '请只返回 JSON，固定结构为' +
  '{"title":"","summary":"","riskLevel":"","points":["","",""],"advice":["","",""],"rehabFocus":"","followUp":["","",""],"caution":""}。' +
  'riskLevel 只能是 低风险、中等风险、高风险 之一。' +
  'points、advice、followUp 每项返回 2 到 4 条中文短句。'

const normalizeAnalyzeReport = (payload, type) => {
  const normalizedType = String(type ?? 'text').trim() || 'text'
  const typeLabel = analyzeTypeLabelMap[normalizedType] ?? '健康资料'
  const report = payload && typeof payload === 'object' ? payload : {}
  const title = String(report.title ?? `${typeLabel}分析报告`).trim() || `${typeLabel}分析报告`
  const summary = String(report.summary ?? '').trim() || `已完成${typeLabel}分析，请结合实际情况核对结果。`
  const riskLevelRaw = String(report.riskLevel ?? '').trim()
  const riskLevel = ['低风险', '中等风险', '高风险'].includes(riskLevelRaw) ? riskLevelRaw : '中等风险'
  const rehabFocus = String(report.rehabFocus ?? '').trim() || '以低强度恢复和持续观察为主。'
  const caution =
    String(report.caution ?? '').trim() || '以上分析仅用于健康管理辅助，不替代医生诊疗与正式报告结论。'

  const normalizeList = (value, fallback) => {
    const items = Array.isArray(value)
      ? value.map((item) => String(item ?? '').trim()).filter(Boolean).slice(0, 4)
      : []
    return items.length ? items : fallback
  }

  return {
    title,
    summary,
    riskLevel,
    points: normalizeList(report.points, ['当前资料已完成结构化分析。', '建议结合症状和既往情况进一步判断。']),
    advice: normalizeList(report.advice, ['先按照低风险方式调整作息与训练强度。', '若症状持续或加重，请尽快线下就医。']),
    rehabFocus,
    followUp: normalizeList(report.followUp, ['继续观察 3 到 7 天内的变化。', '必要时补充更完整的检查资料。']),
    caution,
  }
}

const validReminderDays = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']
const validPlanExerciseModes = ['existing', 'generated']
const validPlanExerciseLevels = ['基础', '进阶']
const rehabPlanLevelMap = {
  basic: '基础',
  advanced: '进阶',
  基础: '基础',
  进阶: '进阶',
  基础级: '基础',
  进阶级: '进阶',
}

const rehabPlanDraftSystemPrompt =
  '你是中文康复计划生成助手。' +
  '你的输入是最近 3 份已经分析完成的健康报告 JSON。' +
  '你必须基于这些结构化报告生成可执行的康复计划草案，不允许输出 Markdown，不允许输出代码块，只能输出 JSON。' +
  '如果多份报告出现冲突，优先参考更新时间更近的报告，同时训练负荷按更保守原则收敛。' +
  '请严格输出固定 JSON：' +
  '{"summary":{"focus":"","frequency":"","duration":"","intensity":""},"exercises":[{"mode":"existing","name":"","category":"","duration":"","level":"基础","minutes":0,"steps":[""],"caution":"","focus":"","benefits":[""],"videoMinutes":0}],"reminder":{"time":"08:00","days":["mon","wed","fri"],"pushEnabled":true}}。' +
  'summary 和 reminder 必填；exercises 必须返回 4 个动作。' +
  'mode 只能是 existing 或 generated。' +
  'level 只能是 基础 或 进阶。' +
  'days 只能使用 mon,tue,wed,thu,fri,sat,sun。' +
  '如果动作命中现有动作库，请将 mode 设为 existing，并且 name 必须与动作库中的中文名称完全一致。' +
  '如果动作不在动作库中，请将 mode 设为 generated，并完整填写该动作的分类、时长、步骤、注意事项、重点和收益。'

const parseJsonString = (value, fallback = null) => {
  if (!value || typeof value !== 'string') return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const normalizeRehabDraftTextList = (value, fieldName, min = 1, max = 4) => {
  const items = Array.isArray(value) ? value.map((item) => String(item ?? '').trim()).filter(Boolean) : []
  if (items.length < min) {
    const error = new Error(`康复计划草案缺少 ${fieldName}`)
    error.statusCode = 502
    throw error
  }
  return items.slice(0, max)
}

const normalizeRehabDraftExercise = (payload) => {
  const mode = String(payload?.mode ?? 'generated').trim()
  const name = String(payload?.name ?? '').trim()
  const category = String(payload?.category ?? '').trim()
  const duration = String(payload?.duration ?? '').trim()
  const rawLevel = String(payload?.level ?? '').trim()
  const level = rehabPlanLevelMap[rawLevel] ?? rawLevel
  const minutes = Number(payload?.minutes)
  const caution = String(payload?.caution ?? '').trim()
  const focus = String(payload?.focus ?? '').trim()
  const videoMinutes = Number(payload?.videoMinutes)

  if (!validPlanExerciseModes.includes(mode)) {
    const error = new Error('康复计划草案动作 mode 无效')
    error.statusCode = 502
    throw error
  }
  if (!name || !category || !duration || !caution || !focus) {
    const error = new Error('康复计划草案动作字段不完整')
    error.statusCode = 502
    throw error
  }
  if (!validPlanExerciseLevels.includes(level)) {
    const error = new Error('康复计划草案动作难度无效')
    error.statusCode = 502
    throw error
  }
  if (!Number.isFinite(minutes) || minutes <= 0) {
    const error = new Error('康复计划草案动作时长无效')
    error.statusCode = 502
    throw error
  }

  return {
    mode,
    name,
    category,
    duration,
    level,
    minutes: Math.max(1, Math.min(90, Math.round(minutes))),
    steps: normalizeRehabDraftTextList(payload?.steps, '动作步骤', 2, 5),
    caution,
    focus,
    benefits: normalizeRehabDraftTextList(payload?.benefits, '动作收益', 1, 4),
    videoMinutes:
      Number.isFinite(videoMinutes) && videoMinutes > 0 ? Math.max(1, Math.min(30, Math.round(videoMinutes))) : 5,
  }
}

const normalizeRehabPlanDraft = (payload, sourceTaskIds) => {
  const summary = payload?.summary && typeof payload.summary === 'object' ? payload.summary : {}
  const reminder = payload?.reminder && typeof payload.reminder === 'object' ? payload.reminder : {}
  const exercises = Array.isArray(payload?.exercises) ? payload.exercises.map(normalizeRehabDraftExercise) : []

  if (exercises.length !== 4) {
    const error = new Error('康复计划草案必须包含 4 个动作')
    error.statusCode = 502
    throw error
  }

  const time = String(reminder.time ?? '').trim()
  if (!/^\d{2}:\d{2}$/.test(time)) {
    const error = new Error('康复计划草案提醒时间无效')
    error.statusCode = 502
    throw error
  }

  const days = Array.isArray(reminder.days)
    ? reminder.days.map((day) => String(day ?? '').trim()).filter((day) => validReminderDays.includes(day))
    : []
  if (!days.length) {
    const error = new Error('康复计划草案缺少提醒日期')
    error.statusCode = 502
    throw error
  }

  const focus = String(summary.focus ?? '').trim()
  const frequency = String(summary.frequency ?? '').trim()
  const duration = String(summary.duration ?? '').trim()
  const intensity = String(summary.intensity ?? '').trim()
  if (!focus || !frequency || !duration || !intensity) {
    const error = new Error('康复计划草案摘要字段不完整')
    error.statusCode = 502
    throw error
  }

  return {
    sourceTaskIds,
    summary: { focus, frequency, duration, intensity },
    exercises,
    reminder: {
      time,
      days,
      pushEnabled: reminder.pushEnabled !== false,
    },
  }
}

const generateRehabPlanDraftFromReports = async ({ reports, currentUserId }) => {
  if (!dashScopeApiKey) {
    const error = new Error('服务端未配置 DASHSCOPE_API_KEY，无法生成康复计划。')
    error.statusCode = 503
    throw error
  }

  if (!Array.isArray(reports) || !reports.length) {
    const error = new Error('缺少可用于生成康复计划的报告。')
    error.statusCode = 400
    throw error
  }

  const [exerciseRows] = await pool.query(
    `SELECT name, category, duration, level, minutes, focus
     FROM rehab_exercises
     WHERE user_id IS NULL OR user_id = ?
     ORDER BY user_id IS NULL DESC, id ASC`,
    [currentUserId],
  )

  const content = [
    `当前用户最近纳入计划生成的报告数量：${reports.length}`,
    '以下是按时间由新到旧排列的结构化报告：',
    JSON.stringify(
      reports.map((report) => ({
        taskId: report.taskId,
        type: report.type,
        updatedAt: report.updatedAt,
        report: report.report,
      })),
      null,
      2,
    ),
    '以下是当前可直接复用的动作库（命中时必须使用 existing）：',
    JSON.stringify(exerciseRows, null, 2),
  ].join('\n\n')

  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: dashScopeChatModel,
      messages: [
        { role: 'system', content: rehabPlanDraftSystemPrompt },
        { role: 'user', content },
      ],
      temperature: 0.2,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '康复计划生成失败')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  const data = await response.json()
  const parsed = extractJsonObject(extractAssistantText(data?.choices?.[0]?.message?.content))
  if (!parsed) {
    const error = new Error('康复计划生成返回格式无效，未得到可解析的 JSON。')
    error.statusCode = 502
    throw error
  }

  return normalizeRehabPlanDraft(
    parsed,
    reports.map((item) => item.taskId),
  )
}

const extractTextFromPdfBuffer = async (buffer) => {
  const parser = new PDFParse({ data: buffer })
  try {
    const parsed = await parser.getText()
    return String(parsed?.text ?? '').replace(/\s+/g, ' ').trim()
  } finally {
    await parser.destroy()
  }
}

const buildUploadAnalysisContent = async ({ type, text, files }) => {
  const normalizedType = String(type ?? 'text').trim() || 'text'
  const content = [
    {
      type: 'text',
      text:
        `资料类型：${analyzeTypeLabelMap[normalizedType] ?? normalizedType}\n` +
        '请根据本次上传内容输出结构化健康管理分析报告。重点包括：总体总结、风险等级、关注点、建议、康复重点和后续观察要点。',
    },
  ]

  const normalizedText = String(text ?? '').trim()
  if (normalizedText) {
    content.push({
      type: 'text',
      text: `用户补充文字：\n${normalizedText.slice(0, 6000)}`,
    })
  }

  for (const file of files) {
    if (String(file.mimetype ?? '').startsWith('image/')) {
      content.push({
        type: 'image_url',
        image_url: {
          url: `data:${file.mimetype || 'image/jpeg'};base64,${file.buffer.toString('base64')}`,
        },
      })
      continue
    }

    const lowerName = String(file.originalname ?? '').toLowerCase()
    const isPdf = lowerName.endsWith('.pdf') || String(file.mimetype ?? '') === 'application/pdf'
    if (isPdf) {
      const extractedText = await extractTextFromPdfBuffer(file.buffer)
      if (extractedText) {
        content.push({
          type: 'text',
          text: `PDF 文件《${file.originalname}》提取内容：\n${extractedText.slice(0, 8000)}`,
        })
      }
      continue
    }

    content.push({
      type: 'text',
      text: `文件《${file.originalname}》当前未提取到正文，请仅参考文件名：${file.originalname}`,
    })
  }

  return content
}

const analyzeUploadByQwen = async ({ type, text, files }) => {
  if (!dashScopeApiKey) {
    const error = new Error('服务端未配置 DASHSCOPE_API_KEY，无法调用上传分析。')
    error.statusCode = 503
    throw error
  }

  const normalizedText = String(text ?? '').trim()
  const normalizedFiles = Array.isArray(files) ? files : []
  if (!normalizedText && !normalizedFiles.length) {
    const error = new Error('请先上传文件、图片或输入分析内容。')
    error.statusCode = 400
    throw error
  }

  const content = await buildUploadAnalysisContent({ type, text: normalizedText, files: normalizedFiles })
  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: dashScopeVisionModel,
      messages: [
        { role: 'system', content: uploadAnalysisSystemPrompt },
        { role: 'user', content },
      ],
      temperature: 0.2,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '上传分析调用失败')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  const data = await response.json()
  const contentText = extractAssistantText(data?.choices?.[0]?.message?.content)
  const parsed = extractJsonObject(contentText)
  if (!parsed) {
    const error = new Error('上传分析返回格式无效，未得到可解析的 JSON。')
    error.statusCode = 502
    throw error
  }

  return normalizeAnalyzeReport(parsed, type)
}

const uploadAnalysisSystemPromptV2 =
  '你是中文健康资料分析助手。' +
  '你只能基于用户上传的文字、图片和文件内容做健康管理辅助分析，不允许编造信息。' +
  '你不能替代医生诊断，不要输出 Markdown，不要输出代码块。' +
  '请只返回 JSON，固定结构为' +
  '{"title":"","summary":"","riskLevel":"","points":["","",""],"advice":["","",""],"rehabFocus":"","followUp":["","",""],"caution":""}。' +
  'riskLevel 只能是 低风险、中等风险、高风险 之一。' +
  'points、advice、followUp 每项返回 2 到 4 条中文短句。'

const buildUploadAnalysisInputV2 = async ({ type, text, files }) => {
  const normalizedType = String(type ?? 'text').trim() || 'text'
  const normalizedText = String(text ?? '').trim()
  const normalizedFiles = Array.isArray(files) ? files : []
  const typeLabel = analyzeTypeLabelMap[normalizedType] ?? normalizedType
  const promptParts = [
    `资料类型：${typeLabel}`,
    '请根据本次上传内容输出结构化健康管理分析报告，重点包括总体总结、风险等级、关注点、建议、康复重点和后续观察要点。',
  ]

  if (normalizedText) {
    promptParts.push(`用户补充文字：\n${normalizedText.slice(0, 6000)}`)
  }

  const imageParts = []
  const textParts = []

  for (const file of normalizedFiles) {
    if (String(file.mimetype ?? '').startsWith('image/')) {
      imageParts.push({
        type: 'image_url',
        image_url: {
          url: `data:${file.mimetype || 'image/jpeg'};base64,${file.buffer.toString('base64')}`,
        },
      })
      continue
    }

    const lowerName = String(file.originalname ?? '').toLowerCase()
    const isPdf = lowerName.endsWith('.pdf') || String(file.mimetype ?? '') === 'application/pdf'
    if (isPdf) {
      const extractedText = await extractTextFromPdfBuffer(file.buffer)
      if (extractedText) {
        textParts.push(`PDF 文件《${file.originalname}》提取内容：\n${extractedText.slice(0, 8000)}`)
      }
      continue
    }

    textParts.push(`文件《${file.originalname}》当前未提取到正文，请仅参考文件名：${file.originalname}`)
  }

  if (textParts.length) {
    promptParts.push(textParts.join('\n\n'))
  }

  const promptText = promptParts.join('\n\n')
  if (imageParts.length) {
    return {
      model: dashScopeVisionModel,
      content: [{ type: 'text', text: promptText }, ...imageParts],
    }
  }

  return {
    model: dashScopeChatModel,
    content: promptText,
  }
}

const analyzeUploadByQwenV2 = async ({ type, text, files }) => {
  if (!dashScopeApiKey) {
    const error = new Error('服务端未配置 DASHSCOPE_API_KEY，无法调用上传分析。')
    error.statusCode = 503
    throw error
  }

  const normalizedText = String(text ?? '').trim()
  const normalizedFiles = Array.isArray(files) ? files : []
  if (!normalizedText && !normalizedFiles.length) {
    const error = new Error('请先上传文件、图片或输入分析内容。')
    error.statusCode = 400
    throw error
  }

  const requestInput = await buildUploadAnalysisInputV2({
    type,
    text: normalizedText,
    files: normalizedFiles,
  })

  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: requestInput.model,
      messages: [
        { role: 'system', content: uploadAnalysisSystemPromptV2 },
        { role: 'user', content: requestInput.content },
      ],
      temperature: 0.2,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '上传分析调用失败')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  const data = await response.json()
  const contentText = extractAssistantText(data?.choices?.[0]?.message?.content)
  const parsed = extractJsonObject(contentText)
  if (!parsed) {
    const error = new Error('上传分析返回格式无效，未得到可解析的 JSON。')
    error.statusCode = 502
    throw error
  }

  return normalizeAnalyzeReport(parsed, type)
}

const forwardMultipartToCustomModel = async ({ url, files, fields }) => {
  if (!url) {
    const error = new Error('当前尚未接入你的自定义模型接口。')
    error.statusCode = 501
    throw error
  }

  const formData = new FormData()
  Object.entries(fields ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      formData.append(key, String(value))
    }
  })

  for (const file of files ?? []) {
    const blob = new Blob([file.buffer], { type: file.mimetype || 'application/octet-stream' })
    formData.append('files', blob, file.originalname || 'upload.bin')
  }

  const response = await fetch(url, {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '自定义模型接口调用失败')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  return response.json()
}

const extractJsonObject = (value) => {
  if (!value || typeof value !== 'string') return null
  const start = value.indexOf('{')
  const end = value.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try {
    return JSON.parse(value.slice(start, end + 1))
  } catch {
    return null
  }
}

const normalizeMedicationUsage = (value) => {
  const text = String(value ?? '').trim()
  if (!text) return ''

  const usageMap = new Map([
    ['\u996d\u524d', '\u996d\u524d'],
    ['before_meal', '\u996d\u524d'],
    ['\u996d\u540e', '\u996d\u540e'],
    ['after_meal', '\u996d\u540e'],
    ['\u968f\u9910', '\u968f\u9910'],
    ['with_meal', '\u968f\u9910'],
    ['\u7761\u524d', '\u7761\u524d'],
    ['bedtime', '\u7761\u524d'],
    ['\u6309\u9700', '\u6309\u9700'],
    ['as_needed', '\u6309\u9700'],
  ])

  return usageMap.get(text) ?? ''
}

const normalizeMedicationUnit = (value) => {
  const text = String(value ?? '').trim()
  if (!text) return ''

  const unitMap = new Map([
    ['\u7247', '\u7247'],
    ['tablet', '\u7247'],
    ['\u7c92', '\u7c92'],
    ['capsule', '\u7c92'],
    ['\u6beb\u5347', '\u6beb\u5347'],
    ['ml', '\u6beb\u5347'],
    ['\u6ef4', '\u6ef4'],
    ['drop', '\u6ef4'],
    ['\u888b', '\u888b'],
    ['bag', '\u888b'],
  ])

  return unitMap.get(text) ?? ''
}

const normalizeMedicationRecognition = (payload) => ({
  name: String(payload?.name ?? payload?.medicationName ?? '').trim(),
  alias: String(payload?.alias ?? '').trim(),
  dosageValue:
    Number.isFinite(Number(payload?.dosageValue)) && Number(payload?.dosageValue) > 0
      ? Math.max(1, Math.min(12, Number(payload?.dosageValue)))
      : null,
  dosageUnit: normalizeMedicationUnit(payload?.dosageUnit),
  usage: normalizeMedicationUsage(payload?.usage),
  notes: String(payload?.notes ?? '').trim(),
  photoUrl: String(payload?.photoUrl ?? '').trim(),
  confidence: typeof payload?.confidence === 'number' ? payload.confidence : null,
  sourceText: String(payload?.sourceText ?? '').trim(),
})

const extractAssistantText = (content) => {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  return content
    .map((item) => {
      if (typeof item === 'string') return item
      if (item?.type === 'text') return item.text ?? ''
      return ''
    })
    .join('\n')
}

const readDashScopeErrorMessage = async (response, fallbackMessage) => {
  const raw = await response.text()
  if (!raw) return fallbackMessage

  try {
    const parsed = JSON.parse(raw)
    return (
      parsed?.error?.message ||
      parsed?.message ||
      parsed?.error?.code ||
      fallbackMessage
    )
  } catch {
    return raw
  }
}

const recognizeMedicationByQwen = async (files) => {
  if (!dashScopeApiKey) {
    const error = new Error('服务端未配置 DASHSCOPE_API_KEY，无法调用药盒识别。')
    error.statusCode = 503
    throw error
  }

  if (!Array.isArray(files) || !files.length) {
    const error = new Error('请至少上传一张药盒图片')
    error.statusCode = 400
    throw error
  }

  const imageBlocks = files.map((file) => {
    const mimeType = file.mimetype || 'image/jpeg'
    return {
      type: 'image_url',
      image_url: {
        url: `data:${mimeType};base64,${file.buffer.toString('base64')}`,
      },
    }
  })

  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: dashScopeVisionModel,
      messages: [
        {
          role: 'system',
          content:
            '????????????????????????????????????????????????????? JSON????? Markdown????????????? {"items":[{"name":"","alias":"","dosageValue":null,"dosageUnit":"","usage":"","notes":"","photoUrl":"","sourceText":"","confidence":0.9}],"confidence":0.9}?dosageUnit ???? ?????????? ??????usage ???? ?????????????? ??????',
        },
        {
          role: 'user',
          content: [
            {
              type: 'text',
              text:
                '??????????????????????????????? items ?????????????????????????????????????????????????????????????? null????????????????????? sourceText ???',
            },
            ...imageBlocks,
          ],
        },
      ],
      temperature: 0.1,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '??????')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  const data = await response.json()
  const content = data?.choices?.[0]?.message?.content
  const text = extractAssistantText(content)
  const parsed = extractJsonObject(text)

  if (!parsed) {
    const error = new Error('识别结果格式无效，未返回可解析的 JSON。')
    error.statusCode = 502
    throw error
  }

  return normalizeMedicationRecognition(parsed, files[0]?.originalname ?? '')
}

const normalizeMedicationRecognitionBatchV2 = (payload) => {
  const rawItems = Array.isArray(payload?.items) ? payload.items : [payload]
  const items = rawItems
    .map((item) => normalizeMedicationRecognition(item))
    .filter(
      (item) =>
        Boolean(
          item.name ||
            item.alias ||
            item.dosageValue ||
            item.dosageUnit ||
            item.usage ||
            item.notes ||
            item.photoUrl ||
            item.sourceText,
        ),
    )

  return {
    items,
    confidence: typeof payload?.confidence === 'number' ? payload.confidence : null,
  }
}

const normalizeConsultResponse = (payload) => ({
  requestId: crypto.randomUUID(),
  answer: String(payload?.answer ?? '').trim(),
  suggestions: Array.isArray(payload?.suggestions)
    ? payload.suggestions.map((item) => String(item ?? '').trim()).filter(Boolean).slice(0, 4)
    : [],
  disclaimer:
    String(payload?.disclaimer ?? '').trim() || '该回答仅用于健康管理辅助，不替代医生诊疗与处方。',
})

const medicationRecognitionSystemPrompt =
  '\u4f60\u662f\u836f\u76d2\u6587\u5b57\u7ed3\u6784\u5316\u63d0\u53d6\u52a9\u624b\u3002' +
  '\u4f60\u53ea\u80fd\u4f9d\u636e\u5f53\u524d\u4e0a\u4f20\u56fe\u7247\u4e2d\u8089\u773c\u53ef\u89c1\u7684\u6587\u5b57\u586b\u5199\u5b57\u6bb5\uff0c\u4e0d\u5141\u8bb8\u4f7f\u7528\u6587\u4ef6\u540d\uff0c\u4e0d\u5141\u8bb8\u4f9d\u8d56\u5e38\u8bc6\u63a8\u6d4b\uff0c\u4e0d\u5141\u8bb8\u7f16\u9020\u5185\u5bb9\u3002' +
  '\u5982\u679c\u67d0\u4e2a\u5b57\u6bb5\u65e0\u6cd5\u4ece\u56fe\u7247\u4e2d\u786e\u8ba4\uff0c\u8bf7\u8fd4\u56de\u7a7a\u5b57\u7b26\u4e32\u6216 null\u3002' +
  '\u8bf7\u53ea\u8fd4\u56de JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\uff0c\u4e0d\u8981\u89e3\u91ca\u3002' +
  '\u56fa\u5b9a\u8fd4\u56de\u7ed3\u6784\u4e3a' +
  '{"items":[{"name":"","alias":"","dosageValue":null,"dosageUnit":"","usage":"","notes":"","photoUrl":"","sourceText":""}]}\u3002' +
  '\u5176\u4e2d dosageUnit \u53ea\u80fd\u662f \u7247\u3001\u7c92\u3001\u6beb\u5347\u3001\u6ef4\u3001\u888b \u4e4b\u4e00\uff1b' +
  'usage \u53ea\u80fd\u662f \u996d\u524d\u3001\u996d\u540e\u3001\u968f\u9910\u3001\u7761\u524d\u3001\u6309\u9700 \u4e4b\u4e00\uff1b' +
  'sourceText \u9700\u8981\u586b\u5199\u4f60\u786e\u5b9e\u4ece\u56fe\u7247\u91cc\u8bfb\u5230\u7684\u5173\u952e\u6587\u5b57\u7247\u6bb5\u3002'

const medicationRecognitionUserPrompt =
  '\u8bf7\u5bf9\u672c\u6b21\u4e0a\u4f20\u7684\u5168\u90e8\u56fe\u7247\u4e00\u6b21\u6027\u5b8c\u6210\u8bc6\u522b\u3002' +
  '\u5982\u679c\u591a\u5f20\u56fe\u7247\u5c5e\u4e8e\u540c\u4e00\u79cd\u836f\uff0c\u8bf7\u5408\u5e76\u4e3a\u4e00\u6761 items\uff1b' +
  '\u5982\u679c\u56fe\u7247\u4e2d\u6709\u591a\u79cd\u4e0d\u540c\u836f\u54c1\uff0c\u8bf7\u9010\u6761\u8fd4\u56de\u3002' +
  '\u9700\u8981\u63d0\u53d6\u5e76\u8fd4\u56de\u7684\u5b57\u6bb5\u53ea\u6709\uff1a\u836f\u54c1\u540d\u79f0 name\u3001\u53e3\u8bed\u522b\u540d alias\u3001\u5355\u6b21\u5242\u91cf dosageValue\u3001\u5242\u91cf\u5355\u4f4d dosageUnit\u3001\u670d\u7528\u65b9\u5f0f usage\u3001\u6ce8\u610f\u4e8b\u9879 notes\u3001\u56fe\u7247\u5730\u5740 photoUrl\u3001\u8bc6\u522b\u4f9d\u636e sourceText\u3002' +
  '\u5982\u679c\u56fe\u4e2d\u540c\u65f6\u51fa\u73b0\u4e2d\u6587\u548c\u82f1\u6587\u836f\u540d\uff0cname \u4f18\u5148\u8fd4\u56de\u4e2d\u6587\u836f\u540d\uff1balias \u53ea\u5728\u56fe\u4e2d\u660e\u786e\u51fa\u73b0\u522b\u540d\u3001\u54c1\u724c\u540d\u6216\u53e3\u8bed\u540d\u79f0\u65f6\u518d\u586b\u5199\u3002' +
  '\u6240\u6709\u5b57\u6bb5\u90fd\u53ea\u80fd\u6765\u81ea\u56fe\u7247\u53ef\u89c1\u6587\u5b57\uff0c\u4e0d\u786e\u5b9a\u5c31\u7559\u7a7a\uff0c\u4e0d\u8981\u7528\u6587\u4ef6\u540d\u3001\u5916\u90e8\u77e5\u8bc6\u6216\u63a8\u6d4b\u8865\u5168\u3002'

const recognizeMedicationByQwenV2 = async (files) => {
  if (!dashScopeApiKey) {
    const error = new Error('\u670d\u52a1\u7aef\u672a\u914d\u7f6e DASHSCOPE_API_KEY\uff0c\u65e0\u6cd5\u8c03\u7528\u836f\u54c1\u8bc6\u522b\u3002')
    error.statusCode = 503
    throw error
  }

  if (!Array.isArray(files) || !files.length) {
    const error = new Error('\u8bf7\u81f3\u5c11\u4e0a\u4f20\u4e00\u5f20\u836f\u76d2\u56fe\u7247\u3002')
    error.statusCode = 400
    throw error
  }

  const imageBlocks = files.map((file) => {
    const mimeType = file.mimetype || 'image/jpeg'
    return {
      type: 'image_url',
      image_url: {
        url: `data:${mimeType};base64,${file.buffer.toString('base64')}`,
      },
    }
  })

  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: dashScopeVisionModel,
      messages: [
        { role: 'system', content: medicationRecognitionSystemPrompt },
        {
          role: 'user',
          content: [
            { type: 'text', text: medicationRecognitionUserPrompt },
            ...imageBlocks,
          ],
        },
      ],
      temperature: 0.1,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '\u836f\u54c1\u8bc6\u522b\u5931\u8d25')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  const data = await response.json()
  const content = data?.choices?.[0]?.message?.content
  const text = extractAssistantText(content)
  const parsed = extractJsonObject(text)

  if (!parsed) {
    const error = new Error('\u836f\u54c1\u8bc6\u522b\u8fd4\u56de\u683c\u5f0f\u65e0\u6548\uff0c\u672a\u5f97\u5230\u53ef\u89e3\u6790\u7684 JSON\u3002')
    error.statusCode = 502
    throw error
  }

  return normalizeMedicationRecognitionBatchV2(parsed)
}

const assistantSystemPrompt =
  '\u4f60\u662f\u4e2d\u6587\u5065\u5eb7\u7ba1\u7406\u52a9\u624b\u3002' +
  '\u4f60\u8981\u6839\u636e\u7528\u6237\u95ee\u9898\u7ed9\u51fa\u76f4\u63a5\u3001\u7b80\u6d01\u3001\u53ef\u6267\u884c\u7684\u4e2d\u6587\u5efa\u8bae\u3002' +
  '\u53ea\u8981\u7528\u6237\u95ee\u9898\u8868\u8fbe\u6e05\u695a\uff0c\u5c31\u5fc5\u987b\u76f4\u63a5\u56de\u7b54\uff0c\u4e0d\u8981\u7528\u201c\u95ee\u9898\u65e0\u6cd5\u8bc6\u522b\u201d\u3001\u201c\u8bf7\u91cd\u65b0\u63cf\u8ff0\u201d\u7b49\u6a21\u677f\u8bdd\u56de\u907f\u3002' +
  '\u5982\u679c\u95ee\u9898\u662f\u5173\u4e8e\u4f5c\u606f\u3001\u996e\u98df\u3001\u8fd0\u52a8\u3001\u75c7\u72b6\u89c2\u5bdf\u3001\u62a5\u544a\u89e3\u8bfb\u6216\u5c31\u533b\u5efa\u8bae\uff0c\u8bf7\u7ed9\u51fa 2 \u5230 4 \u6761\u5b9e\u7528\u5efa\u8bae\u3002' +
  '\u5982\u679c\u9700\u8981\u5c31\u533b\u6216\u7d27\u6025\u5904\u7406\uff0c\u8bf7\u660e\u786e\u63d0\u9192\u3002' +
  '\u4e0d\u8981\u7f16\u9020\u8bca\u65ad\uff0c\u4e0d\u8981\u627f\u8bfa\u7597\u6548\uff0c\u4e0d\u8981\u8f93\u51fa Markdown\u3002' +
  '\u8bf7\u53ea\u8fd4\u56de JSON\uff0c\u56fa\u5b9a\u7ed3\u6784\u4e3a' +
  '{"answer":"","suggestions":["","",""],"disclaimer":"\u8be5\u56de\u7b54\u4ec5\u7528\u4e8e\u5065\u5eb7\u7ba1\u7406\u8f85\u52a9\uff0c\u4e0d\u66ff\u4ee3\u533b\u751f\u8bca\u7597\u4e0e\u5904\u65b9\u3002"}\u3002'

const consultSceneLabelMap = {
  assistant: '\u667a\u80fd\u52a9\u624b\u5bf9\u8bdd',
  home_overview: '\u9996\u9875\u5065\u5eb7\u603b\u89c8',
}

const askConsultByQwen = async (payload) => {
  if (!dashScopeApiKey) {
    const error = new Error('\u670d\u52a1\u7aef\u672a\u914d\u7f6e DASHSCOPE_API_KEY\uff0c\u65e0\u6cd5\u8c03\u7528\u667a\u80fd\u52a9\u624b\u3002')
    error.statusCode = 503
    throw error
  }

  const question = String(payload?.question ?? '').trim()
  if (!question) {
    const error = new Error('\u8bf7\u8f93\u5165\u95ee\u9898\u5185\u5bb9\u3002')
    error.statusCode = 400
    throw error
  }

  const scene = String(payload?.scene ?? 'assistant').trim() || 'assistant'
  const sceneLabel = consultSceneLabelMap[scene] ?? scene

  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: dashScopeChatModel,
      messages: [
        { role: 'system', content: assistantSystemPrompt },
        {
          role: 'user',
          content:
            `\u5f53\u524d\u573a\u666f\uff1a${sceneLabel}\n` +
            `\u7528\u6237\u95ee\u9898\uff1a${question}\n` +
            '\u8bf7\u76f4\u63a5\u56de\u7b54\u8fd9\u4e2a\u95ee\u9898\uff0c\u5982\u679c\u95ee\u9898\u5df2\u7ecf\u6e05\u695a\uff0c\u4e0d\u8981\u8981\u6c42\u7528\u6237\u91cd\u65b0\u63cf\u8ff0\u3002',
        },
      ],
      temperature: 0.3,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '\u667a\u80fd\u52a9\u624b\u8c03\u7528\u5931\u8d25')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  const data = await response.json()
  const content = data?.choices?.[0]?.message?.content
  const parsed = extractJsonObject(extractAssistantText(content))

  if (!parsed) {
    const error = new Error('\u667a\u80fd\u52a9\u624b\u8fd4\u56de\u683c\u5f0f\u65e0\u6548\uff0c\u672a\u5f97\u5230\u53ef\u89e3\u6790\u7684 JSON\u3002')
    error.statusCode = 502
    throw error
  }

  return normalizeConsultResponse(parsed)
}

const assistantStreamingSystemPrompt =
  '\u4f60\u662f\u4e2d\u6587\u5065\u5eb7\u7ba1\u7406\u52a9\u624b\u3002' +
  '\u4f60\u8981\u76f4\u63a5\u3001\u7b80\u6d01\u5730\u56de\u7b54\u7528\u6237\u95ee\u9898\uff0c\u5e76\u7ed9\u51fa\u53ef\u6267\u884c\u7684\u4e2d\u6587\u5efa\u8bae\u3002' +
  '\u4e0d\u8981\u8f93\u51fa JSON\uff0c\u4e0d\u8981\u8f93\u51fa Markdown\uff0c\u4e0d\u8981\u7f16\u9020\u8bca\u65ad\uff0c\u4e0d\u8981\u627f\u8bfa\u7597\u6548\u3002'

const splitAnswerForStreaming = (answer) => {
  const normalized = String(answer ?? '').trim()
  if (!normalized) return []

  const sentences = normalized
    .split(/(?<=[。！？!?；;])/u)
    .map(item => item.trim())
    .filter(Boolean)

  if (sentences.length > 1) {
    return sentences
  }

  const chunks = []
  for (let index = 0; index < normalized.length; index += 18) {
    chunks.push(normalized.slice(index, index + 18))
  }
  return chunks
}

const writeStreamingAnswer = async (result, onChunk) => {
  const chunks = splitAnswerForStreaming(result?.answer)

  for (const chunk of chunks) {
    if (chunk) {
      onChunk(chunk)
    }
    await sleep(80)
  }

  return result
}

const streamConsultByQwen = async (payload, onChunk) => {
  if (!dashScopeApiKey) {
    const error = new Error('\u670d\u52a1\u7aef\u672a\u914d\u7f6e DASHSCOPE_API_KEY\uff0c\u65e0\u6cd5\u8c03\u7528\u667a\u80fd\u52a9\u624b\u3002')
    error.statusCode = 503
    throw error
  }

  const question = String(payload?.question ?? '').trim()
  if (!question) {
    const error = new Error('\u8bf7\u8f93\u5165\u95ee\u9898\u5185\u5bb9\u3002')
    error.statusCode = 400
    throw error
  }

  const scene = String(payload?.scene ?? 'assistant').trim() || 'assistant'
  const sceneLabel = consultSceneLabelMap[scene] ?? scene

  const response = await fetch(`${dashScopeBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${dashScopeApiKey}`,
    },
    body: JSON.stringify({
      model: dashScopeChatModel,
      stream: true,
      messages: [
        { role: 'system', content: assistantStreamingSystemPrompt },
        {
          role: 'user',
          content:
            `\u5f53\u524d\u573a\u666f\uff1a${sceneLabel}\n` +
            `\u7528\u6237\u95ee\u9898\uff1a${question}\n` +
            '\u8bf7\u76f4\u63a5\u7528\u4e2d\u6587\u56de\u7b54\u8fd9\u4e2a\u95ee\u9898\uff0c\u4e0d\u8981\u8981\u6c42\u7528\u6237\u91cd\u65b0\u63cf\u8ff0\u3002',
        },
      ],
      temperature: 0.3,
    }),
  })

  if (!response.ok) {
    const message = await readDashScopeErrorMessage(response, '\u667a\u80fd\u52a9\u624b\u8c03\u7528\u5931\u8d25')
    const error = new Error(message)
    error.statusCode = response.status
    throw error
  }

  if (!response.body) {
    const error = new Error('\u667a\u80fd\u52a9\u624b\u6d41\u5f0f\u54cd\u5e94\u4e0d\u53ef\u7528\u3002')
    error.statusCode = 502
    throw error
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let answer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed || !trimmed.startsWith('data:')) continue

      const payloadText = trimmed.slice(5).trim()
      if (!payloadText || payloadText === '[DONE]') continue

      let parsed
      try {
        parsed = JSON.parse(payloadText)
      } catch {
        continue
      }

      const delta = parsed?.choices?.[0]?.delta?.content
      if (typeof delta === 'string' && delta) {
        answer += delta
        onChunk(delta)
      }
    }
  }

  return {
    requestId: crypto.randomUUID(),
    answer: answer.trim(),
    suggestions: [],
    disclaimer: '\u8be5\u56de\u7b54\u4ec5\u7528\u4e8e\u5065\u5eb7\u7ba1\u7406\u8f85\u52a9\uff0c\u4e0d\u66ff\u4ee3\u533b\u751f\u8bca\u7597\u4e0e\u5904\u65b9\u3002',
  }
}
const buildWeekDelta = (values) => {
  if (values.length < 2) return 0
  const first = values[0]
  const last = values[values.length - 1]
  if (!first) return 0
  return Math.round(((last - first) / first) * 100)
}

const ensureUserProfile = async (userId, name, email) => {
  const [[profileRow]] = await pool.query(
    `SELECT id FROM user_profiles WHERE id = ? LIMIT 1`,
    [userId],
  )
  if (!profileRow) {
    await pool.query(
      `INSERT INTO user_profiles (id, name, email, avatar_url, risk_score, risk_level)
       VALUES (?, ?, ?, NULL, 18, '低风险')`,
      [userId, name, email],
    )
  }

  const [[settingsRow]] = await pool.query(
    `SELECT user_id FROM user_settings WHERE user_id = ? LIMIT 1`,
    [userId],
  )
  if (!settingsRow) {
    await pool.query(
      `INSERT INTO user_settings
        (user_id, age, gender, height, weight, focus, goals_json, daily_summary, risk_alert, rehab_reminder)
       VALUES (?, 28, 'other', 170, 60, '', '[]', 1, 1, 1)`,
      [userId],
    )
  }
}

const fetchAuthUser = async (userId) => {
  const [[profileRow]] = await pool.query(
    `SELECT id, name, email, avatar_url
     FROM user_profiles
     WHERE id = ?
     LIMIT 1`,
    [userId],
  )

  if (profileRow) {
    return {
      id: String(profileRow.id),
      name: profileRow.name,
      email: profileRow.email,
      avatarUrl: profileRow.avatar_url ?? '',
    }
  }

  const [[authRow]] = await pool.query(
    `SELECT id, name, email
     FROM auth_users
     WHERE id = ?
     LIMIT 1`,
    [userId],
  )

  if (!authRow) return null
  return {
    id: String(authRow.id),
    name: authRow.name,
    email: authRow.email,
    avatarUrl: '',
  }
}

const mapMedicationRows = (rows, reminderRows) => {
  const reminderMap = new Map()
  reminderRows.forEach((row) => {
    if (!reminderMap.has(row.medication_id)) {
      reminderMap.set(row.medication_id, [])
    }
    reminderMap.get(row.medication_id).push({
      id: row.id,
      time: row.reminder_time,
      enabled: Boolean(row.enabled),
    })
  })

  return rows.map((row) => ({
    id: row.id,
    name: row.name,
    alias: row.alias,
    dosageValue: Number(row.dosage_value),
    dosageUnit: row.dosage_unit,
    usage: row.usage_label,
    notes: row.notes ?? '',
    photoUrl: row.photo_url ?? '',
    enableOcr: Boolean(row.enable_ocr),
    enableYolo: Boolean(row.enable_yolo),
    ocrEndpoint: row.ocr_endpoint ?? '',
    yoloEndpoint: row.yolo_endpoint ?? '',
    enabled: Boolean(row.enabled),
    reminders: reminderMap.get(row.id) ?? [],
  }))
}

const upsertMedicationRecord = async (userId, payload) => {
  const normalized = {
    name: String(payload?.name ?? '').trim(),
    alias: String(payload?.alias ?? '').trim(),
    dosageValue: Math.max(1, Number(payload?.dosageValue ?? 1) || 1),
    dosageUnit: normalizeMedicationUnit(payload?.dosageUnit),
    usage: normalizeMedicationUsage(payload?.usage),
    notes: String(payload?.notes ?? '').trim(),
    photoUrl: String(payload?.photoUrl ?? '').trim(),
    enableOcr: payload?.enableOcr ? 1 : 0,
    enableYolo: payload?.enableYolo ? 1 : 0,
    ocrEndpoint: String(payload?.ocrEndpoint ?? '').trim(),
    yoloEndpoint: String(payload?.yoloEndpoint ?? '').trim(),
    enabled: payload?.enabled === false ? 0 : 1,
  }

  if (!normalized.name) {
    const error = new Error('药品名称不能为空')
    error.statusCode = 400
    throw error
  }

  if (payload?.id) {
    await pool.query(
      `UPDATE medications
       SET name = ?,
           alias = ?,
           dosage_value = ?,
           dosage_unit = ?,
           usage_label = ?,
           notes = ?,
           photo_url = ?,
           enable_ocr = ?,
           enable_yolo = ?,
           ocr_endpoint = ?,
           yolo_endpoint = ?,
           enabled = ?,
           updated_at = NOW()
       WHERE id = ? AND user_id = ?`,
      [
        normalized.name,
        normalized.alias,
        normalized.dosageValue,
        normalized.dosageUnit,
        normalized.usage,
        normalized.notes,
        normalized.photoUrl,
        normalized.enableOcr,
        normalized.enableYolo,
        normalized.ocrEndpoint,
        normalized.yoloEndpoint,
        normalized.enabled,
        payload.id,
        userId,
      ],
    )
    return Number(payload.id)
  }

  const [result] = await pool.query(
    `INSERT INTO medications
      (user_id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url, enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())`,
    [
      userId,
      normalized.name,
      normalized.alias,
      normalized.dosageValue,
      normalized.dosageUnit,
      normalized.usage,
      normalized.notes,
      normalized.photoUrl,
      normalized.enableOcr,
      normalized.enableYolo,
      normalized.ocrEndpoint,
      normalized.yoloEndpoint,
      normalized.enabled,
    ],
  )

  return result.insertId
}

const fetchMedicationAlarms = async (userId) => {
  await ensureMedicationTables()

  const [alarmRows] = await pool.query(
    `SELECT id, user_id, alarm_time, enabled, created_at, updated_at
     FROM medication_alarm_groups
     WHERE user_id = ?
     ORDER BY alarm_time ASC, id ASC`,
    [userId],
  )

  if (!alarmRows.length) return []

  const alarmIds = alarmRows.map((row) => row.id)
  const [linkRows] = await pool.query(
    `SELECT id, alarm_id, medication_id, sort_order
     FROM medication_alarm_items
     WHERE alarm_id IN (${alarmIds.map(() => '?').join(',')})
     ORDER BY sort_order ASC, id ASC`,
    alarmIds,
  )

  const medicationIds = [...new Set(linkRows.map((row) => row.medication_id))]
  let medications = []
  if (medicationIds.length) {
    const [rows] = await pool.query(
      `SELECT id,
              user_id,
              name,
              alias,
              dosage_value,
              dosage_unit,
              usage_label,
              notes,
              photo_url,
              enable_ocr,
              enable_yolo,
              ocr_endpoint,
              yolo_endpoint,
              enabled,
              created_at,
              updated_at
       FROM medications
       WHERE user_id = ? AND id IN (${medicationIds.map(() => '?').join(',')})`,
      [userId, ...medicationIds],
    )
    medications = rows
  }

  const medicationMap = new Map(
    medications.map((row) => [
      row.id,
      {
        id: row.id,
        name: row.name,
        alias: row.alias,
        dosageValue: Number(row.dosage_value),
        dosageUnit: row.dosage_unit,
        usage: row.usage_label,
        notes: row.notes ?? '',
        photoUrl: row.photo_url ?? '',
        enableOcr: Boolean(row.enable_ocr),
        enableYolo: Boolean(row.enable_yolo),
        ocrEndpoint: row.ocr_endpoint ?? '',
        yoloEndpoint: row.yolo_endpoint ?? '',
        enabled: Boolean(row.enabled),
      },
    ]),
  )

  const linksByAlarm = new Map()
  linkRows.forEach((row) => {
    if (!linksByAlarm.has(row.alarm_id)) {
      linksByAlarm.set(row.alarm_id, [])
    }
    linksByAlarm.get(row.alarm_id).push(row)
  })

  return alarmRows.map((row) => ({
    id: row.id,
    time: row.alarm_time,
    enabled: Boolean(row.enabled),
    medications: (linksByAlarm.get(row.id) ?? [])
      .map((link) => medicationMap.get(link.medication_id))
      .filter(Boolean),
  }))
}

const syncLegacyReminderRows = async (userId, medicationIds, reminderTime, enabled) => {
  if (!medicationIds.length) return

  await pool.query(
    `DELETE FROM medication_reminders
     WHERE user_id = ?
       AND reminder_time = ?
       AND medication_id IN (${medicationIds.map(() => '?').join(',')})`,
    [userId, reminderTime, ...medicationIds],
  )

  const values = medicationIds.map(() => '(?, ?, ?, ?, NOW())').join(', ')
  const params = medicationIds.flatMap((medicationId) => [medicationId, userId, reminderTime, enabled ? 1 : 0])
  await pool.query(
    `INSERT INTO medication_reminders (medication_id, user_id, reminder_time, enabled, created_at)
     VALUES ${values}`,
    params,
  )

  await pool.query(
    `UPDATE medications
     SET enabled = ?, updated_at = NOW()
     WHERE user_id = ? AND id IN (${medicationIds.map(() => '?').join(',')})`,
    [enabled ? 1 : 0, userId, ...medicationIds],
  )
}

const deleteOrphanMedications = async (userId, medicationIds) => {
  if (!medicationIds.length) return

  const [rows] = await pool.query(
    `SELECT DISTINCT medication_id
     FROM medication_alarm_items
     WHERE medication_id IN (${medicationIds.map(() => '?').join(',')})`,
    medicationIds,
  )
  const referencedIds = new Set(rows.map((row) => row.medication_id))
  const orphanIds = medicationIds.filter((id) => !referencedIds.has(id))
  if (!orphanIds.length) return

  await pool.query(
    `DELETE FROM medication_reminders
     WHERE user_id = ? AND medication_id IN (${orphanIds.map(() => '?').join(',')})`,
    [userId, ...orphanIds],
  )

  await pool.query(
    `DELETE FROM medications
     WHERE user_id = ? AND id IN (${orphanIds.map(() => '?').join(',')})`,
    [userId, ...orphanIds],
  )
}

const fetchMedicationAlarmById = async (userId, alarmId) => {
  const alarms = await fetchMedicationAlarms(userId)
  return alarms.find((alarm) => alarm.id === Number(alarmId)) ?? null
}

const fetchPlanRowsForToday = async (userId) => {
  const [rows] = await pool.query(
    `SELECT rpi.id AS plan_id,
            rpi.done,
            re.id,
            re.name,
            re.category,
            re.duration,
            re.level,
            re.minutes,
            re.steps_json,
            re.caution,
            re.focus,
            re.benefits_json,
            re.video_minutes
     FROM rehab_plan_items rpi
     JOIN rehab_exercises re ON re.id = rpi.exercise_id
     WHERE rpi.user_id = ?
       AND rpi.scheduled_date = CURDATE()
     ORDER BY rpi.id ASC`,
    [userId],
  )

  return rows
}

const fetchPlanRowsByDate = async (date, userId) => {
  const [rows] = await pool.query(
    `SELECT rpi.id AS plan_id,
            rpi.done,
            re.id,
            re.name,
            re.category,
            re.duration,
            re.level,
            re.minutes,
            re.steps_json,
            re.caution,
            re.focus,
            re.benefits_json,
            re.video_minutes
     FROM rehab_plan_items rpi
     JOIN rehab_exercises re ON re.id = rpi.exercise_id
     WHERE rpi.user_id = ?
       AND rpi.scheduled_date = ?
     ORDER BY rpi.id ASC`,
    [userId, date],
  )

  return rows
}

const fetchRehabPlanSettingsRow = async (userId) => {
  await ensureRehabSettingsTable()
  const [[row]] = await pool.query(
    `SELECT focus, frequency, duration, intensity
     FROM rehab_plan_settings
     WHERE user_id = ?
     LIMIT 1`,
    [userId],
  )
  return row ?? null
}

const fetchRehabPlanReminderRow = async (userId) => {
  await ensureRehabPlanReminderTable()
  const [[row]] = await pool.query(
    `SELECT reminder_time, days_json, push_enabled
     FROM rehab_plan_reminders
     WHERE user_id = ?
     LIMIT 1`,
    [userId],
  )
  return row ?? null
}

const buildDerivedPlanSummary = ({ exercises, values }) => {
  const totalMinutes = exercises.reduce((sum, item) => sum + Number(item.minutes || 0), 0)
  const intensity = exercises.some(item => item.level === '进阶') ? '中-高' : '低-中'
  const focus = exercises[0]?.focus ?? '核心稳定'
  const frequencyCount = values.filter(val => val > 0).length

  return {
    focus,
    frequency: `每周 ${frequencyCount || 3} 次`,
    duration: totalMinutes ? `单次 ${totalMinutes} 分钟` : '单次 20-30 分钟',
    intensity,
  }
}

const buildPlanReminderSummary = (reminderRow) => {
  const reminderDays = reminderRow ? parseJsonArray(reminderRow.days_json) : []
  const reminderDayLabels = reminderDays.map(day => dayLabelMap[day]).filter(Boolean)
  const reminderEnabled = reminderRow ? Boolean(reminderRow.push_enabled) : false

  return {
    time: reminderRow?.reminder_time ?? '--:--',
    days: reminderDayLabels.length ? reminderDayLabels.join(' / ') : '未选择日期',
    channel: reminderEnabled ? '系统通知' : '未开启',
    status: reminderEnabled ? '已开启' : '未设置',
  }
}

const seedPlanFromExercises = async (exerciseIds, userId) => {
  if (!exerciseIds.length) return
  const values = exerciseIds.map(() => '(?, ?, CURDATE(), 0)').join(',')
  const params = exerciseIds.flatMap((id) => [userId, id])
  await pool.query(
    `INSERT INTO rehab_plan_items (user_id, exercise_id, scheduled_date, done)
     VALUES ${values}`,
    params,
  )
}

const ensureTodayPlanRows = async (userId, { autoSeedIfEmpty = true } = {}) => {
  let rows = await fetchPlanRowsForToday(userId)
  if (rows.length) return rows
  if (!autoSeedIfEmpty) return rows

  const [[latestRow]] = await pool.query(
    `SELECT MAX(scheduled_date) AS latest_date
     FROM rehab_plan_items
     WHERE user_id = ?`,
    [userId],
  )

  if (latestRow?.latest_date) {
    const [latestItems] = await pool.query(
      `SELECT exercise_id
       FROM rehab_plan_items
       WHERE user_id = ? AND scheduled_date = ?`,
      [userId, latestRow.latest_date],
    )
    const exerciseIds = latestItems.map(item => item.exercise_id)
    await seedPlanFromExercises(exerciseIds, userId)
  } else {
    const [exerciseRows] = await pool.query(
      `SELECT id
       FROM rehab_exercises
       WHERE user_id IS NULL
       ORDER BY id ASC
       LIMIT 4`,
    )
    const exerciseIds = exerciseRows.map(row => row.id)
    await seedPlanFromExercises(exerciseIds, userId)
  }

  rows = await fetchPlanRowsForToday(userId)
  if (!rows.length && latestRow?.latest_date) {
    rows = await fetchPlanRowsByDate(latestRow.latest_date, userId)
  }
  return rows
}

const fetchRehabPlan = async (userId, options = {}) => {
  const rows = await ensureTodayPlanRows(userId, options)

  const exercises = rows.map((row) => ({
    id: row.plan_id,
    name: row.name,
    category: row.category,
    duration: row.duration,
    level: row.level,
    minutes: Number(row.minutes),
    steps: parseJsonArray(row.steps_json),
    caution: row.caution,
    focus: row.focus,
    benefits: parseJsonArray(row.benefits_json),
    videoMinutes: Number(row.video_minutes),
    done: Boolean(row.done),
  }))

  const [trendRows] = await pool.query(
    `SELECT DATE_FORMAT(stat_date, '%m-%d') AS label,
            minutes
     FROM rehab_week_stats
     WHERE user_id = ?
       AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
     ORDER BY stat_date ASC`,
    [userId],
  )

  const labels = trendRows.map(row => row.label)
  const values = trendRows.map(row => Number(row.minutes))
  const deltaPercent = buildWeekDelta(values)
  const settingsRow = await fetchRehabPlanSettingsRow(userId)
  const reminderRow = await fetchRehabPlanReminderRow(userId)
  const derivedSummary = buildDerivedPlanSummary({ exercises, values })

  return {
    label: '今日计划',
    exercises,
    weekTrend: {
      labels,
      values,
      insight: values.length
        ? '本周训练完成度整体提升，建议在保证动作质量的前提下逐步增加组数；如出现放射性疼痛、麻木无力请及时就医。'
        : '暂无训练趋势数据。',
      deltaPercent,
    },
    planSummary: settingsRow
      ? {
          focus: settingsRow.focus,
          frequency: settingsRow.frequency,
          duration: settingsRow.duration,
          intensity: settingsRow.intensity,
        }
      : derivedSummary,
    reminderSummary: buildPlanReminderSummary(reminderRow),
  }
}

const findRehabExerciseByName = async (name, userId) => {
  const [rows] = await pool.query(
    `SELECT *
     FROM rehab_exercises
     WHERE name = ?
       AND (user_id = ? OR user_id IS NULL)
     ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END,
              id ASC
     LIMIT 1`,
    [name, userId, userId],
  )
  return rows?.[0] ?? null
}

const getNextRehabExerciseId = async () => {
  const [[row]] = await pool.query(
    `SELECT COALESCE(MAX(id), 0) + 1 AS nextId
     FROM rehab_exercises`,
  )
  return Number(row?.nextId ?? 1)
}

const createUserScopedExercise = async (exercise, userId) => {
  const nextId = await getNextRehabExerciseId()
  const [result] = await pool.query(
    `INSERT INTO rehab_exercises
       (id, user_id, name, category, duration, level, minutes, steps_json, caution, focus, benefits_json, video_minutes)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      nextId,
      userId,
      exercise.name,
      exercise.category,
      exercise.duration,
      exercise.level,
      exercise.minutes,
      JSON.stringify(exercise.steps),
      exercise.caution,
      exercise.focus,
      JSON.stringify(exercise.benefits),
      exercise.videoMinutes,
    ],
  )
  return Number(result.insertId || nextId)
}

const resolveDraftExerciseIds = async ({ exercises, userId }) => {
  const resolvedIds = []

  for (const exercise of exercises) {
    const existing = await findRehabExerciseByName(exercise.name, userId)
    if (existing) {
      resolvedIds.push(existing.id)
      continue
    }

    const insertedId = await createUserScopedExercise(exercise, userId)
    resolvedIds.push(insertedId)
  }

  return resolvedIds
}

const replaceTodayPlanItems = async ({ userId, exerciseIds }) => {
  await pool.query(`DELETE FROM rehab_plan_items WHERE user_id = ? AND scheduled_date = CURDATE()`, [userId])
  if (!exerciseIds.length) return

  const values = exerciseIds.map(() => '(?, ?, CURDATE(), 0)').join(',')
  const params = exerciseIds.flatMap((id) => [userId, id])
  await pool.query(
    `INSERT INTO rehab_plan_items (user_id, exercise_id, scheduled_date, done)
     VALUES ${values}`,
    params,
  )
}

const saveRehabPlanSummary = async ({ userId, summary }) => {
  await ensureRehabSettingsTable()
  await pool.query(
    `INSERT INTO rehab_plan_settings
       (user_id, focus, frequency, duration, intensity, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, NOW(), NOW())
     ON DUPLICATE KEY UPDATE
       focus = VALUES(focus),
       frequency = VALUES(frequency),
       duration = VALUES(duration),
       intensity = VALUES(intensity),
       updated_at = NOW()`,
    [userId, summary.focus, summary.frequency, summary.duration, summary.intensity],
  )
}

const saveRehabPlanReminder = async ({ userId, reminder }) => {
  await ensureRehabPlanReminderTable()
  await pool.query(
    `INSERT INTO rehab_plan_reminders
       (user_id, reminder_time, days_json, push_enabled, created_at, updated_at)
     VALUES (?, ?, ?, ?, NOW(), NOW())
     ON DUPLICATE KEY UPDATE
       reminder_time = VALUES(reminder_time),
       days_json = VALUES(days_json),
       push_enabled = VALUES(push_enabled),
       updated_at = NOW()`,
    [userId, reminder.time, JSON.stringify(reminder.days), reminder.pushEnabled ? 1 : 0],
  )
}

app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok' })
})

app.post('/api/auth/register', async (req, res) => {
  const payload = req.body ?? {}
  const name = String(payload.name ?? '').trim()
  const email = String(payload.email ?? '').trim().toLowerCase()
  const password = String(payload.password ?? '')

  if (!name || !email || !password) {
    res.status(400).json({ message: '姓名、邮箱、密码均为必填项' })
    return
  }

  if (password.length < 6) {
    res.status(400).json({ message: '密码长度至少 6 位' })
    return
  }

  try {
    const [[existsRow]] = await pool.query(
      `SELECT id FROM auth_users WHERE email = ? LIMIT 1`,
      [email],
    )
    if (existsRow) {
      res.status(409).json({ message: '该邮箱已注册，请直接登录' })
      return
    }

    const [result] = await pool.query(
      `INSERT INTO auth_users (name, email, password_hash, created_at)
       VALUES (?, ?, ?, NOW())`,
      [name, email, hashPassword(password)],
    )

    const userId = result.insertId
    await ensureUserProfile(userId, name, email)

    const token = generateToken()
    await pool.query(
      `INSERT INTO auth_sessions (token, user_id, created_at, last_active)
       VALUES (?, ?, NOW(), NOW())`,
      [token, userId],
    )

    const user = await fetchAuthUser(userId)
    res.json({ token, user })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '注册失败' })
  }
})

app.post('/api/auth/login', async (req, res) => {
  const payload = req.body ?? {}
  const email = String(payload.email ?? '').trim().toLowerCase()
  const password = String(payload.password ?? '')

  if (!email || !password) {
    res.status(400).json({ message: '邮箱和密码均为必填项' })
    return
  }

  try {
    const [[row]] = await pool.query(
      `SELECT id, name, password_hash
       FROM auth_users
       WHERE email = ?
       LIMIT 1`,
      [email],
    )

    if (!row || row.password_hash !== hashPassword(password)) {
      res.status(400).json({ message: '邮箱或密码不正确' })
      return
    }

    await ensureUserProfile(row.id, row.name ?? email, email)

    const token = generateToken()
    await pool.query(
      `INSERT INTO auth_sessions (token, user_id, created_at, last_active)
       VALUES (?, ?, NOW(), NOW())`,
      [token, row.id],
    )

    const user = await fetchAuthUser(row.id)
    res.json({ token, user })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '登录失败' })
  }
})

app.get('/api/auth/me', async (req, res) => {
  const token = getAuthToken(req)
  if (!token) {
    res.status(401).json({ message: '未登录' })
    return
  }

  try {
    const [[row]] = await pool.query(
      `SELECT user_id
       FROM auth_sessions
       WHERE token = ?
       LIMIT 1`,
      [token],
    )

    if (!row) {
      res.status(401).json({ message: '登录已失效，请重新登录' })
      return
    }

    const user = await fetchAuthUser(row.user_id)
    res.json({ user })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '获取用户信息失败' })
  }
})

app.post('/api/auth/logout', async (req, res) => {
  const token = getAuthToken(req)
  if (!token) {
    res.json({ success: true })
    return
  }
  try {
    await pool.query('DELETE FROM auth_sessions WHERE token = ?', [token])
    res.json({ success: true })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '退出失败' })
  }
})

app.post('/api/consult/questions', async (req, res) => {
  try {
    const result = await askConsultByQwen(req.body ?? {})
    res.json(result)
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '智能助手调用失败' })
  }
})

app.post('/api/consult/stream', async (req, res) => {
  res.setHeader('Content-Type', 'application/x-ndjson; charset=utf-8')
  res.setHeader('Cache-Control', 'no-cache, no-transform')
  res.setHeader('Connection', 'keep-alive')
  res.setHeader('X-Accel-Buffering', 'no')
  if (typeof res.flushHeaders === 'function') {
    res.flushHeaders()
  }

  try {
    res.write(`${JSON.stringify({ type: 'chunk', delta: '正在分析你的问题，请稍候…' })}\n`)
    const result = await askConsultByQwen(req.body ?? {})

    await writeStreamingAnswer(result, (delta) => {
      res.write(`${JSON.stringify({ type: 'chunk', delta })}\n`)
    })

    res.write(`${JSON.stringify({ type: 'complete', ...result })}\n`)
    res.end()
  } catch (error) {
    const message = error.message ?? '智能助手调用失败'
    if (!res.headersSent) {
      res.status(error.statusCode ?? 500).json({ message })
      return
    }

    res.write(`${JSON.stringify({ type: 'error', message })}\n`)
    res.end()
  }
})

app.post('/api/medications/recognize', upload.array('files', 8), async (req, res) => {
  if (!req.files?.length) {
    res.status(400).json({ message: '请先上传药盒图片' })
    return
  }

  try {
    const result = await recognizeMedicationByQwenV2(req.files)
    console.info('[api/medications/recognize] result', JSON.stringify(result))
    res.json(result)
  } catch (error) {
    console.error('[api/medications/recognize] error', error)
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '药盒识别失败' })
  }
})

app.post('/api/medications/recognize/custom-model', upload.array('files', 8), async (req, res) => {
  try {
    const result = await forwardMultipartToCustomModel({
      url: customMedicationRecognizeUrl,
      files: Array.isArray(req.files) ? req.files : [],
      fields: {
        scene: 'medication_recognition',
      },
    })
    res.json(result)
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '自定义药品识别接口调用失败' })
  }
})

app.get('/api/medication-alarms', async (req, res) => {
  try {
    const alarms = await fetchMedicationAlarms(req.userId)
    res.json(alarms)
  } catch (error) {
    res.status(500).json({ message: error.message ?? '鐢ㄨ嵂闂归挓璇诲彇澶辫触' })
  }
})

app.post('/api/medication-alarms', async (req, res) => {
  const payload = req.body ?? {}
  const alarmTime = String(payload.time ?? '').trim()
  const medications = Array.isArray(payload.medications) ? payload.medications : []

  if (!/^\d{2}:\d{2}$/.test(alarmTime)) {
    res.status(400).json({ message: '鎻愰啋鏃堕棿鏍煎紡鏃犳晥' })
    return
  }

  if (!medications.length) {
    res.status(400).json({ message: '鑷冲皯闇€瑕佷竴绉嶈嵂鍝?' })
    return
  }

  try {
    await ensureMedicationTables()
    const [[duplicate]] = await pool.query(
      `SELECT id
       FROM medication_alarm_groups
       WHERE user_id = ? AND alarm_time = ?
       LIMIT 1`,
      [req.userId, alarmTime],
    )

    if (duplicate) {
      res.status(409).json({ message: '璇ユ椂闂寸殑闂归挓宸插瓨鍦?' })
      return
    }

    const medicationIds = []
    for (const item of medications) {
      const medicationId = await upsertMedicationRecord(req.userId, item)
      medicationIds.push(medicationId)
    }

    const [result] = await pool.query(
      `INSERT INTO medication_alarm_groups (user_id, alarm_time, enabled, created_at, updated_at)
       VALUES (?, ?, ?, NOW(), NOW())`,
      [req.userId, alarmTime, payload.enabled === false ? 0 : 1],
    )

    const alarmId = result.insertId
    if (medicationIds.length) {
      const values = medicationIds.map(() => '(?, ?, ?, NOW())').join(', ')
      const params = medicationIds.flatMap((medicationId, index) => [alarmId, medicationId, index])
      await pool.query(
        `INSERT INTO medication_alarm_items (alarm_id, medication_id, sort_order, created_at)
         VALUES ${values}`,
        params,
      )
      await syncLegacyReminderRows(req.userId, medicationIds, alarmTime, payload.enabled !== false)
    }

    const alarm = await fetchMedicationAlarmById(req.userId, alarmId)
    res.status(201).json(alarm)
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '鐢ㄨ嵂闂归挓淇濆瓨澶辫触' })
  }
})

app.put('/api/medication-alarms/:id', async (req, res) => {
  const payload = req.body ?? {}
  const alarmId = Number(req.params.id)
  const alarmTime = String(payload.time ?? '').trim()
  const medications = Array.isArray(payload.medications) ? payload.medications : []

  if (!alarmId) {
    res.status(400).json({ message: '闂归挓 ID 鏃犳晥' })
    return
  }

  if (!/^\d{2}:\d{2}$/.test(alarmTime)) {
    res.status(400).json({ message: '鎻愰啋鏃堕棿鏍煎紡鏃犳晥' })
    return
  }

  if (!medications.length) {
    res.status(400).json({ message: '鑷冲皯闇€瑕佷竴绉嶈嵂鍝?' })
    return
  }

  try {
    await ensureMedicationTables()
    const existing = await fetchMedicationAlarmById(req.userId, alarmId)
    if (!existing) {
      res.status(404).json({ message: '闂归挓涓嶅瓨鍦?' })
      return
    }

    const [[duplicate]] = await pool.query(
      `SELECT id
       FROM medication_alarm_groups
       WHERE user_id = ? AND alarm_time = ? AND id <> ?
       LIMIT 1`,
      [req.userId, alarmTime, alarmId],
    )

    if (duplicate) {
      res.status(409).json({ message: '璇ユ椂闂寸殑闂归挓宸插瓨鍦?' })
      return
    }

    const previousMedicationIds = existing.medications
      .map((item) => Number(item.id))
      .filter((item) => Number.isFinite(item))

    const medicationIds = []
    for (const item of medications) {
      const medicationId = await upsertMedicationRecord(req.userId, item)
      medicationIds.push(medicationId)
    }

    await pool.query(
      `UPDATE medication_alarm_groups
       SET alarm_time = ?, enabled = ?, updated_at = NOW()
       WHERE id = ? AND user_id = ?`,
      [alarmTime, payload.enabled === false ? 0 : 1, alarmId, req.userId],
    )

    await pool.query(`DELETE FROM medication_alarm_items WHERE alarm_id = ?`, [alarmId])

    if (medicationIds.length) {
      const values = medicationIds.map(() => '(?, ?, ?, NOW())').join(', ')
      const params = medicationIds.flatMap((medicationId, index) => [alarmId, medicationId, index])
      await pool.query(
        `INSERT INTO medication_alarm_items (alarm_id, medication_id, sort_order, created_at)
         VALUES ${values}`,
        params,
      )
    }

    if (previousMedicationIds.length) {
      await pool.query(
        `DELETE FROM medication_reminders
         WHERE user_id = ?
           AND reminder_time = ?
           AND medication_id IN (${previousMedicationIds.map(() => '?').join(',')})`,
        [req.userId, existing.time, ...previousMedicationIds],
      )
    }

    if (medicationIds.length) {
      await syncLegacyReminderRows(req.userId, medicationIds, alarmTime, payload.enabled !== false)
    }

    const removedMedicationIds = previousMedicationIds.filter(
      (medicationId) => !medicationIds.includes(medicationId),
    )
    await deleteOrphanMedications(req.userId, removedMedicationIds)

    const alarm = await fetchMedicationAlarmById(req.userId, alarmId)
    res.json(alarm)
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '鐢ㄨ嵂闂归挓鏇存柊澶辫触' })
  }
})

app.post('/api/medication-alarms/:id/toggle', async (req, res) => {
  const alarmId = Number(req.params.id)

  if (!alarmId) {
    res.status(400).json({ message: '闂归挓 ID 鏃犳晥' })
    return
  }

  try {
    const alarm = await fetchMedicationAlarmById(req.userId, alarmId)
    if (!alarm) {
      res.status(404).json({ message: '闂归挓涓嶅瓨鍦?' })
      return
    }

    const nextEnabled = alarm.enabled ? 0 : 1
    await pool.query(
      `UPDATE medication_alarm_groups
       SET enabled = ?, updated_at = NOW()
       WHERE id = ? AND user_id = ?`,
      [nextEnabled, alarmId, req.userId],
    )

    const medicationIds = alarm.medications
      .map((item) => Number(item.id))
      .filter((item) => Number.isFinite(item))
    await syncLegacyReminderRows(req.userId, medicationIds, alarm.time, Boolean(nextEnabled))

    res.json({ id: alarmId, enabled: Boolean(nextEnabled) })
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '鐘舵€佹洿鏂板け璐?' })
  }
})

app.delete('/api/medication-alarms/:id', async (req, res) => {
  const alarmId = Number(req.params.id)

  if (!alarmId) {
    res.status(400).json({ message: '闂归挓 ID 鏃犳晥' })
    return
  }

  try {
    const alarm = await fetchMedicationAlarmById(req.userId, alarmId)
    if (!alarm) {
      res.status(404).json({ message: '闂归挓涓嶅瓨鍦?' })
      return
    }

    const medicationIds = alarm.medications
      .map((item) => Number(item.id))
      .filter((item) => Number.isFinite(item))

    if (medicationIds.length) {
      await pool.query(
        `DELETE FROM medication_reminders
         WHERE user_id = ?
           AND reminder_time = ?
           AND medication_id IN (${medicationIds.map(() => '?').join(',')})`,
        [req.userId, alarm.time, ...medicationIds],
      )
    }

    await pool.query(`DELETE FROM medication_alarm_items WHERE alarm_id = ?`, [alarmId])
    await pool.query(`DELETE FROM medication_alarm_groups WHERE id = ? AND user_id = ?`, [
      alarmId,
      req.userId,
    ])

    await deleteOrphanMedications(req.userId, medicationIds)
    res.json({ success: true })
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '鍒犻櫎澶辫触' })
  }
})

app.get('/api/medications', async (req, res) => {
  try {
    await ensureMedicationTables()
  const [rows] = await pool.query(
    `SELECT id,
            user_id,
            name,
            alias,
            dosage_value,
            dosage_unit,
            usage_label,
            notes,
            photo_url,
            enable_ocr,
            enable_yolo,
            ocr_endpoint,
            yolo_endpoint,
            enabled,
            created_at,
            updated_at
     FROM medications
     WHERE user_id = ?
     ORDER BY updated_at DESC`,
      [req.userId],
    )

    const medicationIds = rows.map(row => row.id)
    let reminderRows = []
    if (medicationIds.length) {
      const [reminderData] = await pool.query(
        `SELECT *
         FROM medication_reminders
         WHERE user_id = ? AND medication_id IN (${medicationIds.map(() => '?').join(',')})
         ORDER BY reminder_time ASC`,
        [req.userId, ...medicationIds],
      )
      reminderRows = reminderData
    }

    res.json(mapMedicationRows(rows, reminderRows))
  } catch (error) {
    res.status(500).json({ message: error.message ?? '药品列表读取失败' })
  }
})

app.post('/api/medications', async (req, res) => {
  const payload = req.body ?? {}
  const name = String(payload.name ?? '').trim()
  if (!name) {
    res.status(400).json({ message: '药品名称不能为空' })
    return
  }

  try {
    await ensureMedicationTables()
    const [result] = await pool.query(
      `INSERT INTO medications
        (user_id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url, enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())`,
      [
        req.userId,
        name,
        payload.alias ?? '',
        Number(payload.dosageValue ?? 1),
        payload.dosageUnit ?? '片',
        payload.usage ?? '饭后',
        payload.notes ?? '',
        payload.photoUrl ?? '',
        payload.enableOcr ? 1 : 0,
        payload.enableYolo ? 1 : 0,
        payload.ocrEndpoint ?? '',
        payload.yoloEndpoint ?? '',
        payload.enabled === false ? 0 : 1,
      ],
    )

    const medicationId = result.insertId
    const reminders = Array.isArray(payload.reminders) ? payload.reminders : []
    if (reminders.length) {
      const values = reminders.map(() => '(?, ?, ?, ?, NOW())').join(',')
      const params = reminders.flatMap(item => [
        medicationId,
        req.userId,
        item.time,
        item.enabled === false ? 0 : 1,
      ])
      await pool.query(
        `INSERT INTO medication_reminders (medication_id, user_id, reminder_time, enabled, created_at)
         VALUES ${values}`,
        params,
      )
    }

    const [rows] = await pool.query(
      `SELECT id,
              user_id,
              name,
              alias,
              dosage_value,
              dosage_unit,
              usage_label,
              notes,
              photo_url,
              enable_ocr,
              enable_yolo,
              ocr_endpoint,
              yolo_endpoint,
              enabled,
              created_at,
              updated_at
       FROM medications
       WHERE id = ? AND user_id = ?`,
      [medicationId, req.userId],
    )
    const [reminderRows] = await pool.query(
      `SELECT *
       FROM medication_reminders
       WHERE medication_id = ? AND user_id = ?
       ORDER BY reminder_time ASC`,
      [medicationId, req.userId],
    )

    res.json(mapMedicationRows(rows, reminderRows)[0])
  } catch (error) {
    res.status(500).json({ message: error.message ?? '药品保存失败' })
  }
})

app.put('/api/medications/:id', async (req, res) => {
  const payload = req.body ?? {}
  const name = String(payload.name ?? '').trim()
  if (!name) {
    res.status(400).json({ message: '药品名称不能为空' })
    return
  }
  try {
    await ensureMedicationTables()
    await pool.query(
      `UPDATE medications
       SET name = ?,
           alias = ?,
           dosage_value = ?,
           dosage_unit = ?,
           usage_label = ?,
           notes = ?,
           photo_url = ?,
           enable_ocr = ?,
           enable_yolo = ?,
           ocr_endpoint = ?,
           yolo_endpoint = ?,
           enabled = ?,
           updated_at = NOW()
       WHERE id = ? AND user_id = ?`,
      [
        name,
        payload.alias ?? '',
        Number(payload.dosageValue ?? 1),
        payload.dosageUnit ?? '片',
        payload.usage ?? '饭后',
        payload.notes ?? '',
        payload.photoUrl ?? '',
        payload.enableOcr ? 1 : 0,
        payload.enableYolo ? 1 : 0,
        payload.ocrEndpoint ?? '',
        payload.yoloEndpoint ?? '',
        payload.enabled === false ? 0 : 1,
        req.params.id,
        req.userId,
      ],
    )

    await pool.query(
      `DELETE FROM medication_reminders WHERE medication_id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )

    const reminders = Array.isArray(payload.reminders) ? payload.reminders : []
    if (reminders.length) {
      const values = reminders.map(() => '(?, ?, ?, ?, NOW())').join(',')
      const params = reminders.flatMap(item => [
        req.params.id,
        req.userId,
        item.time,
        item.enabled === false ? 0 : 1,
      ])
      await pool.query(
        `INSERT INTO medication_reminders (medication_id, user_id, reminder_time, enabled, created_at)
         VALUES ${values}`,
        params,
      )
    }

    const [rows] = await pool.query(
      `SELECT id,
              user_id,
              name,
              alias,
              dosage_value,
              dosage_unit,
              usage_label,
              notes,
              photo_url,
              enable_ocr,
              enable_yolo,
              ocr_endpoint,
              yolo_endpoint,
              enabled,
              created_at,
              updated_at
       FROM medications
       WHERE id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )
    const [reminderRows] = await pool.query(
      `SELECT *
       FROM medication_reminders
       WHERE medication_id = ? AND user_id = ?
       ORDER BY reminder_time ASC`,
      [req.params.id, req.userId],
    )

    res.json(mapMedicationRows(rows, reminderRows)[0])
  } catch (error) {
    res.status(500).json({ message: error.message ?? '药品更新失败' })
  }
})

app.post('/api/medications/:id/toggle', async (req, res) => {
  try {
    await ensureMedicationTables()
    const [[row]] = await pool.query(
      `SELECT enabled FROM medications WHERE id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )
    if (!row) {
      res.status(404).json({ message: '药品不存在' })
      return
    }
    const nextEnabled = row.enabled ? 0 : 1
    await pool.query(
      `UPDATE medications SET enabled = ?, updated_at = NOW() WHERE id = ? AND user_id = ?`,
      [nextEnabled, req.params.id, req.userId],
    )
    res.json({ id: Number(req.params.id), enabled: Boolean(nextEnabled) })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '状态更新失败' })
  }
})

app.delete('/api/medications/:id', async (req, res) => {
  try {
    await ensureMedicationTables()
    await pool.query(
      `DELETE FROM medication_reminders WHERE medication_id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )
    await pool.query(
      `DELETE FROM medications WHERE id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )
    res.json({ success: true })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '删除失败' })
  }
})

const mapDeviceRow = (row) => ({
  id: Number(row.id),
  name: row.name || row.label || '未命名设备',
  brand: row.brand || '',
  model: row.model || '',
  type: row.device_type || 'other',
  connected: Boolean(row.connected),
  battery: Number(row.battery ?? 100),
  lastSyncAt: row.last_sync_at?.toISOString?.() ?? row.last_sync_at ?? '',
})

app.get('/api/devices', async (req, res) => {
  try {
    await ensureDeviceTables()
    const [rows] = await pool.query(
      `SELECT id, label, name, brand, model, device_type, connected, battery, last_sync_at
       FROM devices
       WHERE user_id = ?
       ORDER BY id DESC`,
      [req.userId],
    )

    res.json(rows.map(mapDeviceRow))
  } catch (error) {
    res.status(500).json({ message: error.message ?? '设备读取失败' })
  }
})

app.post('/api/devices', async (req, res) => {
  const payload = req.body ?? {}
  const brand = String(payload.brand ?? '').trim()
  const model = String(payload.model ?? '').trim()
  const name = String(payload.name ?? '').trim() || `${brand} ${model}`.trim() || '未命名设备'
  const type = ['watch', 'band', 'ring', 'other'].includes(String(payload.type ?? ''))
    ? String(payload.type)
    : 'other'

  try {
    await ensureDeviceTables()
    const [result] = await pool.query(
      `INSERT INTO devices (user_id, label, name, brand, model, device_type, connected, battery, last_sync_at)
       VALUES (?, ?, ?, ?, ?, ?, 1, 100, NOW())`,
      [req.userId, name, name, brand, model, type],
    )

    const [[row]] = await pool.query(
      `SELECT id, label, name, brand, model, device_type, connected, battery, last_sync_at
       FROM devices
       WHERE id = ? AND user_id = ?
       LIMIT 1`,
      [result.insertId, req.userId],
    )

    res.json(mapDeviceRow(row))
  } catch (error) {
    res.status(500).json({ message: error.message ?? '设备创建失败' })
  }
})

app.post('/api/devices/:id/sync', async (req, res) => {
  const id = Number(req.params.id)
  if (!id) {
    res.status(400).json({ message: '设备 ID 无效' })
    return
  }

  try {
    await ensureDeviceTables()
    const [[existing]] = await pool.query(
      `SELECT id, battery
       FROM devices
       WHERE id = ? AND user_id = ?
       LIMIT 1`,
      [id, req.userId],
    )

    if (!existing) {
      res.status(404).json({ message: '设备不存在' })
      return
    }

    const currentBattery = Number(existing.battery ?? 100)
    const nextBattery = Math.max(12, Math.min(100, currentBattery - Math.floor(Math.random() * 4)))

    await pool.query(
      `UPDATE devices
       SET connected = 1,
           battery = ?,
           last_sync_at = NOW()
       WHERE id = ? AND user_id = ?`,
      [nextBattery, id, req.userId],
    )

    const [[row]] = await pool.query(
      `SELECT id, label, name, brand, model, device_type, connected, battery, last_sync_at
       FROM devices
       WHERE id = ? AND user_id = ?
       LIMIT 1`,
      [id, req.userId],
    )

    res.json(mapDeviceRow(row))
  } catch (error) {
    res.status(500).json({ message: error.message ?? '设备同步失败' })
  }
})

app.delete('/api/devices/:id', async (req, res) => {
  const id = Number(req.params.id)
  if (!id) {
    res.status(400).json({ message: '设备 ID 无效' })
    return
  }

  try {
    await ensureDeviceTables()
    const [result] = await pool.query(
      `DELETE FROM devices
       WHERE id = ? AND user_id = ?`,
      [id, req.userId],
    )

    if (!result.affectedRows) {
      res.status(404).json({ message: '设备不存在' })
      return
    }

    res.json({ success: true })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '设备删除失败' })
  }
})

app.get('/api/home/summary', async (req, res) => {
  try {
    const [rows] = await pool.query(
      `SELECT hs.*,
              up.name AS user_name
       FROM user_profiles up
       LEFT JOIN home_summary hs ON hs.user_id = up.id
       WHERE up.id = ?
       ORDER BY hs.summary_date DESC
       LIMIT 1`,
      [req.userId],
    )

    const row = rows?.[0]
    if (!row || !row.summary_date) {
      res.json({
        userName: row?.user_name ?? '未命名用户',
        healthScore: 0,
        statusBadge: '待同步',
        statusBadgeVariant: 'info',
        statusSummary: '暂无同步数据',
        stepsTarget: 0,
        stepsNow: 0,
        keyMetrics: [],
        suggestions: [],
      })
      return
    }

    res.json({
      userName: row.user_name,
      healthScore: Number(row.health_score),
      statusBadge: row.status_badge,
      statusBadgeVariant: row.status_badge_variant,
      statusSummary: row.status_summary,
      stepsTarget: Number(row.steps_target),
      stepsNow: Number(row.steps_now),
      keyMetrics: [
        {
          key: 'hr',
          value: Number(row.hr_value),
          badge: row.hr_badge,
          badgeVariant: row.hr_badge_variant,
          hint: row.hr_hint,
        },
        {
          key: 'stress',
          value: Number(row.stress_value),
          badge: row.stress_badge,
          badgeVariant: row.stress_badge_variant,
          hint: row.stress_hint,
        },
        {
          key: 'hydration',
          value: Number(row.hydration_ml),
          badge: row.hydration_badge,
          badgeVariant: row.hydration_badge_variant,
          hint: row.hydration_hint,
        },
      ],
      suggestions: [row.suggestion_1, row.suggestion_2, row.suggestion_3].filter(Boolean),
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '数据读取失败' })
  }
})

app.get('/api/profile/summary', async (req, res) => {
  try {
    const [[profile]] = await pool.query(
      `SELECT risk_score, risk_level
       FROM user_profiles
       WHERE id = ?`,
      [req.userId],
    )

    const [[deviceRow]] = await pool.query(
      `SELECT COUNT(*) AS count
       FROM devices
       WHERE user_id = ?`,
      [req.userId],
    )

    const [[uploadRow]] = await pool.query(
      `SELECT COUNT(*) AS count
       FROM analyze_tasks
       WHERE user_id = ?`,
      [req.userId],
    )

    res.json({
      devices: `${deviceRow.count} 台（设备）`,
      uploads: `${uploadRow.count} 份`,
      riskScore: profile ? `${profile.risk_score} · ${profile.risk_level}` : '0 · 未知',
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '数据读取失败' })
  }
})

app.get('/api/profile/settings', async (req, res) => {
  try {
    const [[row]] = await pool.query(
      `SELECT up.name,
              up.email,
              us.age,
              us.gender,
              us.height,
              us.weight,
              us.focus,
              us.goals_json,
              us.daily_summary,
              us.risk_alert,
              us.rehab_reminder
       FROM user_profiles up
       JOIN user_settings us ON us.user_id = up.id
       WHERE up.id = ?`,
      [req.userId],
    )

    res.json({
      name: row.name,
      email: row.email,
      age: Number(row.age),
      gender: row.gender,
      height: Number(row.height),
      weight: Number(row.weight),
      focus: row.focus ?? '',
      goals: parseJsonArray(row.goals_json),
      dailySummary: Boolean(row.daily_summary),
      riskAlert: Boolean(row.risk_alert),
      rehabReminder: Boolean(row.rehab_reminder),
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '数据读取失败' })
  }
})

app.post('/api/profile/settings', async (req, res) => {
  const payload = req.body ?? {}
  const goals = Array.isArray(payload.goals) ? payload.goals : []
  const gender = ['male', 'female', 'other'].includes(payload.gender) ? payload.gender : 'other'

  try {
    await pool.query(
      `UPDATE user_profiles
       SET name = ?, email = ?
       WHERE id = ?`,
      [payload.name, payload.email, req.userId],
    )

    await pool.query(
      `UPDATE user_settings
       SET age = ?,
           gender = ?,
           height = ?,
           weight = ?,
           focus = ?,
           goals_json = ?,
           daily_summary = ?,
           risk_alert = ?,
           rehab_reminder = ?
       WHERE user_id = ?`,
      [
        Number(payload.age),
        gender,
        Number(payload.height),
        Number(payload.weight),
        payload.focus ?? '',
        JSON.stringify(goals),
        payload.dailySummary ? 1 : 0,
        payload.riskAlert ? 1 : 0,
        payload.rehabReminder ? 1 : 0,
        req.userId,
      ],
    )

    await pool.query(
      `UPDATE auth_users
       SET name = ?, email = ?
       WHERE id = ?`,
      [payload.name, payload.email, req.userId],
    )

    res.json({
      ...payload,
      gender,
      goals,
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '保存失败' })
  }
})

app.post('/api/profile/avatar', async (req, res) => {
  const { avatarUrl } = req.body ?? {}
  if (!avatarUrl) {
    res.status(400).json({ message: 'avatarUrl 不能为空' })
    return
  }
  try {
    await pool.query('UPDATE user_profiles SET avatar_url = ? WHERE id = ?', [avatarUrl, req.userId])
    res.json({ success: true })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '头像更新失败' })
  }
})

app.post('/api/analyze/tasks', upload.array('files', 8), async (req, res) => {
  const type = req.body?.type ?? 'text'
  const text = req.body?.text ?? ''
  const files = Array.isArray(req.files) ? req.files : []
  const fileName = files.map((file) => file.originalname).join(' | ')
  const taskId = `task_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

  try {
    const report = await analyzeUploadByQwenV2({ type, text, files })

    await pool.query(
      `INSERT INTO analyze_tasks
        (id, user_id, type, file_name, text_content, status, points_json, advice_json, report_json, saved, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 'DONE', ?, ?, ?, 0, NOW(), NOW())`,
      [
        taskId,
        req.userId,
        type,
        fileName,
        text,
        JSON.stringify(report.points),
        JSON.stringify(report.advice),
        JSON.stringify(report),
      ],
    )

    res.json({ taskId })
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '分析任务创建失败' })
  }
})

app.post('/api/analyze/tasks/custom-model', upload.array('files', 8), async (req, res) => {
  try {
    const result = await forwardMultipartToCustomModel({
      url: customUploadAnalyzeUrl,
      files: Array.isArray(req.files) ? req.files : [],
      fields: {
        type: req.body?.type ?? '',
        text: req.body?.text ?? '',
      },
    })

    res.json(result)
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '自定义分析接口调用失败' })
  }
})

app.get('/api/analyze/tasks/:taskId', async (req, res) => {
  try {
    const [[row]] = await pool.query(
      `SELECT type, status, points_json, advice_json, report_json, saved
       FROM analyze_tasks
       WHERE id = ? AND user_id = ?`,
      [req.params.taskId, req.userId],
    )

    if (!row) {
      res.status(404).json({ message: '任务不存在' })
      return
    }

    const parsedReport = extractJsonObject(row.report_json ?? '') ?? {}

    res.json({
      status: row.status,
      points: parseJsonArray(row.points_json),
      advice: parseJsonArray(row.advice_json),
      report: normalizeAnalyzeReport(parsedReport, row.type),
      saved: Boolean(row.saved),
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '分析结果读取失败' })
  }
})

app.get('/api/analyze/reports', async (req, res) => {
  try {
    const [rows] = await pool.query(
      `SELECT id,
              type,
              file_name,
              report_json,
              created_at,
              updated_at
       FROM analyze_tasks
       WHERE user_id = ?
         AND saved = 1
         AND status = 'DONE'
       ORDER BY updated_at DESC`,
      [req.userId],
    )

    res.json(
      rows.map((row) => ({
        taskId: row.id,
        type: row.type,
        fileName: row.file_name ?? '',
        createdAt: row.created_at?.toISOString?.() ?? row.created_at,
        updatedAt: row.updated_at?.toISOString?.() ?? row.updated_at,
        report: normalizeAnalyzeReport(extractJsonObject(row.report_json ?? '') ?? {}, row.type),
      })),
    )
  } catch (error) {
    res.status(500).json({ message: error.message ?? '已保留报告读取失败' })
  }
})

app.post('/api/analyze/tasks/:taskId/save', async (req, res) => {
  try {
    const [[currentTask]] = await pool.query(
      `SELECT id, type, report_json, updated_at
       FROM analyze_tasks
       WHERE id = ? AND user_id = ? AND status = 'DONE'
       LIMIT 1`,
      [req.params.taskId, req.userId],
    )

    if (!currentTask) {
      res.status(404).json({ message: '任务不存在' })
      return
    }

    const [latestSavedRows] = await pool.query(
      `SELECT id, type, report_json, updated_at
       FROM analyze_tasks
       WHERE user_id = ?
         AND saved = 1
         AND status = 'DONE'
         AND id <> ?
       ORDER BY updated_at DESC
       LIMIT 2`,
      [req.userId, req.params.taskId],
    )

    const sourceReports = [currentTask, ...latestSavedRows]
      .map((row) => ({
        taskId: row.id,
        type: row.type,
        updatedAt: row.updated_at?.toISOString?.() ?? row.updated_at,
        report: normalizeAnalyzeReport(extractJsonObject(row.report_json ?? '') ?? {}, row.type),
      }))
      .sort((left, right) => String(right.updatedAt).localeCompare(String(left.updatedAt)))

    const rehabPlanDraft = await generateRehabPlanDraftFromReports({
      reports: sourceReports,
      currentUserId: req.userId,
    })

    await pool.query(
      `UPDATE analyze_tasks
       SET saved = 1, updated_at = NOW()
       WHERE id = ? AND user_id = ?`,
      [req.params.taskId, req.userId],
    )

    res.json({ success: true, saved: true, rehabPlanDraft })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '报告保留失败' })
  }
})

app.delete('/api/analyze/tasks/:taskId', async (req, res) => {
  try {
    const [result] = await pool.query(
      `DELETE FROM analyze_tasks
       WHERE id = ? AND user_id = ?`,
      [req.params.taskId, req.userId],
    )

    if (!result.affectedRows) {
      res.status(404).json({ message: '任务不存在' })
      return
    }

    res.json({ success: true })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '报告删除失败' })
  }
})

app.get('/api/rehab/plan', async (req, res) => {
  try {
    const plan = await fetchRehabPlan(req.userId)
    res.json(plan)
  } catch (error) {
    res.status(500).json({ message: error.message ?? '康复计划读取失败' })
  }
})

app.post('/api/rehab/plan/:id/toggle', async (req, res) => {
  try {
    const [[row]] = await pool.query(
      `SELECT done FROM rehab_plan_items WHERE id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )

    if (!row) {
      res.status(404).json({ message: '训练记录不存在' })
      return
    }

    const nextDone = row.done ? 0 : 1
    await pool.query(
      `UPDATE rehab_plan_items SET done = ? WHERE id = ? AND user_id = ?`,
      [nextDone, req.params.id, req.userId],
    )

    const plan = await fetchRehabPlan(req.userId)
    res.json(plan)
  } catch (error) {
    res.status(500).json({ message: error.message ?? '更新失败' })
  }
})

app.delete('/api/rehab/plan/:id', async (req, res) => {
  try {
    const [result] = await pool.query(
      `DELETE FROM rehab_plan_items WHERE id = ? AND user_id = ?`,
      [req.params.id, req.userId],
    )

    if (!result.affectedRows) {
      res.status(404).json({ message: '训练记录不存在' })
      return
    }

    const plan = await fetchRehabPlan(req.userId, { autoSeedIfEmpty: false })
    res.json(plan)
  } catch (error) {
    res.status(500).json({ message: error.message ?? '删除训练动作失败' })
  }
})

app.get('/api/rehab/plan/settings', async (req, res) => {
  try {
    const row = await fetchRehabPlanSettingsRow(req.userId)

    if (row) {
      res.json({
        focus: row.focus,
        frequency: row.frequency,
        duration: row.duration,
        intensity: row.intensity,
      })
      return
    }

    const plan = await fetchRehabPlan(req.userId)
    res.json(plan.planSummary)
  } catch (error) {
    res.status(500).json({ message: error.message ?? '康复计划设置读取失败' })
  }
})

app.post('/api/rehab/plan/settings', async (req, res) => {
  const payload = req.body ?? {}
  const settings = {
    focus: String(payload.focus ?? '').trim(),
    frequency: String(payload.frequency ?? '').trim(),
    duration: String(payload.duration ?? '').trim(),
    intensity: String(payload.intensity ?? '').trim(),
  }

  try {
    await saveRehabPlanSummary({ userId: req.userId, summary: settings })

    res.json(settings)
  } catch (error) {
    res.status(500).json({ message: error.message ?? '康复计划设置保存失败' })
  }
})

app.post('/api/rehab/plan/apply', async (req, res) => {
  try {
    const draft = normalizeRehabPlanDraft(req.body ?? {}, Array.isArray(req.body?.sourceTaskIds) ? req.body.sourceTaskIds : [])
    const exerciseIds = await resolveDraftExerciseIds({
      exercises: draft.exercises,
      userId: req.userId,
    })

    await saveRehabPlanSummary({ userId: req.userId, summary: draft.summary })
    await saveRehabPlanReminder({ userId: req.userId, reminder: draft.reminder })
    await replaceTodayPlanItems({ userId: req.userId, exerciseIds })

    const plan = await fetchRehabPlan(req.userId)
    res.json(plan)
  } catch (error) {
    res.status(error.statusCode ?? 500).json({ message: error.message ?? '康复计划应用失败' })
  }
})

app.get('/api/rehab/exercises/by-name', async (req, res) => {
  const name = Array.isArray(req.query.name) ? req.query.name[0] : req.query.name
  try {
    const row = await findRehabExerciseByName(name, req.userId)
    if (!row) {
      res.status(404).json({ message: '未找到动作' })
      return
    }

    res.json({
      id: row.id,
      name: row.name,
      category: row.category,
      duration: row.duration,
      level: row.level,
      minutes: Number(row.minutes),
      steps: parseJsonArray(row.steps_json),
      caution: row.caution,
      focus: row.focus,
      benefits: parseJsonArray(row.benefits_json),
      videoMinutes: Number(row.video_minutes),
      done: false,
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '动作读取失败' })
  }
})

app.get('/api/rehab/plan/reminder', async (req, res) => {
  try {
    const row = await fetchRehabPlanReminderRow(req.userId)
    if (!row) {
      res.json({
        time: '08:00',
        days: ['mon', 'wed', 'fri'],
        pushEnabled: true,
      })
      return
    }

    res.json({
      time: row.reminder_time,
      days: parseJsonArray(row.days_json),
      pushEnabled: Boolean(row.push_enabled),
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '计划提醒读取失败' })
  }
})

app.post('/api/rehab/plan/reminder', async (req, res) => {
  const payload = req.body ?? {}
  const time = String(payload.time ?? '').trim()
  const days = Array.isArray(payload.days)
    ? payload.days.map((day) => String(day ?? '').trim()).filter((day) => validReminderDays.includes(day))
    : []

  if (!/^\d{2}:\d{2}$/.test(time)) {
    res.status(400).json({ message: '提醒时间无效' })
    return
  }

  if (!days.length) {
    res.status(400).json({ message: '请至少选择一个提醒日期' })
    return
  }

  try {
    await saveRehabPlanReminder({
      userId: req.userId,
      reminder: {
        time,
        days,
        pushEnabled: payload.pushEnabled !== false,
      },
    })

    res.json({
      time,
      days,
      pushEnabled: payload.pushEnabled !== false,
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '计划提醒保存失败' })
  }
})

app.get('/api/rehab/reminder', async (req, res) => {
  const name = Array.isArray(req.query.name) ? req.query.name[0] : req.query.name
  if (!name) {
    res.status(400).json({ message: 'name 不能为空' })
    return
  }
  try {
    const [rows] = await pool.query(
      `SELECT * FROM rehab_reminders WHERE user_id = ? AND exercise_name = ? LIMIT 1`,
      [req.userId, name],
    )

    const row = rows?.[0]
    if (!row) {
      res.json({
        name,
        time: '08:00',
        days: ['mon', 'wed', 'fri'],
        pushEnabled: true,
      })
      return
    }

    res.json({
      name: row.exercise_name,
      time: row.reminder_time,
      days: parseJsonArray(row.days_json),
      pushEnabled: Boolean(row.push_enabled),
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '提醒读取失败' })
  }
})

app.post('/api/rehab/reminder', async (req, res) => {
  const payload = req.body ?? {}
  if (!payload.name) {
    res.status(400).json({ message: 'name 不能为空' })
    return
  }

  try {
    await pool.query(
      `INSERT INTO rehab_reminders (user_id, exercise_name, reminder_time, days_json, push_enabled)
       VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE reminder_time = VALUES(reminder_time),
                               days_json = VALUES(days_json),
                               push_enabled = VALUES(push_enabled)`,
      [
        req.userId,
        payload.name,
        payload.time,
        JSON.stringify(payload.days ?? []),
        payload.pushEnabled ? 1 : 0,
      ],
    )

    res.json({
      name: payload.name,
      time: payload.time,
      days: payload.days ?? [],
      pushEnabled: Boolean(payload.pushEnabled),
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '提醒保存失败' })
  }
})

app.get('/api/monitor/latest', async (_req, res) => {
  try {
    const [rows] = await pool.query(
      `SELECT recorded_at, hr, sleep_score, deep_sleep_hours, awake_times, stress_score
       FROM monitor_records
       ORDER BY recorded_at DESC
       LIMIT 1`,
    )

    const row = rows?.[0]
    if (!row) {
      res.json({
        hr: 0,
        sleep: 0,
        deepSleep: 0,
        awake: 0,
        stress: 0,
        updatedAt: '',
      })
      return
    }

    res.json({
      hr: Number(row.hr),
      sleep: Number(row.sleep_score),
      deepSleep: Number(row.deep_sleep_hours),
      awake: Number(row.awake_times),
      stress: Number(row.stress_score),
      updatedAt: row.recorded_at?.toISOString?.() ?? row.recorded_at,
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '数据库读取失败' })
  }
})

app.get('/api/monitor/trends', async (req, res) => {
  const metric = Array.isArray(req.query.metric) ? req.query.metric[0] : req.query.metric
  const range = Array.isArray(req.query.range) ? req.query.range[0] : req.query.range

  if (!metricColumnMap[metric]) {
    res.status(400).json({ message: 'metric 参数无效' })
    return
  }

  if (!rangeConfigMap[range]) {
    res.status(400).json({ message: 'range 参数无效' })
    return
  }

  const { where, format } = rangeConfigMap[range]
  const metricColumn = metricColumnMap[metric]

  try {
    const [rows] = await pool.query(
      `SELECT DATE_FORMAT(recorded_at, ?) AS label,
              AVG(${metricColumn}) AS value,
              MIN(recorded_at) AS sort_time
       FROM monitor_records
       WHERE ${where}
       GROUP BY label
       ORDER BY sort_time ASC`,
      [format],
    )

    const labels = rows.map(row => row.label)
    const values = rows.map(row => Number(row.value))
    const insightPayload = buildInsight(metric, values)

    res.json({
      labels,
      values,
      ...insightPayload,
    })
  } catch (error) {
    res.status(500).json({ message: error.message ?? '数据库读取失败' })
  }
})

const port = Number(process.env.API_PORT ?? 3001)

app.listen(port, () => {
  // eslint-disable-next-line no-console
  console.log(`Health API listening on http://localhost:${port}`)
})
