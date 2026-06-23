import { http } from '@/api/http'

export type CategoryScore = {
  key: string
  label: string
  score: number
  currentValue: number
  baselineValue: number
  offset: number
  riskNote: string
  attentionType: string
  weight: number
}

export type TopRisk = {
  attentionType: string
  label: string
  description: string
  severity: number
}

export type HealthScoreResponse = {
  overallScore: number
  overallRisk: string
  categoryScores: CategoryScore[]
  topRisks: TopRisk[]
  recommendedActions: string[]
  summary: string
}

export async function getHealthScore(): Promise<HealthScoreResponse> {
  const { data } = await http.get<HealthScoreResponse>('/health/score')
  return data
}
