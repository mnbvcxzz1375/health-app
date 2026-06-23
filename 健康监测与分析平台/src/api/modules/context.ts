import { http } from '@/api/http'

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

export type ContextSnapshot = {
  systemSummary: string
  dailySummary: string
  activeConcerns: string[]
  currentMedications: string[]
  memories: MemoryEntry[]
  healthBaseline: UserHealthBaseline
  medicationSummary: MedicationContextSummary
  interactionSummary: InteractionMemorySummary
}

export async function getContextSnapshot(): Promise<ContextSnapshot> {
  const { data } = await http.get<ContextSnapshot>('/context/snapshot')
  return data
}

export async function saveContextMemory(payload: {
  category: string
  content: string
}): Promise<void> {
  await http.post('/context/memory/save', payload)
}

export async function refreshContextMemory(): Promise<void> {
  await http.post('/context/memory/refresh')
}
