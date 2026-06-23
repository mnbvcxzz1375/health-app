<template>
  <div class="space-y-5 pb-6 text-slate-950" :class="{ 'elder-mode': elderMode }">
    <ClinicalFeatureNavBar title="药明白" back-to="/home" />

    <!-- Header with elder mode toggle -->
    <div class="flex items-center justify-between px-1">
      <p class="text-sm text-slate-500">扫描识别 · 用药管理 · 服药记录</p>
      <button
        type="button"
        class="flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium transition-all"
        :class="elderMode
          ? 'border-teal-500 bg-teal-50 text-teal-700 shadow-sm'
          : 'border-slate-200 bg-white text-slate-500 hover:border-teal-300 hover:text-teal-600'"
        @click="elderMode = !elderMode"
      >
        <iconify-icon :icon="elderMode ? 'solar:eye-bold' : 'solar:eye-outline'" width="14" height="14" />
        {{ elderMode ? '老人模式 ON' : '老人模式' }}
      </button>
    </div>

    <!-- Custom Tab Bar -->
    <div class="flex gap-1 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-1">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        class="flex flex-1 items-center justify-center gap-1.5 rounded-[1rem] px-3 py-2.5 text-sm font-medium transition-all"
        :class="activeTab === tab.value
          ? 'bg-white text-teal-800 shadow-sm'
          : 'text-slate-500 hover:text-slate-700'"
        @click="activeTab = tab.value"
      >
        <iconify-icon :icon="tab.icon" width="16" height="16" />
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab: Scan Recognition -->
    <div v-if="activeTab === 'scan'" class="space-y-4">
      <ClinicalSurfaceCard title="扫描药盒或说明书">
        <p class="mb-4 text-sm text-slate-600">拍摄药盒照片，AI 自动识别药品信息并生成老人可读版本。</p>
        <input
          ref="scanInput"
          type="file"
          accept="image/*"
          capture="environment"
          multiple
          class="hidden"
          @change="handleScanUpload"
        />
        <div class="flex gap-3">
          <Button @click="triggerScan">
            <iconify-icon icon="solar:camera-outline" width="16" height="16" />
            拍照识别
          </Button>
          <Button variant="secondary" @click="triggerFileSelect">
            <iconify-icon icon="solar:gallery-add-outline" width="16" height="16" />
            选择图片
          </Button>
        </div>
      </ClinicalSurfaceCard>

      <!-- Scan results -->
      <ClinicalSurfaceCard v-if="scanResults.length" title="识别结果">
        <p class="mb-3 text-xs text-slate-500">AI 正在根据药盒信息生成用药建议…</p>
        <div class="space-y-4">
          <div
            v-for="(result, idx) in scanResults"
            :key="idx"
            class="rounded-[1.15rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-4"
          >
            <!-- Drug header -->
            <div class="flex items-center gap-2">
              <iconify-icon icon="solar:pills-3-outline" width="20" height="20" class="text-teal-700" />
              <span class="text-base font-semibold">{{ result.name }}</span>
              <span v-if="result.confidence" class="rounded-full bg-teal-50 px-2 py-0.5 text-xs text-teal-700">
                {{ Math.round(result.confidence * 100) }}%
              </span>
            </div>
            <div class="mt-2 space-y-1 text-sm text-slate-600">
              <p v-if="result.alias">别名：{{ result.alias }}</p>
              <p>用量：{{ result.dosageValue ?? 1 }} {{ result.dosageUnit || '片' }} · {{ result.usage || '饭后' }}</p>
              <p v-if="result.notes">{{ result.notes }}</p>
            </div>

            <!-- OCR source text (collapsible) -->
            <details v-if="result.sourceText" class="mt-2">
              <summary class="cursor-pointer text-xs text-slate-400 hover:text-slate-600">查看药盒原文</summary>
              <p class="mt-1 rounded-lg bg-slate-100 p-2 text-xs leading-5 text-slate-500">{{ result.sourceText }}</p>
            </details>

            <!-- AI explanation (auto-loaded) -->
            <div v-if="scanExplanations[idx]" class="mt-3 space-y-2">
              <div class="flex items-center gap-2">
                <iconify-icon icon="solar:shield-check-outline" width="16" height="16" class="text-teal-600" />
                <span class="text-sm font-medium text-teal-700">AI 用药建议</span>
                <button
                  type="button"
                  class="ml-auto flex h-7 w-7 items-center justify-center rounded-full bg-teal-50 text-teal-700 transition hover:bg-teal-100"
                  @click="speakText(scanExplanations[idx].elderFriendlyExplanation)"
                  title="语音朗读"
                >
                  <iconify-icon icon="solar:volume-loud-outline" width="14" height="14" />
                </button>
              </div>

              <!-- Elder-friendly explanation -->
              <div class="rounded-[1rem] border-2 border-teal-200 bg-teal-50 p-3">
                <p :class="elderMode ? 'text-base leading-7' : 'text-sm leading-6'" class="text-teal-900">
                  {{ scanExplanations[idx].elderFriendlyExplanation }}
                </p>
              </div>

              <!-- Warnings -->
              <div v-if="scanExplanations[idx].warnings.length" class="space-y-1">
                <div
                  v-for="(w, i) in scanExplanations[idx].warnings"
                  :key="i"
                  class="flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-1.5 text-xs text-amber-800"
                >
                  <iconify-icon icon="solar:danger-triangle-outline" width="12" height="12" class="mt-0.5 shrink-0" />
                  {{ w }}
                </div>
              </div>

              <!-- Clinical details (collapsible) -->
              <details>
                <summary class="cursor-pointer text-xs text-slate-400 hover:text-slate-600">查看详细药学信息</summary>
                <pre class="mt-1 whitespace-pre-wrap rounded-lg bg-slate-50 p-2 text-xs leading-5 text-slate-600">{{ scanExplanations[idx].clinicalParse }}</pre>
              </details>
            </div>

            <!-- Smart alarm suggestion -->
            <div v-if="suggestedSchedules.has(idx)" class="mt-3 rounded-[1rem] border border-teal-200 bg-gradient-to-br from-teal-50 to-white p-3">
              <div class="flex items-center gap-2 mb-2">
                <iconify-icon icon="solar:bell-bing-outline" width="16" height="16" class="text-teal-600" />
                <span class="text-sm font-medium text-teal-700">智能提醒建议</span>
              </div>
              <p class="text-xs text-teal-600 mb-2">{{ suggestedSchedules.get(idx)?.reason }}</p>
              <div class="flex flex-wrap gap-2">
                <span
                  v-for="time in suggestedSchedules.get(idx)?.times"
                  :key="time"
                  class="inline-flex items-center gap-1 rounded-full bg-teal-100 px-3 py-1 text-sm font-semibold text-teal-800"
                >
                  <iconify-icon icon="solar:clock-circle-outline" width="12" height="12" />
                  {{ time }}
                </span>
              </div>
              <Button
                variant="ghost"
                class="mt-2"
                size="sm"
                @click="createAlarmFromSuggestion(result, suggestedSchedules.get(idx)!)"
              >
                <iconify-icon icon="solar:add-circle-outline" width="14" height="14" />
                一键创建闹钟
              </Button>
            </div>

            <!-- Loading state -->
            <div v-else-if="explainingIds.has(idx)" class="mt-3 flex items-center gap-2 text-xs text-slate-400">
              <iconify-icon icon="solar:refresh-outline" width="14" height="14" class="animate-spin" />
              AI 正在分析「{{ result.name }}」的用药说明…
            </div>

            <!-- Manual explain button (fallback) -->
            <Button v-else variant="ghost" class="mt-2" size="sm" @click="explainScanned(result, idx)">
              <iconify-icon icon="solar:info-circle-outline" width="14" height="14" />
              生成用药建议
            </Button>
          </div>
        </div>
      </ClinicalSurfaceCard>
    </div>

    <!-- Tab: My Medications -->
    <div v-if="activeTab === 'medications'" class="space-y-4">
      <div class="flex items-center justify-between px-1">
        <span class="text-sm font-medium text-slate-700">我的药物闹钟</span>
        <Button @click="openCreatePage">
          <iconify-icon icon="solar:add-circle-outline" width="14" height="14" />
          新建闹钟
        </Button>
      </div>

      <ClinicalStateNotice
        v-if="!alarmList.length"
        tone="empty"
        title="暂无用药闹钟"
        description="先创建一个闹钟，再把不同药品放进同一提醒里。"
      />

      <div v-else class="grid gap-3 md:grid-cols-2">
        <article
          v-for="alarm in alarmList"
          :key="alarm.id"
          class="rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] px-4 py-4 shadow-[var(--elevation-soft)]"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2">
                <span :class="elderMode ? 'text-3xl' : 'text-2xl'" class="font-bold tracking-tight text-slate-950">
                  {{ alarm.time }}
                </span>
                <span
                  class="rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="alarm.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'"
                >
                  {{ alarm.enabled ? '已启用' : '已暂停' }}
                </span>
              </div>
              <p class="mt-1 text-xs text-slate-500">{{ alarm.medications.length }} 种药品</p>
            </div>
            <div class="flex gap-1">
              <button type="button" class="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-50 hover:text-slate-700" @click="editAlarm(alarm)">
                <iconify-icon icon="solar:pen-outline" width="16" height="16" />
              </button>
              <button type="button" class="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-50 hover:text-slate-700" @click="toggleAlarmStatus(alarm)">
                <iconify-icon :icon="alarm.enabled ? 'solar:pause-outline' : 'solar:play-outline'" width="16" height="16" />
              </button>
              <button type="button" class="rounded-lg p-1.5 text-slate-400 transition hover:bg-red-50 hover:text-red-600" @click="removeAlarm(alarm)">
                <iconify-icon icon="solar:trash-bin-minimalistic-outline" width="16" height="16" />
              </button>
            </div>
          </div>

          <div class="mt-3 space-y-2">
            <article
              v-for="drug in alarm.medications"
              :key="`${alarm.id}_${drug.id ?? drug.name}`"
              class="rounded-[1rem] bg-[color:var(--surface-secondary)] px-3 py-2.5"
            >
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-semibold text-slate-950">{{ drug.name }}</span>
                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{{ displayDosage(drug) }}</span>
                <span class="rounded-full bg-sky-50 px-2 py-0.5 text-xs text-sky-700">{{ drug.usage || '待确认' }}</span>
              </div>
              <p v-if="drug.alias" class="mt-1 text-xs text-slate-400">别名：{{ drug.alias }}</p>
              <p v-if="drug.notes" class="mt-1 text-xs text-slate-500">{{ drug.notes }}</p>
            </article>
          </div>
        </article>
      </div>
    </div>

    <!-- Tab: Today's Medication -->
    <div v-if="activeTab === 'today'" class="space-y-4">
      <!-- Progress card -->
      <div class="rounded-[1.5rem] border border-[color:var(--surface-border)] bg-gradient-to-br from-teal-50 to-white p-5 shadow-[var(--elevation-soft)]">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-teal-700">今日服药计划</p>
            <p :class="elderMode ? 'text-4xl' : 'text-2xl'" class="mt-1 font-bold text-teal-900">
              {{ todaySchedule.completedCount }}/{{ todaySchedule.totalCount }}
              <span class="text-sm font-normal text-teal-600">已完成</span>
            </p>
          </div>
          <div class="relative h-16 w-16">
            <svg class="h-16 w-16 -rotate-90" viewBox="0 0 64 64">
              <circle cx="32" cy="32" r="28" fill="none" stroke="#d1fae5" stroke-width="6" />
              <circle
                cx="32" cy="32" r="28" fill="none" stroke="#0d9488" stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="`${todayProgress * 1.76} 176`"
              />
            </svg>
            <span class="absolute inset-0 flex items-center justify-center text-xs font-bold text-teal-700">
              {{ Math.round(todayProgress) }}%
            </span>
          </div>
        </div>
      </div>

      <ClinicalStateNotice
        v-if="!todaySchedule.items.length"
        tone="empty"
        title="今日无用药计划"
        description="请先在「我的药物」中添加闹钟。"
      />

      <!-- Schedule items -->
      <div
        v-for="item in todaySchedule.items"
        :key="item.alarmId"
        class="rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] px-4 py-4 shadow-[var(--elevation-soft)]"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <span :class="elderMode ? 'text-3xl' : 'text-2xl'" class="font-bold text-slate-950">{{ item.time }}</span>
            <span
              class="rounded-full px-2.5 py-0.5 text-xs font-medium"
              :class="intakeStatusClasses(item.intakeStatus)"
            >
              {{ intakeStatusLabel(item.intakeStatus) }}
            </span>
          </div>
          <iconify-icon
            v-if="item.intakeStatus === 'taken'"
            icon="solar:check-circle-bold"
            width="24" height="24"
            class="text-emerald-500"
          />
          <iconify-icon
            v-else-if="item.intakeStatus === 'skipped'"
            icon="solar:close-circle-bold"
            width="24" height="24"
            class="text-red-400"
          />
        </div>

        <div class="mt-2 space-y-1">
          <p v-for="drug in item.medications" :key="drug.id" class="flex items-center gap-2 text-sm text-slate-700">
            <iconify-icon icon="solar:pills-3-outline" width="14" height="14" class="text-teal-600" />
            <span class="font-medium">{{ drug.name }}</span>
            <span class="text-slate-400">—</span>
            {{ displayDosage(drug) }} {{ drug.usage }}
          </p>
        </div>

        <!-- Intake buttons -->
        <div v-if="item.intakeStatus === 'pending'" class="mt-4 flex gap-2">
          <Button
            class="flex-1"
            :class="elderMode ? '!h-14 !text-lg' : ''"
            @click="confirmIntake(item.alarmId, 'taken')"
            :loading="confirmingId === item.alarmId"
          >
            <iconify-icon icon="solar:check-circle-bold" width="16" height="16" />
            已服用
          </Button>
          <Button
            variant="secondary"
            :class="elderMode ? '!h-14 !text-base' : ''"
            @click="confirmIntake(item.alarmId, 'half')"
            :loading="confirmingId === item.alarmId"
          >
            半片
          </Button>
          <Button
            variant="ghost"
            :class="elderMode ? '!h-14 !text-base' : ''"
            @click="confirmIntake(item.alarmId, 'skipped')"
            :loading="confirmingId === item.alarmId"
          >
            跳过
          </Button>
        </div>

        <!-- Already confirmed -->
        <div v-else class="mt-3 flex items-center gap-2 text-sm" :class="intakeTextClasses(item.intakeStatus)">
          <iconify-icon
            :icon="item.intakeStatus === 'taken' ? 'solar:check-circle-bold' : item.intakeStatus === 'half' ? 'solar:minus-circle-bold' : 'solar:close-circle-bold'"
            width="16" height="16"
          />
          {{ intakeStatusLabel(item.intakeStatus) }}
        </div>
      </div>
    </div>

    <p class="px-1 text-center text-xs leading-6 text-slate-500">
      用药提醒仅用于执行辅助，不替代医生处方与药学指导。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  confirmMedicationIntake,
  createMedicationAlarm,
  deleteMedicationAlarm,
  explainMedication,
  getMedicationAlarms,
  getTodayMedicationSchedule,
  recognizeMedicationBatch,
  toggleMedicationAlarm,
  type MedicationAlarm,
  type MedicationAlarmDrug,
  type MedicationAlarmPayload,
  type MedicationExplainResponse,
  type MedicationRecognitionResult,
  type TodayScheduleResponse,
} from '@/api/modules/medication'
import { useToast } from '@/composables/useToast'
import { emitMedicationAlarmChangedEvent } from '@/modules/medication/utils/medicationAlarm'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Button from '@/shared/components/ui/Button.vue'

const router = useRouter()
const { success, error } = useToast()

const tabs = [
  { value: 'scan', label: '扫描识别', icon: 'solar:qr-code-outline' },
  { value: 'medications', label: '我的药物', icon: 'solar:pills-3-outline' },
  { value: 'today', label: '今日服药', icon: 'solar:calendar-mark-outline' },
]

const activeTab = ref('today')
const elderMode = ref(localStorage.getItem('hm_elder_mode') === 'true')
watch(elderMode, (val) => { localStorage.setItem('hm_elder_mode', String(val)) })
const scanInput = ref<HTMLInputElement | null>(null)
const scanResults = ref<MedicationRecognitionResult[]>([])
const scanExplanations = ref<Record<number, MedicationExplainResponse>>({})
const explainingIds = ref(new Set<number>())
const confirmingId = ref<number | null>(null)
const alarmList = ref<MedicationAlarm[]>([])
const todaySchedule = ref<TodayScheduleResponse>({
  date: '',
  items: [],
  totalCount: 0,
  completedCount: 0,
})

const todayProgress = computed(() => {
  if (!todaySchedule.value.totalCount) return 0
  return (todaySchedule.value.completedCount / todaySchedule.value.totalCount) * 100
})

const sortAlarms = (alarms: MedicationAlarm[]) =>
  [...alarms].sort((left, right) => left.time.localeCompare(right.time) || left.id - right.id)

const displayDosage = (drug: Pick<MedicationAlarmDrug, 'dosageValue' | 'dosageUnit'>) => {
  if (!drug.dosageValue || !drug.dosageUnit) return '待确认'
  return `${drug.dosageValue} ${drug.dosageUnit}`
}

const intakeStatusLabel = (status: string) => {
  switch (status) {
    case 'taken': return '已服用'
    case 'skipped': return '已跳过'
    case 'half': return '吃了半片'
    default: return '待服用'
  }
}

const intakeStatusClasses = (status: string) => {
  switch (status) {
    case 'taken': return 'bg-emerald-50 text-emerald-700'
    case 'skipped': return 'bg-red-50 text-red-600'
    case 'half': return 'bg-amber-50 text-amber-700'
    default: return 'bg-slate-100 text-slate-600'
  }
}

const intakeTextClasses = (status: string) => {
  switch (status) {
    case 'taken': return 'text-emerald-600'
    case 'skipped': return 'text-red-500'
    case 'half': return 'text-amber-600'
    default: return 'text-slate-500'
  }
}

// Scan functions
const triggerScan = () => { scanInput.value?.click() }
const triggerFileSelect = () => {
  if (scanInput.value) { scanInput.value.removeAttribute('capture'); scanInput.value.click() }
}

const handleScanUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (!files.length) return
  try {
    const result = await recognizeMedicationBatch(files)
    scanResults.value = result.items ?? []
    scanExplanations.value = {}
    suggestedSchedules.value = new Map()
    if (scanResults.value.length) {
      success('识别完成', `共识别 ${scanResults.value.length} 种药品，正在生成用药建议…`)
      suggestedSchedules.value = buildSuggestedSchedules(scanResults.value)
      // Auto-explain each recognized drug
      for (let i = 0; i < scanResults.value.length; i++) {
        void explainScanned(scanResults.value[i], i)
      }
    } else {
      error('识别失败', '未能识别出药品信息，请重新拍照。')
    }
  } catch (err) {
    error('识别失败', err instanceof Error ? err.message : '请稍后重试')
  }
  input.value = ''
}

const explainScanned = async (result: MedicationRecognitionResult, index: number) => {
  explainingIds.value.add(index)
  try {
    // Pass both OCR notes and sourceText to AI for comprehensive analysis
    const notes = [result.notes, result.sourceText].filter(Boolean).join('\n') || undefined
    const explanation = await explainMedication(result.name, notes)
    scanExplanations.value = { ...scanExplanations.value, [index]: explanation }
  } catch (err) {
    // Silent fail — manual retry button available
  } finally {
    const next = new Set(explainingIds.value)
    next.delete(index)
    explainingIds.value = next
  }
}

const speakText = (text: string) => {
  if (!text || typeof window === 'undefined' || !('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = elderMode.value ? 0.8 : 1.0
  window.speechSynthesis.speak(utterance)
}

// === Smart alarm interval calculation ===

type SuggestedSchedule = {
  times: string[]      // ["08:00", "12:30", "17:00"]
  frequency: number    // times per day
  intervalHours: number
  reason: string
}

/** Parse frequency from usage text (e.g., "一天三次" → 3, "每日2次" → 2, "bid" → 2) */
function parseFrequency(usage: string, notes: string): number {
  const text = `${usage} ${notes}`.toLowerCase()

  // Chinese patterns
  const cnMatch = text.match(/[一每]?天(\d|[一二三四五六七八九])次/)
  if (cnMatch) {
    const numMap: Record<string, number> = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6 }
    const v = cnMatch[1]
    return numMap[v] ?? (parseInt(v, 10) || 1)
  }

  // English abbreviations
  if (/\bqd\b/.test(text)) return 1     // once daily
  if (/\bbid\b/.test(text)) return 2    // twice daily
  if (/\btid\b/.test(text)) return 3    // three times daily
  if (/\bqid\b/.test(text)) return 4    // four times daily
  if (/\bq\d+h\b/.test(text)) {
    const h = parseInt(text.match(/q(\d+)h/)?.[1] ?? '24', 10)
    return Math.max(1, Math.round(24 / h))
  }

  // "每日N次"
  const dailyMatch = text.match(/每日\s*(\d)\s*次/)
  if (dailyMatch) return parseInt(dailyMatch[1], 10)

  return 1 // default: once daily
}

/** Calculate optimal reminder times within waking hours (7:00-22:00) */
function calculateSmartTimes(frequency: number): SuggestedSchedule {
  const wakeHour = 7
  const sleepHour = 22
  const activeHours = sleepHour - wakeHour // 15 hours

  if (frequency <= 1) {
    return {
      times: ['08:00'],
      frequency: 1,
      intervalHours: 0,
      reason: '每日一次，建议早餐后固定时间服用',
    }
  }

  // Interval: at least 4h, spread evenly across active hours
  const idealInterval = activeHours / frequency
  const interval = Math.max(4, Math.min(6, Math.round(idealInterval)))

  const times: string[] = []
  let currentHour = wakeHour + 0.5 // start at 7:30

  for (let i = 0; i < frequency && currentHour < sleepHour - 1; i++) {
    const h = Math.floor(currentHour)
    const m = Math.round((currentHour - h) * 60)
    times.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`)
    currentHour += interval
  }

  return {
    times,
    frequency,
    intervalHours: interval,
    reason: `每日${frequency}次，间隔约${interval}小时，在活动时间内均匀分布`,
  }
}

/** Build suggested schedules for all recognized medications */
function buildSuggestedSchedules(results: MedicationRecognitionResult[]): Map<number, SuggestedSchedule> {
  const map = new Map<number, SuggestedSchedule>()
  results.forEach((result, idx) => {
    const freq = parseFrequency(result.usage || '', result.notes || '')
    map.set(idx, calculateSmartTimes(freq))
  })
  return map
}

const suggestedSchedules = ref<Map<number, SuggestedSchedule>>(new Map())

/** Create alarm from AI-suggested schedule */
const createAlarmFromSuggestion = async (drug: MedicationRecognitionResult, schedule: SuggestedSchedule) => {
  try {
    // Create alarms for each suggested time
    for (const time of schedule.times) {
      await createMedicationAlarm({
        time,
        enabled: true,
        medications: [{
          name: drug.name,
          alias: drug.alias || '',
          dosageValue: drug.dosageValue ?? 1,
          dosageUnit: drug.dosageUnit || '片',
          usage: drug.usage || '饭后',
          notes: drug.notes || '',
          photoUrl: drug.photoUrl || '',
          enableOcr: false,
          enableYolo: false,
          ocrEndpoint: '',
          yoloEndpoint: '',
          enabled: true,
        }],
      })
    }
    emitMedicationAlarmChangedEvent()
    success('闹钟已创建', `${drug.name} 每日${schedule.times.length}次提醒已设置`)
    // Switch to medications tab to show the new alarms
    activeTab.value = 'medications'
    void loadAlarms()
  } catch (err) {
    error('创建失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

// Medication list
const loadAlarms = async () => {
  try { alarmList.value = sortAlarms(await getMedicationAlarms()) }
  catch (e) { error('读取失败', e instanceof Error ? e.message : '用药闹钟加载失败。') }
}

const loadTodaySchedule = async () => {
  try { todaySchedule.value = await getTodayMedicationSchedule() } catch { /* best-effort */ }
}

const openCreatePage = () => { void router.push('/medication/alarm') }
const editAlarm = (alarm: MedicationAlarm) => { void router.push({ path: '/medication/alarm', query: { id: String(alarm.id) } }) }

const toggleAlarmStatus = async (alarm: MedicationAlarm) => {
  try {
    const response = await toggleMedicationAlarm(alarm.id)
    alarmList.value = alarmList.value.map((item) => item.id === alarm.id ? { ...item, enabled: response.enabled } : item)
    emitMedicationAlarmChangedEvent()
    success(response.enabled ? '闹钟已启用' : '闹钟已暂停')
  } catch (e) { error('状态更新失败', e instanceof Error ? e.message : '请稍后再试。') }
}

const removeAlarm = async (alarm: MedicationAlarm) => {
  const shouldDelete = typeof window === 'undefined' || typeof window.confirm !== 'function' ? true : window.confirm(`确认删除 ${alarm.time} 的用药闹钟吗？`)
  if (!shouldDelete) return
  try {
    await deleteMedicationAlarm(alarm.id)
    alarmList.value = alarmList.value.filter((item) => item.id !== alarm.id)
    emitMedicationAlarmChangedEvent()
    success('闹钟已删除')
  } catch (e) { error('删除失败', e instanceof Error ? e.message : '请稍后再试。') }
}

const confirmIntake = async (alarmId: number, status: 'taken' | 'skipped' | 'half') => {
  confirmingId.value = alarmId
  try {
    await confirmMedicationIntake(alarmId, status)
    todaySchedule.value = {
      ...todaySchedule.value,
      items: todaySchedule.value.items.map((item) => item.alarmId === alarmId ? { ...item, intakeStatus: status } : item),
      completedCount: todaySchedule.value.items.filter((i) => (i.alarmId === alarmId ? status : i.intakeStatus) !== 'pending').length,
    }
    success('确认成功', intakeStatusLabel(status))
  } catch (e) { error('确认失败', e instanceof Error ? e.message : '请稍后重试') }
  finally { confirmingId.value = null }
}

watch(activeTab, (tab) => {
  if (tab === 'medications') void loadAlarms()
  if (tab === 'today') void loadTodaySchedule()
})

onMounted(() => { void loadTodaySchedule() })
</script>

<style scoped>
.elder-mode {
  font-size: 18px;
}
.elder-mode :deep(.text-sm) { font-size: 1rem !important; }
.elder-mode :deep(.text-xs) { font-size: 0.875rem !important; }
</style>
