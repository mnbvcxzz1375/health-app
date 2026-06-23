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
  const { data } = await http.get<RehabPerformanceAnalysis>('/rehab/analysis')
  return data
}
