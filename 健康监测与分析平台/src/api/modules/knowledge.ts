import { http } from '@/api/http'
import { withMockFallback } from '@/dev/mockApi'

// ===== 中药材 =====
export type HerbSearchItem = {
  id: number
  name: string
  pinyin: string
  alias: string
  nature: string
  flavor: string
  meridian: string
  efficacy: string
}

// ===== 方剂 =====
export type FormulaHerbInput = {
  herbName: string
  dosageGrams?: number | null
  role?: string
}

export type FormulaSaveRequest = {
  name: string
  diagnosis?: string
  herbs: FormulaHerbInput[]
  notes?: string
}

export type FormulaHerbItem = {
  herbId: number
  herbName: string
  pinyin: string
  nature: string
  flavor: string
  meridian: string
  efficacy: string
  dosageGrams?: number | null
  role?: string
}

export type FormulaResponse = {
  id: number
  name: string
  diagnosis?: string
  herbs: FormulaHerbItem[]
  notes?: string
  createdAt: string
}

export type FormulaListItem = {
  id: number
  name: string
  herbCount: number
  createdAt: string
}

// ===== 多药材识别 =====
export type HerbRecognitionItem = {
  herbName: string
  pinyin: string
  nature: string
  flavor: string
  meridian: string
  efficacy: string
  confidence: number | null
  source: string
}

export type HerbRecognitionResult = {
  items: HerbRecognitionItem[]
  duplicatesRemoved: string[]
  confidence: number | null
}

// ===== 药品临床信息 =====
export type ClinicalInfoResponse = {
  drugName: string
  medicineType: string
  ingredients: string[]
  indications: string
  sideEffects: string[]
  allergicReactions: string[]
  contraindicatedGroups: string[]
  contraindications: string
  interactions: string[]
  dietaryTaboos: string[]
  dosingIntervalMinutes: number | null
  source: string
}

// ===== 交互报告 =====
export type InteractionRecord = {
  type: string
  severity: string
  drugA: string
  drugB: string
  description: string
  source: string
}

export type InteractionReport = {
  tcmIncompatibilities: InteractionRecord[]
  tcmWmInteractions: InteractionRecord[]
  drugFoodInteractions: InteractionRecord[]
  ddiWarnings: InteractionRecord[]
  allergyConflicts: InteractionRecord[]
  contraindicatedGroupWarnings: InteractionRecord[]
  totalWarnings: number
  summary: string[]
}

// ===== 用药间隔 =====
export type DosingScheduleItem = {
  drugName: string
  medicineType: string
  suggestedTime: string
  intervalMinutes: number | null
  reason: string
}

export type DosingSchedule = {
  date: string
  morning: DosingScheduleItem[]
  noon: DosingScheduleItem[]
  evening: DosingScheduleItem[]
  notes: string[]
}

// ===== 过敏 =====
export type AllergySaveRequest = {
  allergen: string
  allergenType?: string
  severity?: string
  note?: string
}

export type AllergyItem = {
  id: number
  allergen: string
  allergenType: string
  severity: string
  note: string
}

// ===== API 函数 =====

export async function searchHerbs(keyword: string, limit = 20): Promise<HerbSearchItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<HerbSearchItem[]>('/knowledge/herbs/search', {
        params: { keyword, limit },
      })
      return data
    },
    () => [],
  )
}

export async function getHerbByName(name: string): Promise<HerbSearchItem | null> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<HerbSearchItem>(`/knowledge/herbs/${encodeURIComponent(name)}`)
      return data
    },
    () => null,
  )
}

export async function getClinicalInfo(drugName: string): Promise<ClinicalInfoResponse | null> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<ClinicalInfoResponse>(`/knowledge/clinical/${encodeURIComponent(drugName)}`)
      return data
    },
    () => null,
  )
}

export async function createFormula(payload: FormulaSaveRequest): Promise<FormulaResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<FormulaResponse>('/knowledge/formulas', payload)
      return data
    },
    () => ({
      id: Date.now(),
      name: payload.name,
      diagnosis: payload.diagnosis ?? '',
      herbs: payload.herbs.map((h) => ({
        herbId: 0,
        herbName: h.herbName,
        pinyin: '',
        nature: '',
        flavor: '',
        meridian: '',
        efficacy: '',
        dosageGrams: h.dosageGrams ?? null,
        role: h.role ?? '',
      })),
      notes: payload.notes ?? '',
      createdAt: new Date().toISOString(),
    }),
  )
}

export async function listFormulas(): Promise<FormulaListItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<FormulaListItem[]>('/knowledge/formulas')
      return data
    },
    () => [],
  )
}

export async function getFormula(id: number): Promise<FormulaResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<FormulaResponse>(`/knowledge/formulas/${id}`)
      return data
    },
    () => {
      throw new Error('方剂不存在')
    },
  )
}

export async function deleteFormula(id: number): Promise<{ deleted: boolean }> {
  return withMockFallback(
    async () => {
      const { data } = await http.delete<{ deleted: boolean }>(`/knowledge/formulas/${id}`)
      return data
    },
    () => ({ deleted: true }),
  )
}

export async function recognizeHerbs(file: File): Promise<HerbRecognitionResult> {
  return withMockFallback(
    async () => {
      const formData = new FormData()
      formData.append('file', file)
      const { data } = await http.post<HerbRecognitionResult>('/knowledge/herbs/recognize', formData, {
        timeout: 60_000,
      })
      return data
    },
    () => ({
      items: [],
      duplicatesRemoved: [],
      confidence: 0,
    }),
  )
}

export async function getInteractionReport(): Promise<InteractionReport> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<InteractionReport>('/knowledge/interactions')
      return data
    },
    () => ({
      tcmIncompatibilities: [],
      tcmWmInteractions: [],
      drugFoodInteractions: [],
      ddiWarnings: [],
      allergyConflicts: [],
      contraindicatedGroupWarnings: [],
      totalWarnings: 0,
      summary: ['当前没有用药记录，无需检查交互。'],
    }),
  )
}

export async function getDosingSchedule(): Promise<DosingSchedule> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<DosingSchedule>('/knowledge/dosing-schedule')
      return data
    },
    () => ({
      date: new Date().toISOString().slice(0, 10),
      morning: [],
      noon: [],
      evening: [],
      notes: ['暂无用药数据。'],
    }),
  )
}

export async function listAllergies(): Promise<AllergyItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<AllergyItem[]>('/knowledge/allergies')
      return data
    },
    () => [],
  )
}

export async function addAllergy(payload: AllergySaveRequest): Promise<AllergyItem> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<AllergyItem>('/knowledge/allergies', payload)
      return data
    },
    () => ({
      id: Date.now(),
      allergen: payload.allergen,
      allergenType: payload.allergenType ?? '',
      severity: payload.severity ?? '',
      note: payload.note ?? '',
    }),
  )
}

export async function removeAllergy(id: number): Promise<{ deleted: boolean }> {
  return withMockFallback(
    async () => {
      const { data } = await http.delete<{ deleted: boolean }>(`/knowledge/allergies/${id}`)
      return data
    },
    () => ({ deleted: true }),
  )
}
