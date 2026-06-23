import { http } from '@/api/http'

export type DdiWarning = {
  drugA: string
  drugB: string
  severity: 'high' | 'moderate' | 'low'
  description: string
  recommendation: string
}

export type NerEntity = {
  text: string
  label: string
  startOffset: number
  endOffset: number
  confidence: number
}

export async function checkDrugInteractions(): Promise<DdiWarning[]> {
  try {
    const { data } = await http.get<DdiWarning[]>('/medications/interactions')
    return data
  } catch {
    return []
  }
}
