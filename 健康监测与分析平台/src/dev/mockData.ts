export type MockBadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'default'
export type MockHomeMetricKey = 'hr' | 'stress' | 'hydration'
export type MockMonitorMetric = 'hr' | 'sleep' | 'stress'
export type MockMonitorRange = 'hour' | 'day' | 'month'
export type MockUploadType = 'image' | 'lab' | 'text' | 'symptom'
export type MockDeviceType = 'watch' | 'band' | 'ring' | 'other'
export type MockTaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export type MockHomeMetric = {
  key: MockHomeMetricKey
  value: number
  badge: string
  badgeVariant: MockBadgeVariant
  hint: string
}

export type MockHomeSummary = {
  userName: string
  healthScore: number
  statusBadge: string
  statusBadgeVariant: MockBadgeVariant
  statusSummary: string
  stepsTarget: number
  stepsNow: number
  keyMetrics: MockHomeMetric[]
  suggestions: string[]
}

export type MockMonitorLatest = {
  hr: number
  sleep: number
  deepSleep: number
  awake: number
  stress: number
  updatedAt: string
}

export type MockMonitorTrend = {
  labels: string[]
  values: number[]
  insight: string
  suggestion: string
}

export type MockAnalyzeResult = {
  status: MockTaskStatus
  points: string[]
  advice: string[]
  report?: {
    title: string
    summary: string
    riskLevel: string
    points: string[]
    advice: string[]
    rehabFocus: string
    followUp: string[]
    caution: string
  }
  saved?: boolean
  message?: string
}

export type MockAnalyzeTaskRuntime = MockAnalyzeResult & {
  polls: number
}

export type MockMedicationReminder = {
  id: number
  time: string
  enabled: boolean
}

export type MockMedicationItem = {
  id: number
  name: string
  alias: string
  dosageValue: number
  dosageUnit: string
  usage: string
  notes: string
  photoUrl: string
  enableOcr: boolean
  enableYolo: boolean
  ocrEndpoint: string
  yoloEndpoint: string
  enabled: boolean
  reminders: MockMedicationReminder[]
}

export type MockProfileSettings = {
  name: string
  email: string
  age: number
  gender: 'male' | 'female' | 'other'
  height: number
  weight: number
  focus: string
  goals: string[]
  dailySummary: boolean
  riskAlert: boolean
  rehabReminder: boolean
}

export type MockRehabExercise = {
  id: number
  name: string
  category: string
  duration: string
  level: '基础' | '进阶'
  minutes: number
  steps: string[]
  caution: string
  focus: string
  benefits: string[]
  videoMinutes: number
  done: boolean
}

export type MockRehabPlan = {
  label: string
  exercises: MockRehabExercise[]
  weekTrend: {
    labels: string[]
    values: number[]
    insight: string
    deltaPercent: number
  }
  planSummary: {
    focus: string
    frequency: string
    duration: string
    intensity: string
  }
  reminderSummary: {
    time: string
    days: string
    channel: string
    status: string
  }
}

export type MockRehabReminder = {
  time: string
  days: string[]
  pushEnabled: boolean
}

export type MockRehabPlanSettings = {
  focus: string
  frequency: string
  duration: string
  intensity: string
}

export type MockRehabVideoSegment = {
  start: string
  end: string
  issue: string
  suggestion: string
}

export type MockRehabVideoResult = {
  status: MockTaskStatus
  score?: number
  issues?: string[]
  tips?: string[]
  segments?: MockRehabVideoSegment[]
  message?: string
  polls: number
}

export type MockConsultResponse = {
  requestId: string
  answer: string
  suggestions: string[]
  disclaimer: string
}

export type MockDeviceItem = {
  id: number
  name: string
  brand: string
  model: string
  type: MockDeviceType
  connected: boolean
  battery: number
  lastSyncAt: string
}

export type MockDb = {
  homeSummary: MockHomeSummary
  monitorLatest: MockMonitorLatest
  monitorTrends: Record<MockMonitorMetric, Record<MockMonitorRange, MockMonitorTrend>>
  analyzeTemplates: Record<MockUploadType, Omit<MockAnalyzeResult, 'status'>>
  analyzeTasks: Record<string, MockAnalyzeTaskRuntime>
  medications: MockMedicationItem[]
  profileSettings: MockProfileSettings
  profileRiskScore: string
  rehabPlan: MockRehabPlan
  rehabPlanReminder: MockRehabReminder
  rehabPlanSettings: MockRehabPlanSettings
  rehabVideoTasks: Record<string, MockRehabVideoResult>
  devices: MockDeviceItem[]
  consultHistory: MockConsultResponse[]
}

const nowIso = '2026-03-11T09:36:00.000Z'

const seedMockDb: MockDb = {
  homeSummary: {
    userName: '李明',
    healthScore: 84,
    statusBadge: '总体稳定',
    statusBadgeVariant: 'success',
    statusSummary: '今日建议：保持补水、午后进行 12 分钟拉伸。',
    stepsTarget: 9000,
    stepsNow: 5320,
    keyMetrics: [
      { key: 'hr', value: 71, badge: '正常', badgeVariant: 'success', hint: '静息心率较昨日 -1' },
      { key: 'stress', value: 48, badge: '可控', badgeVariant: 'info', hint: '建议维持番茄钟节奏' },
      { key: 'hydration', value: 1700, badge: '良好', badgeVariant: 'success', hint: '距目标还差 500ml' },
    ],
    suggestions: ['午餐后步行 10 分钟，降低久坐负担。', '下午 16:00 前补充 300-500ml 温水。', '睡前进行 6 分钟呼吸放松训练。'],
  },
  monitorLatest: {
    hr: 71,
    sleep: 86,
    deepSleep: 2.1,
    awake: 1,
    stress: 48,
    updatedAt: nowIso,
  },
  monitorTrends: {
    hr: {
      hour: {
        labels: ['09:00', '09:10', '09:20', '09:30', '09:40', '09:50'],
        values: [72, 73, 71, 70, 71, 72],
        insight: '心率波动平稳，处于轻活动区间。',
        suggestion: '维持当前节奏，避免连续久坐超过 60 分钟。',
      },
      day: {
        labels: ['03-05', '03-06', '03-07', '03-08', '03-09', '03-10', '03-11'],
        values: [74, 73, 72, 71, 70, 72, 71],
        insight: '近 7 天静息心率逐步回落。',
        suggestion: '继续保持规律作息与中等强度活动。',
      },
      month: {
        labels: ['2025-10', '2025-11', '2025-12', '2026-01', '2026-02', '2026-03'],
        values: [76, 75, 74, 73, 72, 71],
        insight: '过去 6 个月心率趋势向好。',
        suggestion: '可继续执行当前训练和恢复计划。',
      },
    },
    sleep: {
      hour: {
        labels: ['昨夜'],
        values: [86],
        insight: '睡眠评分良好，深睡占比合理。',
        suggestion: '保持固定入睡时间，减少睡前屏幕暴露。',
      },
      day: {
        labels: ['03-05', '03-06', '03-07', '03-08', '03-09', '03-10', '03-11'],
        values: [80, 82, 84, 83, 85, 87, 86],
        insight: '睡眠质量整体稳定提升。',
        suggestion: '保持晚间放松流程，避免咖啡因过晚摄入。',
      },
      month: {
        labels: ['2025-10', '2025-11', '2025-12', '2026-01', '2026-02', '2026-03'],
        values: [74, 76, 78, 81, 84, 86],
        insight: '月度睡眠恢复能力明显提升。',
        suggestion: '继续维持每周轻量拉伸和固定作息。',
      },
    },
    stress: {
      hour: {
        labels: ['09:00', '09:10', '09:20', '09:30', '09:40', '09:50'],
        values: [52, 51, 50, 48, 47, 48],
        insight: '压力指数逐步下降。',
        suggestion: '保持深呼吸节奏，继续短间隔休息。',
      },
      day: {
        labels: ['03-05', '03-06', '03-07', '03-08', '03-09', '03-10', '03-11'],
        values: [62, 58, 56, 54, 51, 49, 48],
        insight: '一周压力负荷下降明显。',
        suggestion: '建议继续执行晚间放松训练。',
      },
      month: {
        labels: ['2025-10', '2025-11', '2025-12', '2026-01', '2026-02', '2026-03'],
        values: [70, 66, 63, 58, 53, 49],
        insight: '长期压力负荷下降，恢复效率更好。',
        suggestion: '可逐步增加轻有氧并保持睡眠习惯。',
      },
    },
  },
  analyzeTemplates: {
    image: {
      points: ['脊柱侧弯角度轻度偏高，建议进一步评估。', '肩胛稳定不足，存在代偿风险。'],
      advice: ['每日进行 8-10 分钟核心稳定练习。', '保持显示器视线平齐，减少低头时长。'],
    },
    lab: {
      points: ['炎症指标在正常范围上沿。', '水分摄入不足迹象较明显。'],
      advice: ['提高每日饮水量至 2200ml 左右。', '建议 2 周后复测关键指标。'],
    },
    text: {
      points: ['描述提示腰背慢性劳损倾向。', '疼痛触发场景与久坐相关性较高。'],
      advice: ['每 50 分钟起身活动 3-5 分钟。', '加入低强度核心激活训练。'],
    },
    symptom: {
      points: ['症状与疲劳累积和睡眠不足相关。', '短期内不建议提升训练强度。'],
      advice: ['先恢复睡眠节律，再逐步增加训练量。', '若出现麻木放射痛，建议线下就医。'],
    },
  },
  analyzeTasks: {},
  medications: [
    {
      id: 1,
      name: '维生素D',
      alias: '小黄丸',
      dosageValue: 1,
      dosageUnit: '粒',
      usage: '饭后',
      notes: '建议固定早餐后服用',
      photoUrl: '',
      enableOcr: false,
      enableYolo: false,
      ocrEndpoint: '',
      yoloEndpoint: '',
      enabled: true,
      reminders: [
        { id: 1, time: '08:10', enabled: true },
        { id: 2, time: '20:10', enabled: true },
      ],
    },
    {
      id: 2,
      name: '鱼油',
      alias: '透明胶囊',
      dosageValue: 2,
      dosageUnit: '粒',
      usage: '随餐',
      notes: '避免空腹服用',
      photoUrl: '',
      enableOcr: false,
      enableYolo: false,
      ocrEndpoint: '',
      yoloEndpoint: '',
      enabled: true,
      reminders: [{ id: 3, time: '12:30', enabled: true }],
    },
  ],
  profileSettings: {
    name: '李明',
    email: 'liming@example.com',
    age: 29,
    gender: 'male',
    height: 172,
    weight: 66,
    focus: '改善久坐导致的腰背紧张，提升睡眠恢复质量。',
    goals: ['姿势改善', '减压恢复'],
    dailySummary: true,
    riskAlert: true,
    rehabReminder: true,
  },
  profileRiskScore: '18 · 低风险',
  rehabPlan: {
    label: '今日计划',
    exercises: [
      {
        id: 101,
        name: '鸟狗式',
        category: '核心稳定',
        duration: '3 组 × 12 次',
        level: '基础',
        minutes: 8,
        steps: ['保持脊柱中立位', '对侧手脚缓慢抬起', '动作稳定后再回收'],
        caution: '若腰部出现明显疼痛请停止并调整动作幅度。',
        focus: '核心稳定',
        benefits: ['改善腰背稳定', '减少久坐疲劳'],
        videoMinutes: 6,
        done: false,
      },
      {
        id: 102,
        name: '臀桥',
        category: '臀腿激活',
        duration: '4 组 × 15 次',
        level: '基础',
        minutes: 9,
        steps: ['脚跟发力抬髋', '骨盆保持稳定', '顶峰停留 1 秒'],
        caution: '避免耸肩代偿和腰椎过伸。',
        focus: '髋伸展控制',
        benefits: ['增强后链力量', '提升骨盆控制'],
        videoMinutes: 7,
        done: false,
      },
    ],
    weekTrend: {
      labels: ['03-05', '03-06', '03-07', '03-08', '03-09', '03-10', '03-11'],
      values: [16, 18, 20, 19, 22, 24, 26],
      insight: '训练时长稳步上升，动作完成率持续改善。',
      deltaPercent: 63,
    },
    planSummary: {
      focus: '核心稳定',
      frequency: '每周 3 次',
      duration: '单次 22 分钟',
      intensity: '低-中',
    },
    reminderSummary: {
      time: '08:00',
      days: '周一 / 周三 / 周五',
      channel: '系统通知',
      status: '已开启',
    },
  },
  rehabPlanReminder: {
    time: '08:00',
    days: ['mon', 'wed', 'fri'],
    pushEnabled: true,
  },
  rehabPlanSettings: {
    focus: '核心稳定',
    frequency: '每周 3 次',
    duration: '单次 22 分钟',
    intensity: '低-中',
  },
  rehabVideoTasks: {},
  devices: [
    {
      id: 1,
      name: 'Apple Watch',
      brand: 'Apple',
      model: 'Series 9',
      type: 'watch',
      connected: true,
      battery: 68,
      lastSyncAt: nowIso,
    },
  ],
  consultHistory: [],
}

export function createMockDb(): MockDb {
  return JSON.parse(JSON.stringify(seedMockDb)) as MockDb
}
