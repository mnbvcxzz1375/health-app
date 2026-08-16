import { http } from '@/api/http'
import { env } from '@/config/env'
import { cloneMock, getMockDb, nextMockTaskId } from '@/dev/mockApi'
import { normalizeRehabPlanDraft, type RehabPlanDraft } from '@/api/modules/rehab'

type UploadType = 'image' | 'lab' | 'text' | 'symptom'

export type AnalyzeTask = {
  taskId: string
}

export type AnalyzeReport = {
  title: string
  summary: string
  riskLevel: string
  points: string[]
  advice: string[]
  rehabFocus: string
  followUp: string[]
  caution: string
}

export type AnalyzeResult = {
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  points?: string[]
  advice?: string[]
  report?: AnalyzeReport
  saved?: boolean
  message?: string
}

export type SavedAnalyzeReport = {
  taskId: string
  type: UploadType
  fileName: string
  createdAt: string
  updatedAt: string
  report: AnalyzeReport
}

export type AnalyzeSaveResponse = {
  success: boolean
  saved: boolean
  rehabPlanDraft: RehabPlanDraft
}

// ===== 骨龄评估 =====

export type BoneAgeResult = {
  estimatedAgeYears: number | null
  confidence: number | null
  growthPlateStage: string
  malformedIndicators: string[]
  disclaimer: string
}

export type BoneAgeEstimateResponse = {
  taskId: string
  type: 'bone'
  source: 'local_model' | 'llm_fallback' | string
  result: BoneAgeResult
  estimatedAt?: string
}

export type BoneAgeTaskRecord = {
  taskId: string
  imageName: string
  estimatedAge: number | null
  confidence: number | null
  growthPlateStage: string
  indicators: string[]
  source: string
  createdAtIso: string
}

function buildMockRehabPlanDraft(taskId: string, report: AnalyzeReport): RehabPlanDraft {
  const db = getMockDb()

  return {
    sourceTaskIds: [taskId],
    summary: {
      focus: report.rehabFocus,
      frequency: db.rehabPlanSettings.frequency,
      duration: db.rehabPlanSettings.duration,
      intensity: db.rehabPlanSettings.intensity,
    },
    exercises: cloneMock(db.rehabPlan.exercises).slice(0, 4).map((exercise, index) => ({
      mode: index < 2 ? 'existing' : 'generated',
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
    })),
    reminder: cloneMock(db.rehabPlanReminder),
  }
}

function buildMockReport(type: UploadType): AnalyzeReport {
  const db = getMockDb()
  const template = db.analyzeTemplates[type] ?? db.analyzeTemplates.text
  const titleMap: Record<UploadType, string> = {
    image: '影像资料分析报告',
    lab: '化验报告分析报告',
    text: '文字报告分析报告',
    symptom: '症状描述分析报告',
  }

  return {
    title: titleMap[type],
    summary: '已根据当前上传资料生成结构化分析，请结合实际症状和正式检查结论综合判断。',
    riskLevel: type === 'symptom' ? '中等风险' : '低风险',
    points: cloneMock(template.points),
    advice: cloneMock(template.advice),
    rehabFocus: '先进行低到中强度恢复，避免短时间内盲目加量。',
    followUp: ['保留当前报告便于后续对比。', '如症状持续或加重，建议尽快线下复诊。'],
    caution: '以上结果仅用于健康管理辅助，不替代医生诊疗与正式报告结论。',
  }
}

export async function createAnalyzeTask(payload: FormData): Promise<AnalyzeTask> {
  if (env.useDevMock) {
    const db = getMockDb()
    const type = String(payload.get('type') ?? 'text') as UploadType
    const taskId = nextMockTaskId('analyze')

    db.analyzeTasks[taskId] = {
      status: 'RUNNING',
      points: [],
      advice: [],
      report: buildMockReport(type),
      saved: false,
      polls: 0,
    }

    return { taskId }
  }

  const { data } = await http.post<AnalyzeTask>('/analyze/tasks', payload, {
    timeout: 120_000,
  })
  return data
}

export async function getAnalyzeResult(taskId: string): Promise<AnalyzeResult> {
  if (env.useDevMock) {
    const db = getMockDb()
    const task = db.analyzeTasks[taskId]
    if (!task) {
      return {
        status: 'FAILED',
        message: '任务不存在或已过期',
      }
    }

    task.polls += 1
    if (task.polls >= 2) {
      task.status = 'DONE'
      task.points = cloneMock(task.report?.points ?? [])
      task.advice = cloneMock(task.report?.advice ?? [])
    }

    return {
      status: task.status,
      points: cloneMock(task.points ?? []),
      advice: cloneMock(task.advice ?? []),
      report: task.report ? cloneMock(task.report) : undefined,
      saved: Boolean(task.saved),
    }
  }

  const { data } = await http.get<AnalyzeResult>(`/analyze/tasks/${taskId}`)
  return data
}

export async function saveAnalyzeReport(taskId: string): Promise<AnalyzeSaveResponse> {
  if (env.useDevMock) {
    const db = getMockDb()
    const task = db.analyzeTasks[taskId]
    if (!task) {
      throw new Error('任务不存在')
    }
    task.saved = true
    return {
      success: true,
      saved: true,
      rehabPlanDraft: normalizeRehabPlanDraft(buildMockRehabPlanDraft(taskId, task.report as AnalyzeReport)),
    }
  }

  const { data } = await http.post<AnalyzeSaveResponse>(`/analyze/tasks/${taskId}/save`, undefined, {
    timeout: 180_000,
  })
  return {
    ...data,
    rehabPlanDraft: normalizeRehabPlanDraft(data.rehabPlanDraft),
  }
}

export async function discardAnalyzeReport(taskId: string): Promise<void> {
  if (env.useDevMock) {
    const db = getMockDb()
    delete db.analyzeTasks[taskId]
    return
  }

  await http.delete(`/analyze/tasks/${taskId}`)
}

export async function getSavedAnalyzeReports(): Promise<SavedAnalyzeReport[]> {
  if (env.useDevMock) {
    const db = getMockDb()
    return Object.entries(db.analyzeTasks)
      .filter(([, task]) => Boolean(task.saved && task.report))
      .map(([taskId, task]) => ({
        taskId,
        type: 'text',
        fileName: '',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        report: cloneMock(task.report as AnalyzeReport),
      }))
  }

  const { data } = await http.get<SavedAnalyzeReport[]>('/analyze/reports')
  return data
}

// ===== 骨龄评估 API =====

/**
 * 上传 X 光图片进行骨龄评估。
 * 调用 POST /api/bone-age/estimate（不走 /api/analyze/tasks/custom-model，
 * 因为骨龄评估是同步返回完整结果的，无需轮询）。
 */
export async function estimateBoneAge(file: File): Promise<BoneAgeEstimateResponse> {
  if (env.useDevMock) {
    return mockEstimateBoneAge(file)
  }

  const formData = new FormData()
  formData.append('file', file)

  // 不要手动设置 Content-Type，让 Axios 自动生成带 boundary 的 multipart/form-data
  const { data } = await http.post<BoneAgeEstimateResponse>('/bone-age/estimate', formData, {
    timeout: 120_000,
  })
  return data
}

/** 查询最近骨龄评估记录（最多 50 条） */
export async function listRecentBoneAgeTasks(limit = 10): Promise<BoneAgeTaskRecord[]> {
  if (env.useDevMock) {
    return mockListRecentBoneAgeTasks(limit)
  }

  const { data } = await http.get<BoneAgeTaskRecord[]>('/bone-age/recent', {
    params: { limit },
  })
  return data
}

// ===== 骨龄评估 Mock =====

const mockBoneAgeRecords: BoneAgeTaskRecord[] = []

function mockEstimateBoneAge(file: File): BoneAgeEstimateResponse {
  // Keep the development fixture deterministic; it is never used in production.
  const age = 10.4
  const confidence = 0.86

  const stageMap: Array<[number, string]> = [
    [2, '婴幼儿期 (<2)'],
    [6, '儿童早期 (2-6)'],
    [10, '儿童晚期 (6-10)'],
    [13, '青春期前期 (10-13)'],
    [16, '青春期 (13-16)'],
    [18, '青春期后期 (16-18)'],
    [Number.POSITIVE_INFINITY, '骨骺闭合期 (≥18)'],
  ]
  const stage = stageMap.find(([threshold]) => age < threshold)?.[1] ?? '未知'

  const indicators: string[] = ['开发模式示例结果，请使用真实骨龄服务重新评估']

  const result: BoneAgeResult = {
    estimatedAgeYears: age,
    confidence,
    growthPlateStage: stage,
    malformedIndicators: indicators,
    disclaimer:
      '本结果由 AI 模型自动评估，仅供参考，不能替代专业医师的临床判断。骨龄评估受拍摄角度、光质、个体差异等因素影响，请以执业医师出具的报告为准。',
  }

  const taskId = `bone_mock_${String(mockBoneAgeRecords.length + 1).padStart(4, '0')}`

  // 写入历史记录（用于 listRecentBoneAgeTasks mock）
  mockBoneAgeRecords.unshift({
    taskId,
    imageName: file.name,
    estimatedAge: age,
    confidence,
    growthPlateStage: stage,
    indicators,
    source: 'dev_fixture',
    createdAtIso: new Date().toISOString(),
  })

  return {
    taskId,
    type: 'bone',
    source: 'dev_fixture',
    result,
    estimatedAt: new Date().toISOString(),
  }
}

function mockListRecentBoneAgeTasks(limit: number): BoneAgeTaskRecord[] {
  return mockBoneAgeRecords.slice(0, limit)
}
