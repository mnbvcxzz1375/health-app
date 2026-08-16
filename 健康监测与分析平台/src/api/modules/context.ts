import { http } from '@/api/http'
import { withMockFallback } from '@/dev/mockApi'

export type MemoryEntry = {
  id: number
  category: string
  content: string
  createdAt: string
}

export type UserHealthBaseline = {
  restingHr: number
  avgSleepScore: number
  avgStressScore: number
  avgVo2Max: number
  avgSteps: number
  riskScore: number
  riskLevel: string
}

export type MedicationContextSummary = {
  activeCount: number
  medicationNames: string[]
  warnings: string[]
}

export type InteractionMemorySummary = {
  totalInteractions: number
  recentTopics: string[]
  lastInteractionAt: string
}

export type PatientMemoryItem = {
  id: number
  tier: 'long_term' | 'care_cycle' | 'encounter'
  memoryType: string
  content: string
  source: string
  safetyLevel: 'routine' | 'elevated' | 'high' | 'critical'
  confirmedByUser: boolean
  safetyCritical: boolean
  effectiveAt: string
  expiresAt: string
}

export type PatientMemoryBrief = {
  longTerm: PatientMemoryItem[]
  careCycle: PatientMemoryItem[]
  encounter: PatientMemoryItem[]
  safetyFacts: PatientMemoryItem[]
}

export type ContextSnapshot = {
  systemSummary: string
  dailySummary: string
  activeConcerns: string[]
  currentMedications: string[]
  memories: MemoryEntry[]
  healthBaseline: UserHealthBaseline
  medicationSummary: MedicationContextSummary
  interactionSummary: InteractionMemorySummary
  patientMemory: PatientMemoryBrief
}

export async function getContextSnapshot(): Promise<ContextSnapshot> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<ContextSnapshot>('/context/snapshot')
      return data
    },
    () => ({
      systemSummary: '用户目前整体健康状况良好，主要关注睡眠质量和日常活动量。',
      dailySummary: '今日心率正常，睡眠时长 7.2 小时，步数 8500 步。',
      activeConcerns: ['睡眠深度不足', '久坐时间偏长'],
      currentMedications: ['氨氯地平 5mg', '阿司匹林 100mg'],
      memories: [],
      healthBaseline: {
        restingHr: 68, avgSleepScore: 78, avgStressScore: 32, avgVo2Max: 42, avgSteps: 8000, riskScore: 18, riskLevel: 'low',
      },
      medicationSummary: { activeCount: 2, medicationNames: ['氨氯地平', '阿司匹林'], warnings: [] },
      interactionSummary: { totalInteractions: 0, recentTopics: [], lastInteractionAt: '' },
      patientMemory: { longTerm: [], careCycle: [], encounter: [], safetyFacts: [] },
    }),
    true,
  )
}

export async function getPatientMemory(): Promise<PatientMemoryBrief> {
  const { data } = await http.get<PatientMemoryBrief>('/context/patient-memory')
  return data
}

export async function savePatientMemory(payload: {
  memoryType: string
  content: string
  source?: string
  confirmedByUser?: boolean
  safetyLevel?: PatientMemoryItem['safetyLevel']
}): Promise<PatientMemoryItem> {
  const { data } = await http.post<PatientMemoryItem>('/context/patient-memory', payload)
  return data
}

export async function retirePatientMemory(id: number): Promise<void> {
  await http.delete(`/context/patient-memory/${id}`)
}

export async function saveContextMemory(payload: {
  category: string
  content: string
}): Promise<void> {
  return withMockFallback(
    async () => { await http.post('/context/memory/save', payload) },
    () => {},
    true,
  )
}

export async function refreshContextMemory(): Promise<void> {
  return withMockFallback(
    async () => { await http.post('/context/memory/refresh') },
    () => {},
    true,
  )
}
