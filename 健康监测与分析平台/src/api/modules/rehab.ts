import { http } from '@/api/http'
import {
  createPostureJob,
  getPostureJobStatus,
  getPostureReport,
  type CameraView,
  type ExerciseType,
  type PostureReport,
} from '@/api/modules/posture'
import { cloneMock, getMockDb, nextMockTaskId, withMockFallback } from '@/dev/mockApi'

export type RehabExercise = {
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

export type RehabWeekTrend = {
  labels: string[]
  values: number[]
  insight: string
  deltaPercent: number
}

export type RehabPlanSummary = {
  focus: string
  frequency: string
  duration: string
  intensity: string
}

export type RehabReminderSummary = {
  time: string
  days: string
  channel: string
  status: string
}

export type RehabPlan = {
  label: string
  exercises: RehabExercise[]
  weekTrend: RehabWeekTrend
  planSummary: RehabPlanSummary
  reminderSummary: RehabReminderSummary
}

export type PlanReminderDraft = {
  time: string
  days: string[]
  pushEnabled: boolean
}

export type DraftExerciseCandidate = {
  mode: 'existing' | 'generated'
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
}

export type RehabPlanDraft = {
  sourceTaskIds: string[]
  summary: RehabPlanSummary
  exercises: DraftExerciseCandidate[]
  reminder: PlanReminderDraft
}

export type RehabVideoTask = {
  taskId: string
}

export type RehabVideoSegment = {
  start: string
  end: string
  issue: string
  suggestion: string
}

export type RehabVideoResult = {
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  score?: number
  issues?: string[]
  tips?: string[]
  segments?: RehabVideoSegment[]
  message?: string
}

const postureExerciseMap: Record<string, ExerciseType> = {
  深蹲: 'SQUAT',
  俯卧撑: 'PUSH_UP',
  平板支撑: 'PLANK',
  弓步蹲: 'LUNGE',
  鸟狗式: 'PLANK',
  死虫式: 'PLANK',
  髂腰肌拉伸: 'LUNGE',
  弹力带划船: 'PUSH_UP',
}

function mapExerciseNameToPostureExercise(name: string): ExerciseType {
  return postureExerciseMap[String(name ?? '').trim()] ?? 'PLANK'
}

function readCurrentUserId(): string {
  if (typeof window === 'undefined') return '1'
  const raw = window.localStorage.getItem('hm_auth_session')
  if (!raw) return '1'
  try {
    const parsed = JSON.parse(raw) as { user?: { id?: string | number } }
    return String(parsed?.user?.id ?? '1')
  } catch {
    return '1'
  }
}

function formatPostureTime(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

function mapPostureReportToRehabVideoResult(report: PostureReport): RehabVideoResult {
  return {
    status: 'DONE',
    score: Math.round(report.score),
    issues: report.issues.map((issue) => issue.description),
    tips: report.suggestions,
    segments: report.reps.flatMap((rep) =>
      rep.issues.map((issue) => ({
        start: formatPostureTime(rep.startMs),
        end: formatPostureTime(rep.endMs),
        issue: issue.description,
        suggestion: report.suggestions[0] ?? '请放慢动作，优先修正本段问题。',
      })),
    ),
    message: report.summary ?? undefined,
  }
}

export type RehabPlanSettings = {
  focus: string
  frequency: string
  duration: string
  intensity: string
}

const rehabTextMap: Record<string, string> = {
  'Bird Dog': '鸟狗式',
  'Dead Bug': '死虫式',
  'Hip Flexor Stretch': '髂腰肌拉伸',
  'Band Row': '弹力带划船',
  'Core Stability': '核心稳定',
  Mobility: '灵活性',
  'Upper Back Activation': '上背激活',
  basic: '基础',
  advanced: '进阶',
  '3 x 12': '3 组 × 12 次',
  '3 x 10': '3 组 × 10 次',
  '2 x 30s each side': '每侧 2 组 × 30 秒',
  'Core stability and anti-rotation control': '核心稳定与抗旋转控制',
  'Core anti-extension control': '核心抗伸展控制',
  'Hip flexor release and pelvis alignment': '髋屈肌放松与骨盆位置调整',
  'Scapular stability and upper back activation': '肩胛稳定与上背激活',
  'Keep a neutral spine': '保持脊柱中立位',
  'Reach opposite arm and leg': '对侧手脚伸直',
  'Move slowly and return with control': '动作缓慢并控制回位',
  'Keep your low back on the floor': '腰背贴地，保持腹压',
  'Extend opposite arm and leg slowly': '对侧手脚缓慢伸展',
  'Exhale on reach and inhale on return': '伸展时呼气，回位时吸气',
  'Use a half-kneeling lunge stance': '采用跪姿弓步位',
  'Posteriorly tilt the pelvis': '骨盆轻微后倾',
  'Stretch both sides evenly': '左右两侧均匀拉伸',
  'Set the shoulder blades first': '先完成肩胛后收与下压',
  'Keep elbows close to the body': '肘部贴近身体向后拉',
  'Maintain thoracic stability': '全程保持胸椎稳定',
  'Stop if you feel sharp low-back pain.': '如果下背部出现明显刺痛请立即停止。',
  'Avoid holding your breath during the movement.': '动作过程中避免憋气。',
  'Pad the knee if needed.': '如有需要可在膝下垫软垫。',
  'Reduce resistance if shoulder pain appears.': '如果肩部不适，请降低阻力或暂停。',
  'Improve trunk stability': '提升躯干稳定性',
  'Improve movement control': '改善动作控制',
  'Reduce compensation risk': '降低代偿风险',
  'Build abdominal control': '增强腹部控制',
  'Improve pelvic stability': '改善骨盆稳定',
  'Reduce lower back load': '减轻下背部负担',
  'Reduce sitting stiffness': '缓解久坐僵硬',
  'Improve hip mobility': '提升髋部活动度',
  'Support lumbar comfort': '辅助腰背舒适',
  'Improve rounded shoulders': '改善含胸圆肩',
  'Build upper back endurance': '增强上背耐力',
  'Support posture': '提升姿势支撑',
  'Posture Correction': '姿势改善',
  'Core stability': '核心稳定',
  '3 sessions per week': '每周 3 次',
  '4 sessions per week': '每周 4 次',
  '20 minutes per session': '单次 20 分钟',
  '25 minutes per session': '单次 25 分钟',
  'Low to moderate': '低到中等强度',
  Moderate: '中等强度',
  'System notification': '系统通知',
  Enabled: '已开启',
  Disabled: '已关闭',
  'Not set': '未设置',
}

const rehabNameToBackendMap: Record<string, string> = {
  鸟狗式: 'Bird Dog',
  死虫式: 'Dead Bug',
  髂腰肌拉伸: 'Hip Flexor Stretch',
  弹力带划船: 'Band Row',
}

const dayMap: Record<string, string> = {
  mon: '周一',
  tue: '周二',
  wed: '周三',
  thu: '周四',
  fri: '周五',
  sat: '周六',
  sun: '周日',
}

function translateRehabText(value: string): string {
  const text = String(value ?? '').trim()
  return rehabTextMap[text] ?? text
}

function toBackendExerciseName(value: string): string {
  const text = String(value ?? '').trim()
  return rehabNameToBackendMap[text] ?? text
}

export function normalizeRehabExercise(
  exercise: Omit<RehabExercise, 'level'> & { level: string },
): RehabExercise {
  return {
    ...exercise,
    name: translateRehabText(exercise.name),
    category: translateRehabText(exercise.category),
    duration: translateRehabText(exercise.duration),
    level: translateRehabText(exercise.level) as RehabExercise['level'],
    steps: exercise.steps.map(translateRehabText),
    caution: translateRehabText(exercise.caution),
    focus: translateRehabText(exercise.focus),
    benefits: exercise.benefits.map(translateRehabText),
  }
}

function normalizePlanSummary(summary: RehabPlanSummary): RehabPlanSummary {
  return {
    focus: translateRehabText(summary.focus),
    frequency: translateRehabText(summary.frequency),
    duration: translateRehabText(summary.duration),
    intensity: translateRehabText(summary.intensity),
  }
}

function normalizeReminderSummary(summary: RehabReminderSummary): RehabReminderSummary {
  return {
    time: summary.time,
    days: summary.days,
    channel: translateRehabText(summary.channel),
    status: translateRehabText(summary.status),
  }
}

function normalizePlanReminder(reminder: PlanReminderDraft): PlanReminderDraft {
  return {
    time: reminder.time,
    days: Array.isArray(reminder.days) ? [...reminder.days] : [],
    pushEnabled: Boolean(reminder.pushEnabled),
  }
}

function normalizeDraftExercise(exercise: DraftExerciseCandidate): DraftExerciseCandidate {
  return {
    ...exercise,
    name: translateRehabText(exercise.name),
    category: translateRehabText(exercise.category),
    duration: translateRehabText(exercise.duration),
    level: translateRehabText(exercise.level) as DraftExerciseCandidate['level'],
    steps: exercise.steps.map(translateRehabText),
    caution: translateRehabText(exercise.caution),
    focus: translateRehabText(exercise.focus),
    benefits: exercise.benefits.map(translateRehabText),
  }
}

function normalizeRehabPlan(plan: RehabPlan): RehabPlan {
  return {
    ...plan,
    exercises: plan.exercises.map(normalizeRehabExercise),
    planSummary: normalizePlanSummary(plan.planSummary),
    reminderSummary: normalizeReminderSummary(plan.reminderSummary),
  }
}

function normalizeRehabPlanDraft(draft: RehabPlanDraft): RehabPlanDraft {
  return {
    sourceTaskIds: Array.isArray(draft.sourceTaskIds) ? [...draft.sourceTaskIds] : [],
    summary: normalizePlanSummary(draft.summary),
    exercises: Array.isArray(draft.exercises) ? draft.exercises.map(normalizeDraftExercise) : [],
    reminder: normalizePlanReminder(draft.reminder),
  }
}

function resolveReminderSummary(reminder: PlanReminderDraft | null): RehabReminderSummary {
  if (!reminder) {
    return {
      time: '--:--',
      days: '未设置',
      channel: '未开启',
      status: '未设置',
    }
  }

  const dayLabels = reminder.days.map((day) => dayMap[day]).filter(Boolean)
  return {
    time: reminder.time,
    days: dayLabels.join(' / ') || '未设置',
    channel: reminder.pushEnabled ? '系统通知' : '未开启',
    status: reminder.pushEnabled ? '已开启' : '已关闭',
  }
}

function buildMockPlan(): RehabPlan {
  const db = getMockDb()
  return {
    label: db.rehabPlan.label,
    exercises: cloneMock(db.rehabPlan.exercises),
    weekTrend: cloneMock(db.rehabPlan.weekTrend),
    planSummary: cloneMock(db.rehabPlanSettings),
    reminderSummary: resolveReminderSummary(db.rehabPlanReminder),
  }
}

function buildVideoTemplate(exerciseName: string): Omit<RehabVideoResult, 'status'> {
  return {
    score: 86,
    issues: [`${exerciseName} 中存在轻微重心偏移`, '动作后段节奏偏快，离心控制不足'],
    tips: ['保持骨盆中立位，减少躯干旋转', '回程阶段放慢 1 秒，专注肌肉控制', '每组结束后放松 20 秒再继续'],
    segments: [
      {
        start: '00:04',
        end: '00:08',
        issue: '肩胛稳定不足，出现耸肩代偿',
        suggestion: '收紧下沉肩胛，肘部方向与躯干对齐',
      },
      {
        start: '00:12',
        end: '00:16',
        issue: '骨盆轻微旋转，核心张力不足',
        suggestion: '降低动作幅度，先保证骨盆水平再抬腿',
      },
      {
        start: '00:20',
        end: '00:25',
        issue: '回程速度过快，离心控制不足',
        suggestion: '将回程节奏调整为 2 秒，避免借力回落',
      },
    ],
  }
}

export async function getRehabPlan(): Promise<RehabPlan> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<RehabPlan>('/rehab/plan')
      return normalizeRehabPlan(data)
    },
    () => normalizeRehabPlan(buildMockPlan()),
  )
}

export async function toggleRehabExercise(id: number): Promise<RehabPlan> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<RehabPlan>(`/rehab/plan/${id}/toggle`)
      return normalizeRehabPlan(data)
    },
    () => {
      const db = getMockDb()
      const target = db.rehabPlan.exercises.find((item) => item.id === id)
      if (!target) {
        throw new Error('训练动作不存在')
      }
      target.done = !target.done
      return normalizeRehabPlan(buildMockPlan())
    },
  )
}

export async function removeRehabExercise(id: number): Promise<RehabPlan> {
  return withMockFallback(
    async () => {
      const { data } = await http.delete<RehabPlan>(`/rehab/plan/${id}`)
      return normalizeRehabPlan(data)
    },
    () => {
      const db = getMockDb()
      db.rehabPlan.exercises = db.rehabPlan.exercises.filter((item) => item.id !== id)
      return normalizeRehabPlan(buildMockPlan())
    },
  )
}

export async function getRehabExerciseByName(name: string): Promise<RehabExercise> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<RehabExercise>('/rehab/exercises/by-name', {
        params: { name: toBackendExerciseName(name) },
      })
      return normalizeRehabExercise(data)
    },
    () => {
      const db = getMockDb()
      const target =
        db.rehabPlan.exercises.find((item) => item.name === name || item.name === toBackendExerciseName(name)) ??
        db.rehabPlan.exercises[0]
      if (!target) {
        throw new Error('暂无康复动作')
      }
      return normalizeRehabExercise(cloneMock(target))
    },
  )
}

export async function getRehabPlanReminder(): Promise<PlanReminderDraft> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<PlanReminderDraft>('/rehab/plan/reminder')
      return normalizePlanReminder(data)
    },
    () => {
      const db = getMockDb()
      return normalizePlanReminder(cloneMock(db.rehabPlanReminder))
    },
  )
}

export async function saveRehabPlanReminder(payload: PlanReminderDraft): Promise<PlanReminderDraft> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<PlanReminderDraft>('/rehab/plan/reminder', payload)
      return normalizePlanReminder(data)
    },
    () => {
      const db = getMockDb()
      db.rehabPlanReminder = cloneMock(payload)
      db.rehabPlan.reminderSummary = resolveReminderSummary(payload)
      return normalizePlanReminder(cloneMock(payload))
    },
  )
}

export async function applyRehabPlanDraft(payload: RehabPlanDraft): Promise<RehabPlan> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<RehabPlan>('/rehab/plan/apply', payload)
      return normalizeRehabPlan(data)
    },
    () => {
      const db = getMockDb()
      db.rehabPlanSettings = cloneMock(payload.summary)
      db.rehabPlan.planSummary = cloneMock(payload.summary)
      db.rehabPlanReminder = cloneMock(payload.reminder)
      db.rehabPlan.reminderSummary = resolveReminderSummary(payload.reminder)
      db.rehabPlan.exercises = payload.exercises.map((exercise, index) => ({
        id: index + 1,
        name: exercise.name,
        category: exercise.category,
        duration: exercise.duration,
        level: exercise.level,
        minutes: exercise.minutes,
        steps: cloneMock(exercise.steps),
        caution: exercise.caution,
        focus: exercise.focus,
        benefits: cloneMock(exercise.benefits),
        videoMinutes: exercise.videoMinutes,
        done: false,
      }))
      return normalizeRehabPlan(buildMockPlan())
    },
  )
}

export async function createRehabVideoTask(payload: FormData): Promise<RehabVideoTask> {
  return withMockFallback(
    async () => {
      const file = payload.get('file')
      if (!(file instanceof File)) {
        throw new Error('请先选择录制视频')
      }
      const exerciseName = String(payload.get('exerciseName') ?? '平板支撑')
      const job = await createPostureJob({
        userId: readCurrentUserId(),
        exerciseType: mapExerciseNameToPostureExercise(exerciseName),
        cameraView: 'SIDE' as CameraView,
        videoFile: file,
      })
      return { taskId: job.jobId }
    },
    () => {
      const db = getMockDb()
      const taskId = nextMockTaskId('rehabVideo')
      const exerciseName = String(payload.get('exerciseName') ?? '当前动作')
      const template = buildVideoTemplate(exerciseName)

      db.rehabVideoTasks[taskId] = {
        status: 'RUNNING',
        polls: 0,
        ...cloneMock(template),
      }

      return { taskId }
    },
  )
}

export async function getRehabVideoTask(taskId: string): Promise<RehabVideoResult> {
  return withMockFallback(
    async () => {
      const latest = await getPostureJobStatus(taskId)
      if (latest.status === 'FAILED') {
        return {
          status: 'FAILED',
          message: latest.failReason ?? '动作纠错任务失败',
        }
      }

      if (latest.status === 'SUCCEEDED' || latest.status === 'LOW_CONFIDENCE') {
        const report = await getPostureReport(taskId)
        return mapPostureReportToRehabVideoResult(report)
      }

      return {
        status: latest.status === 'RUNNING' ? 'RUNNING' : 'PENDING',
      }
    },
    () => {
      const db = getMockDb()
      const task = db.rehabVideoTasks[taskId]
      if (!task) {
        return {
          status: 'FAILED',
          message: '视频任务不存在或已失效',
        }
      }

      task.polls += 1
      if (task.polls >= 3) {
        task.status = 'DONE'
      }

      if (task.status === 'DONE') {
        return {
          status: 'DONE',
          score: task.score,
          issues: cloneMock(task.issues ?? []),
          tips: cloneMock(task.tips ?? []),
          segments: cloneMock(task.segments ?? []),
        }
      }

      return {
        status: task.status,
      }
    },
  )
}

export async function getRehabPlanSettings(): Promise<RehabPlanSettings> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<RehabPlanSettings>('/rehab/plan/settings')
      return normalizePlanSummary(data)
    },
    () => {
      const db = getMockDb()
      return normalizePlanSummary(cloneMock(db.rehabPlanSettings))
    },
  )
}

export async function saveRehabPlanSettings(payload: RehabPlanSettings): Promise<RehabPlanSettings> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<RehabPlanSettings>('/rehab/plan/settings', payload)
      return normalizePlanSummary(data)
    },
    () => {
      const db = getMockDb()
      db.rehabPlanSettings = cloneMock(payload)
      db.rehabPlan.planSummary = cloneMock(payload)
      return normalizePlanSummary(cloneMock(payload))
    },
  )
}

export { normalizeRehabPlanDraft }

export type ExerciseAnalysis = {
  exerciseName: string
  performanceLevel: 'excellent' | 'good' | 'overexertion' | 'underperformance' | 'no_device_data' | 'not_completed'
  avgHeartRate: number
  maxHeartRate: number
  actualDurationSeconds: number
  targetDurationSeconds: number
  exertionScore: number
  note: string
}

export type RehabPerformanceAnalysis = {
  date: string
  exerciseAnalyses: ExerciseAnalysis[]
  overallAssessment: string
  warnings: string[]
  planAdjustments: string[]
}

export async function getRehabAnalysis(): Promise<RehabPerformanceAnalysis> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<RehabPerformanceAnalysis>('/rehab/analysis')
      return data
    },
    () => ({
      date: new Date().toISOString().slice(0, 10),
      exerciseAnalyses: [
        {
          exerciseName: '肩颈放松操',
          performanceLevel: 'good',
          avgHeartRate: 96,
          maxHeartRate: 118,
          actualDurationSeconds: 480,
          targetDurationSeconds: 600,
          exertionScore: 72,
          note: '整体节奏稳定，心率处于合理区间，建议保持当前强度。',
        },
        {
          exerciseName: '靠墙深蹲',
          performanceLevel: 'excellent',
          avgHeartRate: 104,
          maxHeartRate: 126,
          actualDurationSeconds: 300,
          targetDurationSeconds: 300,
          exertionScore: 85,
          note: '动作规范，达成目标时长，可以进入进阶组。',
        },
      ],
      overallAssessment: '今日训练完成度良好，动作质量保持稳定，继续按计划执行即可。',
      warnings: [],
      planAdjustments: ['适当增加单组次数，逐步提升训练强度。'],
    }),
  )
}

// ============ 智能康复计划 API ============

export type SmartPlanGoal = 'fat_loss' | 'muscle_gain' | 'body_shaping' | 'maintenance' | 'rehab' | 'flexibility' | 'auto'
export type SmartPlanActivityLevel = 'sedentary' | 'light' | 'moderate' | 'active'

export type SmartPlanRequest = {
  height: number       // cm
  weight: number       // kg
  age: number
  gender: 'male' | 'female'
  goal: SmartPlanGoal
  activityLevel: SmartPlanActivityLevel
  source?: 'manual' | 'device'  // 信息来源：手动输入或设备读取
}

export type SmartPlanExerciseItem = {
  id: number
  name: string
  goalType: string
  muscleGroup: string
  equipment: string
  caloriesBurnPerMin: number
  steps: string
  benefits: string
  impact?: 'low' | 'moderate' | 'high'
  minutes?: number
}

/** 周训练计划单日安排 */
export type WeeklyDayPlan = {
  day: string          // 周一 ~ 周日
  dayIndex: number     // 0=周一 ... 6=周日
  isRestDay: boolean
  focus: string        // 训练重点，如"胸肌+三头" / "休息恢复"
  exercises: SmartPlanExerciseItem[]
  duration: number     // 训练时长（分钟）
  estimatedCalories: number
}

/** 饮食建议单餐 */
export type DietSuggestionMeal = {
  mealType: 'breakfast' | 'lunch' | 'dinner' | 'snack'
  title: string
  foods: string[]
  calories: number
  protein: number   // g
  carbs: number     // g
  fat: number       // g
}

/** 饮食建议 */
export type DietSuggestion = {
  targetCalories: number
  targetProtein: number
  targetCarbs: number
  targetFat: number
  meals: DietSuggestionMeal[]
  tips: string[]
}

export type RehabCaseEvidence = {
  id: string
  sourceType: string
  summary: string
  observedAt: string
}

export type RehabPlanConstraint = {
  code: string
  level: 'info' | 'medium' | 'high' | string
  reason: string
  action: string
}

export type RehabCaseProfile = {
  height: number
  weight: number
  age: number
  gender: string
  goal: string
  activityLevel: string
  inputSource: string
}

export type RehabCaseMonitoring = {
  restingHeartRate: number
  sleepScore: number
  stressScore: number
  vo2Max: number
  averageSteps: number
  riskScore: number
  riskLevel: string
}

export type RehabCaseMedication = {
  activeCount: number
  names: string[]
  warnings: string[]
}

export type RehabCaseReport = {
  taskId: string
  type: string
  title: string
  riskLevel: string
  updatedAt: string
}

export type RehabCaseTimeRange = {
  label: string
  from: string
  to: string
}

export type RehabCasePosture = {
  status: string
  score: number | null
  issues: string[]
  source: string
  observedAt: string
}

export type RehabCase = {
  caseId: string
  generatedAt: string
  version: string
  profile: RehabCaseProfile
  monitoring: RehabCaseMonitoring
  medication: RehabCaseMedication
  reports: RehabCaseReport[]
  timeRange: RehabCaseTimeRange
  posture: RehabCasePosture
  evidence: RehabCaseEvidence[]
  constraints: RehabPlanConstraint[]
  safety: {
    level: 'routine' | 'elevated' | string
    flags: string[]
    uncertainty: string
    escalation: string
    actionTags: string[]
  }
}

export type SmartPlanResponse = {
  bmi: number
  bmiCategory: string  // underweight / normal / overweight / obese
  bmr: number
  tdee: number
  targetCalories: number
  exerciseIds: number[]
  exercises: SmartPlanExerciseItem[]
  weeklyPlan: WeeklyDayPlan[]
  dietSuggestion: DietSuggestion
  summary: string      // AI 总体分析
  rehabCase?: RehabCase
}

export type BodyMetrics = {
  bmi: number
  bmiCategory: string
  bmr: number
  tdee: number
}

// ============ 每周身体数据记录 ============

export type WeeklyProgressEntry = {
  date: string           // ISO 日期
  week: number           // 第几周
  weight: number         // kg
  waistCircumference?: number  // 腰围 cm
  hipCircumference?: number    // 臀围 cm
  bodyFatPercent?: number      // 体脂率 %
  muscleMass?: number          // 肌肉量 kg
  note?: string
}

export type WeeklyProgressResponse = {
  entries: WeeklyProgressEntry[]
  trend: {
    weightDelta: number        // 体重变化 kg
    bodyFatDelta: number       // 体脂变化 %
    waistDelta: number         // 腰围变化 cm
    direction: 'improving' | 'stable' | 'regressing'
    insight: string            // AI 趋势分析
  }
  chartData: {
    labels: string[]
    weights: number[]
    bodyFats: number[]
  }
}

export async function generateSmartPlan(req: SmartPlanRequest): Promise<SmartPlanResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<SmartPlanResponse>('/rehab/smart-plan', req)
      return data
    },
    () => mockSmartPlan(req),
  )
}

export async function getRehabCase(): Promise<RehabCase> {
  const { data } = await http.get<RehabCase>('/rehab/case')
  return data
}

export async function applySmartPlan(exerciseIds: number[]): Promise<void> {
  await withMockFallback(
    async () => {
      await http.post('/rehab/smart-plan/apply', { exerciseIds })
    },
    async () => {
      // mock 直接返回
    },
  )
}

export async function getBodyMetrics(): Promise<BodyMetrics> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<BodyMetrics>('/rehab/body-metrics')
      return data
    },
    () => ({ bmi: 22.5, bmiCategory: 'normal', bmr: 1500, tdee: 2000 }),
  )
}

/** 获取每周身体数据记录 */
export async function getWeeklyProgress(): Promise<WeeklyProgressResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<WeeklyProgressResponse>('/rehab/weekly-progress')
      return data
    },
    () => mockWeeklyProgress(),
    true,
  )
}

/** 保存每周身体数据记录 */
export async function saveWeeklyProgress(entry: WeeklyProgressEntry): Promise<void> {
  return withMockFallback(
    async () => {
      await http.post('/rehab/weekly-progress', entry)
    },
    () => {
      // mock 直接返回
    },
    true,
  )
}

// ============ 周训练计划本地持久化（供提醒页读取） ============

const WEEKLY_PLAN_STORAGE_KEY = 'hm_rehab_weekly_plan'
const WEEKLY_PLAN_GOAL_KEY = 'hm_rehab_weekly_plan_goal'

/** 将生成的周训练计划写入 localStorage，供提醒页读取今日训练内容 */
export function saveWeeklyPlanToLocal(plan: WeeklyDayPlan[], goal: SmartPlanGoal): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(WEEKLY_PLAN_STORAGE_KEY, JSON.stringify(plan))
    window.localStorage.setItem(WEEKLY_PLAN_GOAL_KEY, goal)
  } catch {
    // 忽略写入失败（隐私模式 / 容量超限）
  }
}

/** 读取本地保存的周训练计划；不存在时返回 null */
export function readWeeklyPlanFromLocal(): WeeklyDayPlan[] | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(WEEKLY_PLAN_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as WeeklyDayPlan[]
    if (!Array.isArray(parsed) || parsed.length === 0) return null
    return parsed
  } catch {
    return null
  }
}

/** 获取已保存的周训练计划（API + localStorage 兜底）；都没有时返回 mock 默认计划 */
export async function getSavedWeeklyPlan(): Promise<{ weeklyPlan: WeeklyDayPlan[]; goal: SmartPlanGoal }> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<{ weeklyPlan: WeeklyDayPlan[]; goal: SmartPlanGoal }>('/rehab/smart-plan/saved')
      return data
    },
    () => {
      // 1. 优先读取 localStorage 中保存的计划
      const local = readWeeklyPlanFromLocal()
      if (local) {
        const goal = (typeof window !== 'undefined' && window.localStorage.getItem(WEEKLY_PLAN_GOAL_KEY)) as SmartPlanGoal | null
        return { weeklyPlan: local, goal: goal ?? 'maintenance' }
      }
      // 2. 都没有时，返回基于默认参数生成的计划
      const exercises = selectSmartExercises('maintenance', 'moderate')
      return { weeklyPlan: generateWeeklyPlan(exercises, 'maintenance', 'moderate'), goal: 'maintenance' }
    },
    true,
  )
}

/** 根据日期（0=周日, 1=周一...）返回今日训练安排 */
export function getTodayPlan(weeklyPlan: WeeklyDayPlan[]): WeeklyDayPlan | null {
  if (!Array.isArray(weeklyPlan) || weeklyPlan.length === 0) return null
  const jsDay = new Date().getDay() // 0=周日, 1=周一 ... 6=周六
  const dayIndex = jsDay === 0 ? 6 : jsDay - 1 // 转为 0=周一 ... 6=周日
  return weeklyPlan.find((d) => d.dayIndex === dayIndex) ?? weeklyPlan[0] ?? null
}

const smartExercisePool: SmartPlanExerciseItem[] = [
  { id: 1, name: '俯卧撑', goalType: 'muscle_gain', muscleGroup: '胸肌/核心', equipment: '自重', caloriesBurnPerMin: 8, steps: '1. 双手与肩同宽撑地 2. 身体呈一条直线 3. 屈肘下降至胸部近地 4. 推起还原', benefits: '增强上肢推力与核心稳定', impact: 'high' },
  { id: 2, name: '深蹲', goalType: 'muscle_gain', muscleGroup: '下肢', equipment: '自重', caloriesBurnPerMin: 10, steps: '1. 双脚与肩同宽 2. 臀部后坐屈膝 3. 大腿与地面平行 4. 脚跟发力站起', benefits: '强化股四头肌、臀大肌与下肢力量', impact: 'high' },
  { id: 3, name: '哑铃弯举', goalType: 'muscle_gain', muscleGroup: '手臂', equipment: '哑铃', caloriesBurnPerMin: 6, steps: '1. 站立双手持哑铃 2. 大臂贴紧身体 3. 屈肘上举 4. 缓慢控制下放', benefits: '锻炼肱二头肌与前臂力量', impact: 'moderate' },
  { id: 4, name: '平板支撑', goalType: 'rehab', muscleGroup: '核心', equipment: '自重', caloriesBurnPerMin: 5, steps: '1. 前臂撑地，肘在肩部正下方 2. 身体呈一条直线 3. 收紧腹部与臀部 4. 保持 30-60 秒', benefits: '增强核心抗伸展稳定性', impact: 'low' },
  { id: 5, name: '弓步蹲', goalType: 'muscle_gain', muscleGroup: '下肢', equipment: '自重', caloriesBurnPerMin: 9, steps: '1. 一腿向前迈出 2. 双膝屈曲约 90° 3. 后膝接近地面 4. 前脚蹬地还原', benefits: '提升单腿力量与平衡能力', impact: 'moderate' },
  { id: 6, name: '臀桥', goalType: 'rehab', muscleGroup: '臀部/下背', equipment: '自重', caloriesBurnPerMin: 6, steps: '1. 仰卧屈膝，双脚踩地 2. 臀部发力向上顶起 3. 肩、髋、膝成一条线 4. 缓慢下落', benefits: '激活臀肌，稳定骨盆与腰椎', impact: 'low' },
  { id: 7, name: '侧平板支撑', goalType: 'rehab', muscleGroup: '侧腹/核心', equipment: '自重', caloriesBurnPerMin: 5, steps: '1. 侧身用前臂撑地 2. 身体呈一条直线 3. 髋部离开地面 4. 保持 20-40 秒后换边', benefits: '强化侧向核心与抗侧屈能力', impact: 'low' },
  { id: 8, name: '弹力带面拉', goalType: 'rehab', muscleGroup: '上背/肩后束', equipment: '弹力带', caloriesBurnPerMin: 6, steps: '1. 弹力带固定于高位 2. 双手拉向面部两侧 3. 肩胛骨后收下沉 4. 缓慢回放', benefits: '改善圆肩，强化上背与肩胛稳定', impact: 'low' },
  { id: 9, name: '卷腹', goalType: 'fat_loss', muscleGroup: '腹部', equipment: '自重', caloriesBurnPerMin: 7, steps: '1. 仰卧屈膝，手轻放头侧 2. 腹部发力卷起肩胛 3. 腰部始终贴地 4. 缓慢还原', benefits: '强化腹直肌，提升腹部耐力', impact: 'moderate' },
  { id: 10, name: '开合跳', goalType: 'fat_loss', muscleGroup: '全身', equipment: '自重', caloriesBurnPerMin: 12, steps: '1. 双脚并拢站立 2. 跳起双脚分开、双手上举 3. 再次跳起还原 4. 保持节奏连续进行', benefits: '快速提升心率，燃脂热身', impact: 'high' },
  { id: 11, name: '婴儿式拉伸', goalType: 'flexibility', muscleGroup: '腰背/髋', equipment: '自重', caloriesBurnPerMin: 3, steps: '1. 跪坐，臀部坐向脚跟 2. 上身前倾，双臂前伸 3. 额头贴地 4. 保持 30-60 秒', benefits: '放松下背部，改善髋屈肌紧张', impact: 'low' },
  { id: 12, name: '猫牛式', goalType: 'flexibility', muscleGroup: '脊柱', equipment: '自重', caloriesBurnPerMin: 3, steps: '1. 四足跪姿准备 2. 吸气塌腰抬头（牛式） 3. 呼气拱背低头（猫式） 4. 缓慢交替重复', benefits: '增加脊柱灵活性，缓解腰背僵硬', impact: 'low' },
  { id: 13, name: '弹力带划船', goalType: 'muscle_gain', muscleGroup: '背部', equipment: '弹力带', caloriesBurnPerMin: 7, steps: '1. 坐姿双腿伸直固定弹力带 2. 肩胛后收，拉向腹部 3. 肘部贴近身体 4. 缓慢回放', benefits: '强化背部肌群与肩胛稳定', impact: 'moderate' },
  { id: 14, name: '高抬腿', goalType: 'fat_loss', muscleGroup: '下肢/心肺', equipment: '自重', caloriesBurnPerMin: 11, steps: '1. 原地快速交替抬腿 2. 大腿抬至接近水平 3. 配合摆臂保持节奏 4. 持续 30-60 秒', benefits: '提升心肺功能与下肢爆发力', impact: 'high' },
  { id: 15, name: '靠墙静蹲', goalType: 'rehab', muscleGroup: '下肢/膝关节', equipment: '自重', caloriesBurnPerMin: 5, steps: '1. 背靠墙站立 2. 缓慢下蹲至大腿与地面平行 3. 保持 30-60 秒 4. 缓慢站起', benefits: '增强股四头肌力量，保护膝关节', impact: 'low' },
  { id: 16, name: '站姿提踵', goalType: 'muscle_gain', muscleGroup: '小腿', equipment: '自重', caloriesBurnPerMin: 5, steps: '1. 双脚并拢站立 2. 踮起脚尖至最高点 3. 缓慢下放 4. 重复 15-20 次', benefits: '强化小腿三头肌与踝关节稳定', impact: 'low' },
]

function selectSmartExercises(goal: SmartPlanGoal, activityLevel: SmartPlanActivityLevel): SmartPlanExerciseItem[] {
  // auto 目标根据 BMI 自动判断
  let effectiveGoal = goal
  if (goal === 'auto') {
    effectiveGoal = 'maintenance'
  }

  const goalPriority: Record<string, string[]> = {
    fat_loss: ['fat_loss', 'maintenance', 'muscle_gain', 'rehab'],
    muscle_gain: ['muscle_gain', 'maintenance', 'rehab', 'fat_loss'],
    body_shaping: ['fat_loss', 'muscle_gain', 'rehab', 'flexibility'],
    rehab: ['rehab', 'flexibility', 'maintenance', 'muscle_gain'],
    flexibility: ['flexibility', 'rehab', 'maintenance', 'fat_loss'],
    maintenance: ['maintenance', 'rehab', 'muscle_gain', 'fat_loss', 'flexibility'],
  }

  const ordered = goalPriority[effectiveGoal] ?? goalPriority.maintenance
  const candidates: SmartPlanExerciseItem[] = []
  const seen = new Set<number>()

  ordered.forEach((g) => {
    smartExercisePool.forEach((ex) => {
      if (ex.goalType === g && !seen.has(ex.id)) {
        seen.add(ex.id)
        candidates.push(ex)
      }
    })
  })

  smartExercisePool.forEach((ex) => {
    if (!seen.has(ex.id)) {
      seen.add(ex.id)
      candidates.push(ex)
    }
  })

  let filtered = candidates
  if (activityLevel === 'sedentary' || activityLevel === 'light') {
    filtered = candidates.filter((ex) => ex.impact !== 'high')
  }

  const count = activityLevel === 'sedentary' ? 6 : activityLevel === 'light' ? 7 : 8
  const selected = filtered.slice(0, count)

  return selected.map((ex, index) => ({ ...ex, id: index + 1 }))
}

/** 生成周训练计划 */
function generateWeeklyPlan(exercises: SmartPlanExerciseItem[], goal: SmartPlanGoal, activityLevel: SmartPlanActivityLevel): WeeklyDayPlan[] {
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const dayFocus = ['胸肌+三头', '背部+二头', '下肢+核心', '肩+手臂', '全身燃脂', '柔韧拉伸', '休息恢复']

  // 根据活动量决定训练天数
  const trainingDays = activityLevel === 'sedentary' ? 3 : activityLevel === 'light' ? 4 : activityLevel === 'moderate' ? 5 : 6
  // 休息日安排在哪些天
  const restDays = new Set<number>()
  if (trainingDays <= 5) restDays.add(6) // 周日休息
  if (trainingDays <= 4) restDays.add(2) // 周三休息
  if (trainingDays <= 3) restDays.add(4) // 周五休息

  // 训练时长
  const baseDuration = activityLevel === 'sedentary' ? 20 : activityLevel === 'light' ? 30 : activityLevel === 'moderate' ? 40 : 50

  // 将动作分配到训练日
  const exercisesPerDay = Math.ceil(exercises.length / trainingDays)

  return days.map((day, idx) => {
    const isRestDay = restDays.has(idx)
    if (isRestDay) {
      return {
        day,
        dayIndex: idx,
        isRestDay: true,
        focus: '休息恢复',
        exercises: [],
        duration: 0,
        estimatedCalories: 0,
      }
    }

    const dayExercises = exercises.slice(
      (idx - [...restDays].filter(d => d < idx).length) * exercisesPerDay,
      (idx - [...restDays].filter(d => d < idx).length + 1) * exercisesPerDay,
    )

    // Keep the development fixture deterministic; real duration comes from the planner.
    const duration = baseDuration + (idx % 3) * 5
    const estimatedCalories = dayExercises.reduce((sum, ex) => sum + ex.caloriesBurnPerMin * (duration / dayExercises.length), 0)

    return {
      day,
      dayIndex: idx,
      isRestDay: false,
      focus: dayFocus[idx] ?? '综合训练',
      exercises: dayExercises,
      duration: Math.round(duration),
      estimatedCalories: Math.round(estimatedCalories),
    }
  })
}

/** 生成饮食建议 */
function generateDietSuggestion(targetCalories: number, goal: SmartPlanGoal): DietSuggestion {
  // 宏量营养素比例
  let proteinRatio = 0.25, carbsRatio = 0.45, fatRatio = 0.30
  if (goal === 'muscle_gain') { proteinRatio = 0.30; carbsRatio = 0.45; fatRatio = 0.25 }
  else if (goal === 'fat_loss') { proteinRatio = 0.35; carbsRatio = 0.35; fatRatio = 0.30 }
  else if (goal === 'body_shaping') { proteinRatio = 0.30; carbsRatio = 0.40; fatRatio = 0.30 }

  const targetProtein = Math.round((targetCalories * proteinRatio) / 4)
  const targetCarbs = Math.round((targetCalories * carbsRatio) / 4)
  const targetFat = Math.round((targetCalories * fatRatio) / 9)

  const breakfastCal = Math.round(targetCalories * 0.30)
  const lunchCal = Math.round(targetCalories * 0.40)
  const dinnerCal = Math.round(targetCalories * 0.25)
  const snackCal = targetCalories - breakfastCal - lunchCal - dinnerCal

  const meals: DietSuggestionMeal[] = [
    {
      mealType: 'breakfast',
      title: '早餐',
      foods: ['全麦面包 2 片', '水煮蛋 2 个', '脱脂牛奶 250ml', '蓝莓 50g'],
      calories: breakfastCal,
      protein: Math.round((breakfastCal * proteinRatio) / 4),
      carbs: Math.round((breakfastCal * carbsRatio) / 4),
      fat: Math.round((breakfastCal * fatRatio) / 9),
    },
    {
      mealType: 'lunch',
      title: '午餐',
      foods: ['糙米饭 150g', '香煎鸡胸肉 150g', '西兰花 200g', '橄榄油拌沙拉'],
      calories: lunchCal,
      protein: Math.round((lunchCal * proteinRatio) / 4),
      carbs: Math.round((lunchCal * carbsRatio) / 4),
      fat: Math.round((lunchCal * fatRatio) / 9),
    },
    {
      mealType: 'snack',
      title: '加餐',
      foods: ['希腊酸奶 150g', '杏仁 15 颗'],
      calories: snackCal,
      protein: Math.round((snackCal * proteinRatio) / 4),
      carbs: Math.round((snackCal * carbsRatio) / 4),
      fat: Math.round((snackCal * fatRatio) / 9),
    },
    {
      mealType: 'dinner',
      title: '晚餐',
      foods: ['红薯 200g', '清蒸三文鱼 120g', '菠菜 150g', '番茄蛋花汤'],
      calories: dinnerCal,
      protein: Math.round((dinnerCal * proteinRatio) / 4),
      carbs: Math.round((dinnerCal * carbsRatio) / 4),
      fat: Math.round((dinnerCal * fatRatio) / 9),
    },
  ]

  const tips: string[] = [
    '每日饮水 2000-2500ml，运动前后各补充 500ml',
    '训练后 30 分钟内补充蛋白质，促进肌肉修复',
    '睡前 2 小时避免大量进食，可饮用一杯温牛奶',
  ]
  if (goal === 'fat_loss') tips.push('控制精制碳水摄入，以粗粮替代白米白面')
  if (goal === 'muscle_gain') tips.push('确保每公斤体重摄入 1.6-2.2g 蛋白质')

  return { targetCalories, targetProtein, targetCarbs, targetFat, meals, tips }
}

function mockSmartPlan(req: SmartPlanRequest): SmartPlanResponse {
  const heightM = req.height / 100
  const bmi = Number((req.weight / (heightM * heightM)).toFixed(1))
  const bmiCategory = bmi < 18.5 ? 'underweight' : bmi < 24 ? 'normal' : bmi < 28 ? 'overweight' : 'obese'

  // auto 目标自动分析
  let effectiveGoal = req.goal
  if (req.goal === 'auto') {
    if (bmi < 18.5) effectiveGoal = 'muscle_gain'
    else if (bmi >= 28) effectiveGoal = 'fat_loss'
    else if (bmi >= 24) effectiveGoal = 'body_shaping'
    else effectiveGoal = 'maintenance'
  }

  const bmr = req.gender === 'male'
    ? Math.round(10 * req.weight + 6.25 * req.height - 5 * req.age + 5)
    : Math.round(10 * req.weight + 6.25 * req.height - 5 * req.age - 161)
  const activityFactor = { sedentary: 1.2, light: 1.375, moderate: 1.55, active: 1.725 }[req.activityLevel]
  const tdee = Math.round(bmr * activityFactor)
  const targetCalories = effectiveGoal === 'fat_loss' ? tdee - 500
                    : effectiveGoal === 'muscle_gain' ? tdee + 300
                    : effectiveGoal === 'body_shaping' ? tdee - 200
                    : tdee

  const exercises = selectSmartExercises(effectiveGoal, req.activityLevel)
  const weeklyPlan = generateWeeklyPlan(exercises, effectiveGoal, req.activityLevel)
  const dietSuggestion = generateDietSuggestion(targetCalories, effectiveGoal)

  const goalLabel: Record<string, string> = {
    fat_loss: '减脂', muscle_gain: '增肌', body_shaping: '塑形',
    maintenance: '保持健康', rehab: '康复', flexibility: '柔韧',
  }

  const summary = `根据您的身体数据（BMI ${bmi}，${bmiCategory === 'normal' ? '体型正常' : bmiCategory === 'underweight' ? '偏瘦' : bmiCategory === 'overweight' ? '偏胖' : '肥胖'}），AI 推荐的目标为「${goalLabel[effectiveGoal] ?? '综合训练'}」。每日目标摄入 ${targetCalories} kcal，每周训练 ${weeklyPlan.filter(d => !d.isRestDay).length} 天，预计 ${activityFactor > 1.5 ? '8-12 周' : '12-16 周'}可见明显效果。`

  return {
    bmi,
    bmiCategory,
    bmr,
    tdee,
    targetCalories,
    exerciseIds: exercises.map((e) => e.id),
    exercises,
    weeklyPlan,
    dietSuggestion,
    summary,
  }
}

/** mock 每周身体数据 */
function mockWeeklyProgress(): WeeklyProgressResponse {
  const now = new Date()
  const entries: WeeklyProgressEntry[] = []
  for (let i = 3; i >= 0; i--) {
    const date = new Date(now)
    date.setDate(date.getDate() - i * 7)
    // Keep mock trend reproducible and visibly synthetic.
    const weight = 70 - i * 0.6
    entries.push({
      date: date.toISOString().slice(0, 10),
      week: 4 - i,
      weight: Number(weight.toFixed(1)),
      waistCircumference: 82 - i * 0.5,
      hipCircumference: 96 - i * 0.3,
      bodyFatPercent: 22 - i * 0.4,
      muscleMass: 52 + i * 0.2,
    })
  }

  const weightDelta = entries[entries.length - 1].weight - entries[0].weight
  const bodyFatDelta = (entries[entries.length - 1].bodyFatPercent ?? 0) - (entries[0].bodyFatPercent ?? 0)
  const waistDelta = (entries[entries.length - 1].waistCircumference ?? 0) - (entries[0].waistCircumference ?? 0)

  const direction = weightDelta < -0.3 && bodyFatDelta < -0.3 ? 'improving'
    : weightDelta > 0.5 || bodyFatDelta > 0.5 ? 'regressing'
    : 'stable'

  const insight = direction === 'improving'
    ? `过去 4 周体重下降 ${Math.abs(weightDelta).toFixed(1)}kg，体脂降低 ${Math.abs(bodyFatDelta).toFixed(1)}%，腰围减少 ${Math.abs(waistDelta).toFixed(1)}cm。训练效果良好，建议保持当前计划。`
    : direction === 'regressing'
    ? `过去 4 周体重增加 ${weightDelta.toFixed(1)}kg，建议检查饮食摄入是否超标，并适当增加有氧训练。`
    : `过去 4 周数据基本稳定，建议调整训练强度或饮食结构以突破平台期。`

  return {
    entries,
    trend: { weightDelta: Number(weightDelta.toFixed(1)), bodyFatDelta: Number(bodyFatDelta.toFixed(1)), waistDelta: Number(waistDelta.toFixed(1)), direction, insight },
    chartData: {
      labels: entries.map(e => `第${e.week}周`),
      weights: entries.map(e => e.weight),
      bodyFats: entries.map(e => e.bodyFatPercent ?? 0),
    },
  }
}
