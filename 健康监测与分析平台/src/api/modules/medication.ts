import { http } from '@/api/http'
import { cloneMock, getMockDb, nextMockEntityId, withMockFallback } from '@/dev/mockApi'

export type MedicationReminder = {
  id: number
  time: string
  enabled: boolean
}

export type MedicationItem = {
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
  reminders: MedicationReminder[]
}

export type MedicationReminderInput = {
  time: string
  enabled: boolean
}

export type MedicationPayload = Omit<MedicationItem, 'id' | 'reminders'> & {
  id?: number
  reminders: MedicationReminderInput[]
}

export type MedicationRecognitionResult = {
  name: string
  alias: string
  dosageValue: number | null
  dosageUnit: string
  usage: string
  notes: string
  photoUrl: string
  confidence?: number | null
  sourceText?: string
}

export type MedicationAlarmDrug = Omit<MedicationItem, 'reminders'>

export type MedicationAlarmDrugInput = Omit<MedicationAlarmDrug, 'id' | 'dosageValue'> & {
  dosageValue: number | null
}

export type MedicationAlarm = {
  id: number
  time: string
  enabled: boolean
  medications: MedicationAlarmDrug[]
}

export type MedicationAlarmPayload = {
  time: string
  enabled: boolean
  medications: Array<MedicationAlarmDrugInput & { id?: number }>
}

export type MedicationRecognitionBatchResult = {
  items: MedicationRecognitionResult[]
  confidence?: number | null
}

const textMap: Record<string, string> = {
  'Blood Pressure Med': '降压药',
  Calcium: '钙片',
  'White Tablet': '小白片',
  Supplement: '补充剂',
  tablet: '片',
  capsule: '粒',
  ml: '毫升',
  drop: '滴',
  bag: '袋',
  after_meal: '饭后',
  with_meal: '随餐',
  before_meal: '饭前',
  bedtime: '睡前',
  as_needed: '按需',
  'Avoid taking with milk': '避免与牛奶同服',
  'Keep one hour away from coffee': '与咖啡间隔 1 小时',
}

const validUnits = new Set(['片', '粒', '毫升', '滴', '袋'])
const validUsages = new Set(['饭前', '饭后', '随餐', '睡前', '按需'])

const createDefaultAlarmDrug = (): MedicationAlarmDrugInput => ({
  name: '',
  alias: '',
  dosageValue: null,
  dosageUnit: '',
  usage: '',
  notes: '',
  photoUrl: '',
  enableOcr: false,
  enableYolo: false,
  ocrEndpoint: '',
  yoloEndpoint: '',
  enabled: true,
})

function translateText(value: unknown): string {
  const text = String(value ?? '').trim()
  return textMap[text] ?? text
}

function normalizeDosageUnit(value: unknown): string {
  const text = translateText(value)
  return validUnits.has(text) ? text : ''
}

function normalizeUsage(value: unknown): string {
  const text = translateText(value)
  return validUsages.has(text) ? text : ''
}

function normalizeDosageValue(value: unknown): number | null {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) return null
  return Math.min(12, Math.round(parsed))
}

function normalizeMedicationItem(item: MedicationItem): MedicationItem {
  return {
    ...item,
    name: translateText(item.name),
    alias: translateText(item.alias),
    dosageUnit: normalizeDosageUnit(item.dosageUnit) || item.dosageUnit,
    usage: normalizeUsage(item.usage) || item.usage,
    notes: translateText(item.notes),
    reminders: item.reminders.map((reminder) => ({
      ...reminder,
      time: String(reminder.time ?? ''),
    })),
  }
}

function normalizeMedicationRecognition(result: MedicationRecognitionResult): MedicationRecognitionResult {
  return {
    name: translateText(result.name),
    alias: translateText(result.alias),
    dosageValue: normalizeDosageValue(result.dosageValue),
    dosageUnit: normalizeDosageUnit(result.dosageUnit),
    usage: normalizeUsage(result.usage),
    notes: translateText(result.notes),
    photoUrl: String(result.photoUrl ?? '').trim(),
    confidence: typeof result.confidence === 'number' ? result.confidence : null,
    sourceText: String(result.sourceText ?? '').trim(),
  }
}

function normalizeMedicationAlarmDrug(item: MedicationAlarmDrug): MedicationAlarmDrug {
  return {
    ...item,
    name: translateText(item.name),
    alias: translateText(item.alias),
    dosageUnit: normalizeDosageUnit(item.dosageUnit) || item.dosageUnit,
    usage: normalizeUsage(item.usage) || item.usage,
    notes: translateText(item.notes),
  }
}

function normalizeMedicationAlarm(alarm: MedicationAlarm): MedicationAlarm {
  return {
    ...alarm,
    time: String(alarm.time ?? ''),
    medications: alarm.medications.map(normalizeMedicationAlarmDrug),
  }
}

function nextReminderId(): number {
  const db = getMockDb()
  const maxId = Math.max(0, ...db.medications.flatMap((item) => item.reminders.map((rem) => rem.id)))
  return maxId + 1
}

function buildMockMedication(id: number, payload: MedicationPayload): MedicationItem {
  let reminderId = nextReminderId()
  return {
    id,
    name: payload.name,
    alias: payload.alias,
    dosageValue: payload.dosageValue,
    dosageUnit: payload.dosageUnit,
    usage: payload.usage,
    notes: payload.notes,
    photoUrl: payload.photoUrl,
    enableOcr: payload.enableOcr,
    enableYolo: payload.enableYolo,
    ocrEndpoint: payload.ocrEndpoint,
    yoloEndpoint: payload.yoloEndpoint,
    enabled: payload.enabled,
    reminders: payload.reminders.map((item) => ({
      id: reminderId++,
      time: item.time,
      enabled: item.enabled,
    })),
  }
}

function buildMockRecognitionResult(files: File[]): MedicationRecognitionResult {
  return {
    name: '',
    alias: '',
    dosageValue: null,
    dosageUnit: '',
    usage: '',
    notes: '',
    photoUrl: '',
    sourceText: files.map((file) => file.name.replace(/\.[^.]+$/, '')).join(' '),
  }
}

let mockAlarmIdSeed = 0
let mockAlarmCache: MedicationAlarm[] | null = null

function buildMockAlarmsFromLegacy(): MedicationAlarm[] {
  const db = getMockDb()
  const alarmMap = new Map<string, MedicationAlarm>()

  db.medications.forEach((item) => {
    const normalizedItem = normalizeMedicationItem(item)
    normalizedItem.reminders.forEach((reminder) => {
      const current =
        alarmMap.get(reminder.time) ??
        {
          id: ++mockAlarmIdSeed,
          time: reminder.time,
          enabled: reminder.enabled,
          medications: [],
        }

      current.enabled = current.enabled || reminder.enabled
      current.medications.push({
        id: normalizedItem.id,
        name: normalizedItem.name,
        alias: normalizedItem.alias,
        dosageValue: normalizedItem.dosageValue,
        dosageUnit: normalizedItem.dosageUnit,
        usage: normalizedItem.usage,
        notes: normalizedItem.notes,
        photoUrl: normalizedItem.photoUrl,
        enableOcr: normalizedItem.enableOcr,
        enableYolo: normalizedItem.enableYolo,
        ocrEndpoint: normalizedItem.ocrEndpoint,
        yoloEndpoint: normalizedItem.yoloEndpoint,
        enabled: normalizedItem.enabled,
      })

      alarmMap.set(reminder.time, current)
    })
  })

  return Array.from(alarmMap.values()).sort((a, b) => a.time.localeCompare(b.time))
}

function getMockAlarms(): MedicationAlarm[] {
  if (!mockAlarmCache) {
    mockAlarmCache = buildMockAlarmsFromLegacy()
  }
  return mockAlarmCache
}

function cloneAlarmPayloadToEntity(id: number, payload: MedicationAlarmPayload): MedicationAlarm {
  return {
    id,
    time: payload.time,
    enabled: payload.enabled,
    medications: payload.medications.map((item) => ({
      id: item.id ?? nextMockEntityId('medication'),
      ...createDefaultAlarmDrug(),
      ...item,
      dosageValue: normalizeDosageValue(item.dosageValue) ?? 1,
      dosageUnit: normalizeDosageUnit(item.dosageUnit),
      usage: normalizeUsage(item.usage),
      notes: String(item.notes ?? ''),
      photoUrl: String(item.photoUrl ?? ''),
      enabled: item.enabled !== false,
    })),
  }
}

export async function getMedications(): Promise<MedicationItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<MedicationItem[]>('/medications')
      return data.map(normalizeMedicationItem)
    },
    () => cloneMock(getMockDb().medications).map(normalizeMedicationItem),
  )
}

export async function createMedication(payload: MedicationPayload): Promise<MedicationItem> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<MedicationItem>('/medications', payload)
      return normalizeMedicationItem(data)
    },
    () => {
      const db = getMockDb()
      const item = buildMockMedication(nextMockEntityId('medication'), payload)
      db.medications.unshift(cloneMock(item))
      return normalizeMedicationItem(item)
    },
  )
}

export async function updateMedication(id: number, payload: MedicationPayload): Promise<MedicationItem> {
  return withMockFallback(
    async () => {
      const { data } = await http.put<MedicationItem>(`/medications/${id}`, payload)
      return normalizeMedicationItem(data)
    },
    () => {
      const db = getMockDb()
      const item = buildMockMedication(id, payload)
      const index = db.medications.findIndex((target) => target.id === id)
      if (index === -1) {
        db.medications.unshift(cloneMock(item))
      } else {
        db.medications[index] = cloneMock(item)
      }
      return normalizeMedicationItem(item)
    },
  )
}

export async function toggleMedication(id: number): Promise<{ id: number; enabled: boolean }> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<{ id: number; enabled: boolean }>(`/medications/${id}/toggle`)
      return data
    },
    () => {
      const db = getMockDb()
      const target = db.medications.find((item) => item.id === id)
      if (!target) {
        throw new Error('药品不存在')
      }
      target.enabled = !target.enabled
      return { id: target.id, enabled: target.enabled }
    },
  )
}

export async function deleteMedication(id: number): Promise<void> {
  await withMockFallback(
    async () => {
      await http.delete(`/medications/${id}`)
      return true
    },
    () => {
      const db = getMockDb()
      db.medications = db.medications.filter((item) => item.id !== id)
      return true
    },
  )
}

export async function recognizeMedication(files: File[]): Promise<MedicationRecognitionResult> {
  const result = await recognizeMedicationBatch(files)
  return result.items[0] ?? normalizeMedicationRecognition(buildMockRecognitionResult(files))
}

export async function getMedicationAlarms(): Promise<MedicationAlarm[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<MedicationAlarm[]>('/medication-alarms')
      return data.map(normalizeMedicationAlarm)
    },
    () => cloneMock(getMockAlarms()).map(normalizeMedicationAlarm),
  )
}

export async function createMedicationAlarm(payload: MedicationAlarmPayload): Promise<MedicationAlarm> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<MedicationAlarm>('/medication-alarms', payload)
      return normalizeMedicationAlarm(data)
    },
    () => {
      const next = cloneAlarmPayloadToEntity(++mockAlarmIdSeed, payload)
      getMockAlarms().unshift(cloneMock(next))
      return normalizeMedicationAlarm(next)
    },
  )
}

export async function updateMedicationAlarm(
  id: number,
  payload: MedicationAlarmPayload,
): Promise<MedicationAlarm> {
  return withMockFallback(
    async () => {
      const { data } = await http.put<MedicationAlarm>(`/medication-alarms/${id}`, payload)
      return normalizeMedicationAlarm(data)
    },
    () => {
      const next = cloneAlarmPayloadToEntity(id, payload)
      const alarms = getMockAlarms()
      const index = alarms.findIndex((item) => item.id === id)
      if (index === -1) {
        alarms.unshift(cloneMock(next))
      } else {
        alarms[index] = cloneMock(next)
      }
      return normalizeMedicationAlarm(next)
    },
  )
}

export async function toggleMedicationAlarm(id: number): Promise<{ id: number; enabled: boolean }> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<{ id: number; enabled: boolean }>(`/medication-alarms/${id}/toggle`)
      return data
    },
    () => {
      const target = getMockAlarms().find((item) => item.id === id)
      if (!target) {
        throw new Error('提醒闹钟不存在')
      }
      target.enabled = !target.enabled
      return { id: target.id, enabled: target.enabled }
    },
  )
}

export async function deleteMedicationAlarm(id: number): Promise<void> {
  await withMockFallback(
    async () => {
      await http.delete(`/medication-alarms/${id}`)
      return true
    },
    () => {
      mockAlarmCache = getMockAlarms().filter((item) => item.id !== id)
      return true
    },
  )
}

export async function recognizeMedicationBatch(
  files: File[],
): Promise<MedicationRecognitionBatchResult> {
  if (!files.length) {
    return { items: [] }
  }

  const payload = new FormData()
  files.forEach((file) => payload.append('files', file))
  const { data } = await http.post<MedicationRecognitionBatchResult>(
    '/medications/recognize',
    payload,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120_000,
    },
  )

  return {
    items: Array.isArray(data.items) ? data.items.map(normalizeMedicationRecognition) : [],
  }
}

// === Phase 4: 药明白 API ===

export type MedicationExplainResponse = {
  clinicalParse: string
  elderFriendlyExplanation: string
  warnings: string[]
}

export type TodayScheduleItem = {
  alarmId: number
  time: string
  enabled: boolean
  medications: MedicationAlarmDrug[]
  intakeStatus: 'pending' | 'taken' | 'skipped' | 'half'
}

export type TodayScheduleResponse = {
  date: string
  items: TodayScheduleItem[]
  totalCount: number
  completedCount: number
}

export async function explainMedication(name: string, notes?: string): Promise<MedicationExplainResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<MedicationExplainResponse>('/medications/explain', { name, notes })
      return data
    },
    () => ({
      clinicalParse: `药品名称：${name}\n适应症：暂无详细信息\n用法用量：请遵医嘱\n不良反应：暂无数据\n禁忌：请咨询医生`,
      elderFriendlyExplanation: `${name}的具体用法请查看药盒说明或咨询医生。`,
      warnings: ['请遵医嘱用药', '如有不适及时就医'],
    }),
  )
}

export async function confirmMedicationIntake(
  alarmId: number,
  status: 'taken' | 'skipped' | 'half',
): Promise<{ success: boolean; alarmId: number; status: string }> {
  return withMockFallback(
    async () => {
      const { data } = await http.post('/medications/confirm-intake', { alarmId, status })
      return data as { success: boolean; alarmId: number; status: string }
    },
    () => ({ success: true, alarmId, status }),
  )
}

export async function getTodayMedicationSchedule(): Promise<TodayScheduleResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<TodayScheduleResponse>('/medications/today')
      return data
    },
    () => {
      const db = getMockDb()
      // Build alarm-like schedule from medication reminders
      const timeMap = new Map<string, { alarmId: number; medications: MedicationAlarmDrug[] }>()
      let alarmIdSeq = 1
      for (const med of db.medications) {
        for (const rem of med.reminders) {
          if (!rem.enabled) continue
          let entry = timeMap.get(rem.time)
          if (!entry) {
            entry = { alarmId: alarmIdSeq++, medications: [] }
            timeMap.set(rem.time, entry)
          }
          entry.medications.push({
            id: med.id,
            name: med.name,
            alias: med.alias ?? '',
            dosageValue: med.dosageValue ?? 1,
            dosageUnit: med.dosageUnit ?? '片',
            usage: med.usage ?? '饭后',
            notes: med.notes ?? '',
            photoUrl: '',
            enableOcr: false,
            enableYolo: false,
            ocrEndpoint: '',
            yoloEndpoint: '',
            enabled: med.enabled ?? true,
          })
        }
      }
      const items: TodayScheduleItem[] = Array.from(timeMap.entries())
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([time, { alarmId, medications }]) => ({
          alarmId,
          time,
          enabled: true,
          medications,
          intakeStatus: 'pending' as const,
        }))
      return {
        date: new Date().toISOString().slice(0, 10),
        items,
        totalCount: items.length,
        completedCount: 0,
      }
    },
  )
}
