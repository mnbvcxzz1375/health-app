import { postureHttp } from '@/api/postureHttp'

export type ExerciseType = 'SQUAT' | 'PUSH_UP' | 'PLANK' | 'LUNGE'
export type CameraView = 'SIDE' | 'FRONT' | 'ANGLED'
export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'LOW_CONFIDENCE'
export type Verdict = 'STANDARD' | 'NEEDS_IMPROVEMENT' | 'LOW_CONFIDENCE'
export type Severity = 'MAJOR' | 'MEDIUM' | 'MINOR'

export type PostureJob = {
  jobId: string
  status: JobStatus
}

export type PostureJobStatus = {
  jobId: string
  status: JobStatus
  progress: number
  failReason: string | null
}

export type PostureIssue = {
  code: string
  severity: Severity
  phase: string
  metricName: string
  actualValue: number
  targetRange: string
  evidenceTimestampMs: number
  description: string
}

export type PostureRepAnalysis = {
  repIndex: number
  startMs: number
  endMs: number
  score: number
  issues: PostureIssue[]
}

export type PostureEvidenceFrame = {
  label: string
  timestampMs: number
  imageUrl: string | null
}

export type PostureReport = {
  exerciseType: ExerciseType
  score: number
  verdict: Verdict
  summary: string | null
  issues: PostureIssue[]
  suggestions: string[]
  warnings: string[]
  reps: PostureRepAnalysis[]
  evidenceFrames: PostureEvidenceFrame[]
  validFrameRatio: number
}

function toErrorMessage(error: unknown, fallback: string): Error {
  if (typeof error === 'object' && error !== null) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    const message = response?.data?.message
    if (typeof message === 'string' && message.trim()) {
      return new Error(message)
    }
  }
  if (error instanceof Error && error.message.trim()) {
    return new Error(error.message)
  }
  return new Error(fallback)
}

export async function createPostureJob(payload: {
  userId: string
  exerciseType: ExerciseType
  cameraView: CameraView
  videoFile: File
}): Promise<PostureJob> {
  const formData = new FormData()
  formData.append('userId', payload.userId)
  formData.append('exerciseType', payload.exerciseType)
  formData.append('cameraView', payload.cameraView)
  formData.append('videoFile', payload.videoFile)

  try {
    const { data } = await postureHttp.post<PostureJob>('/api/v1/posture/jobs', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  } catch (error) {
    throw toErrorMessage(error, '动作纠错任务创建失败')
  }
}

export async function getPostureJobStatus(jobId: string): Promise<PostureJobStatus> {
  try {
    const { data } = await postureHttp.get<PostureJobStatus>(`/api/v1/posture/jobs/${jobId}`)
    return data
  } catch (error) {
    throw toErrorMessage(error, '动作纠错任务状态获取失败')
  }
}

export async function getPostureReport(jobId: string): Promise<PostureReport> {
  try {
    const { data } = await postureHttp.get<PostureReport>(`/api/v1/posture/jobs/${jobId}/report`)
    return data
  } catch (error) {
    throw toErrorMessage(error, '动作纠错报告获取失败')
  }
}
