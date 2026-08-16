<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalFeatureNavBar title="训练提醒" back-to="/rehab" />
    <ClinicalPageHeader
      title="每日训练提醒"
      :description="summaryText"
      :meta="pushEnabled ? '已开启' : '未开启'"
      meta-label="系统通知"
    />

    <!-- 今日训练提醒卡片 -->
    <ClinicalSurfaceCard
      eyebrow="今日提醒"
      :title="todayTitle"
      :description="todayDescription"
    >
      <div v-if="todayPlan" class="space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <span
            class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold"
            :class="todayPlan.isRestDay
              ? 'bg-slate-100 text-slate-700'
              : 'bg-teal-50 text-teal-700'"
          >
            <iconify-icon
              :icon="todayPlan.isRestDay ? 'solar:bed-outline' : 'solar:dumbbell-outline'"
              width="14"
              height="14"
            />
            {{ todayPlan.isRestDay ? '休息日' : '训练日' }}
          </span>
          <span v-if="!todayPlan.isRestDay" class="text-xs text-slate-500">
            {{ todayPlan.duration }} 分钟 · 约 {{ todayPlan.estimatedCalories }} 千卡
          </span>
          <span v-else class="text-xs text-slate-500">主动恢复，让肌肉生长</span>
        </div>

        <!-- 今日动作清单 -->
        <div v-if="!todayPlan.isRestDay && todayPlan.exercises.length" class="space-y-2">
          <div
            v-for="ex in todayPlan.exercises"
            :key="ex.id"
            class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <iconify-icon icon="solar:clipboard-list-outline" width="18" height="18" class="text-slate-500" />
                <span class="text-sm font-semibold text-slate-950">{{ ex.name }}</span>
                <span class="text-xs text-slate-500">{{ ex.muscleGroup }}</span>
              </div>
              <span class="text-xs text-slate-600">{{ ex.caloriesBurnPerMin }} kcal/分</span>
            </div>
          </div>
        </div>

        <!-- 休息日提示 -->
        <div
          v-else
          class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm leading-6 text-slate-700"
        >
          今日为休息日，建议进行轻度活动（散步、拉伸），并保证充足睡眠以促进恢复。
        </div>

        <!-- 提醒文案预览 -->
        <div
          class="rounded-[1.2rem] border border-teal-200 bg-teal-50/60 px-4 py-3 text-sm leading-6 text-teal-900"
        >
          <p class="font-semibold">提醒文案预览</p>
          <p class="mt-1">{{ reminderPreviewText }}</p>
        </div>
      </div>

      <div v-else class="text-sm text-slate-500">暂无训练计划，请先到「智能健康计划」生成。</div>

      <template #headerRight>
        <Button variant="secondary" @click="goSmartPlan">
          <iconify-icon icon="solar:magic-stick-outline" width="14" height="14" />
          生成计划
        </Button>
      </template>
    </ClinicalSurfaceCard>

    <!-- 本周训练日程 -->
    <ClinicalSurfaceCard
      v-if="weeklyPlan.length"
      eyebrow="本周日程"
      title="本周训练安排"
    >
      <div class="grid grid-cols-7 gap-1.5">
        <button
          v-for="day in weeklyPlan"
          :key="day.dayIndex"
          type="button"
          class="flex flex-col items-center rounded-[0.8rem] border px-1 py-2 text-center transition"
          :class="day.dayIndex === todayDayIndex
            ? 'border-teal-400 bg-teal-50 text-teal-900 ring-1 ring-teal-300'
            : day.isRestDay
              ? 'border-[color:var(--surface-border)] bg-slate-50 text-slate-500'
              : 'border-[color:var(--surface-border)] bg-white text-slate-800 hover:text-slate-950'"
          @click="previewDay = previewDay === day.dayIndex ? null : day.dayIndex"
        >
          <span class="text-[11px] font-medium">{{ day.day }}</span>
          <iconify-icon
            :icon="day.isRestDay ? 'solar:bed-outline' : 'solar:dumbbell-outline'"
            width="16"
            height="16"
            class="mt-1"
            :class="day.dayIndex === todayDayIndex ? 'text-teal-600' : ''"
          />
          <span class="mt-1 text-[10px] leading-tight">{{ day.isRestDay ? '休息' : `${day.duration}分` }}</span>
        </button>
      </div>

      <!-- 选中某天的详情 -->
      <div v-if="previewDay !== null" class="mt-4 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
        <div class="flex items-center justify-between">
          <p class="text-sm font-semibold text-slate-950">{{ previewedDay?.day }} · {{ previewedDay?.focus }}</p>
          <span class="text-xs text-slate-500">
            {{ previewedDay?.isRestDay ? '休息日' : `${previewedDay?.duration} 分钟 / ${previewedDay?.estimatedCalories} 千卡` }}
          </span>
        </div>
        <div v-if="previewedDay && !previewedDay.isRestDay && previewedDay.exercises.length" class="mt-2 flex flex-wrap gap-1.5">
          <span
            v-for="ex in previewedDay.exercises"
            :key="ex.id"
            class="inline-flex items-center gap-1 rounded-full bg-white px-2.5 py-1 text-xs text-slate-700 border border-[color:var(--surface-border)]"
          >
            <iconify-icon icon="solar:bullet-outline" width="12" height="12" />
            {{ ex.name }}
          </span>
        </div>
        <p v-else class="mt-2 text-xs text-slate-500">今日安排主动恢复与拉伸放松。</p>
      </div>

      <template #headerRight>
        <Button variant="secondary" @click="syncTrainingDays">
          <iconify-icon icon="solar:refresh-circle-outline" width="14" height="14" />
          同步训练日
        </Button>
      </template>
    </ClinicalSurfaceCard>

    <div class="grid gap-4 xl:grid-cols-[0.95fr_1.05fr]">
      <ClinicalSurfaceCard title="提醒时间">
        <div class="block">
          <span class="text-xs text-slate-500">时间</span>
          <div class="mt-1 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2">
            <AppSelect
              v-model="hourValue"
              ariaLabel="康复提醒小时"
              placeholder="小时"
              :options="hourOptions"
            />
            <span class="text-sm font-semibold text-slate-400">:</span>
            <AppSelect
              v-model="minuteValue"
              ariaLabel="康复提醒分钟"
              placeholder="分钟"
              :options="minuteOptions"
            />
          </div>
        </div>

        <div class="mt-4 grid grid-cols-3 gap-2">
          <Button variant="secondary" @click="applyPreset('everyday')">每天</Button>
          <Button variant="secondary" @click="applyPreset('workday')">工作日</Button>
          <Button variant="secondary" @click="applyPreset('weekend')">周末</Button>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard title="提醒频率">
        <div class="grid grid-cols-4 gap-2">
          <button
            v-for="day in days"
            :key="day.key"
            type="button"
            class="rounded-[1rem] border px-2 py-2 text-center text-xs transition"
            :class="
              selectedDays.includes(day.key)
                ? 'border-teal-300 bg-teal-50 text-teal-900'
                : 'border-[color:var(--surface-border)] bg-white text-slate-600 hover:text-slate-900'
            "
            @click="toggleDay(day.key)"
          >
            {{ day.label }}
          </button>
        </div>

        <label
          class="mt-4 flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
        >
          <div>
            <p class="text-sm font-semibold text-slate-950">系统通知</p>
            <p class="mt-1 text-xs text-slate-500">按整套训练计划推送提醒</p>
          </div>
          <button
            type="button"
            class="inline-flex h-8 w-14 items-center rounded-full border border-[color:var(--surface-border)] px-1 transition"
            :class="pushEnabled ? 'justify-end bg-teal-600 text-white' : 'justify-start bg-white text-slate-700'"
            @click="pushEnabled = !pushEnabled"
          >
            <span class="h-6 w-6 rounded-full bg-current" />
          </button>
        </label>

        <p class="mt-4 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm leading-6 text-slate-700">
          {{ summaryText }}
        </p>
      </ClinicalSurfaceCard>
    </div>

    <div class="grid grid-cols-2 gap-2.5">
      <Button variant="secondary" :disabled="saving" @click="goBack">返回计划</Button>
      <Button :loading="saving" @click="saveReminder">保存提醒</Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getRehabPlanReminder,
  saveRehabPlanReminder,
  getSavedWeeklyPlan,
  getTodayPlan,
  type WeeklyDayPlan,
} from '@/api/modules/rehab'
import { useToast } from '@/composables/useToast'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import AppSelect from '@/shared/components/ui/AppSelect.vue'
import Button from '@/shared/components/ui/Button.vue'

type DayKey = 'mon' | 'tue' | 'wed' | 'thu' | 'fri' | 'sat' | 'sun'

const router = useRouter()
const { success, warning } = useToast()

const time = ref('08:00')
const hourValue = ref('08')
const minuteValue = ref('00')
const selectedDays = ref<DayKey[]>(['mon', 'wed', 'fri'])
const pushEnabled = ref(true)
const saving = ref(false)

// 周训练计划数据
const weeklyPlan = ref<WeeklyDayPlan[]>([])
const previewDay = ref<number | null>(null)

const days: { key: DayKey; label: string }[] = [
  { key: 'mon', label: '周一' },
  { key: 'tue', label: '周二' },
  { key: 'wed', label: '周三' },
  { key: 'thu', label: '周四' },
  { key: 'fri', label: '周五' },
  { key: 'sat', label: '周六' },
  { key: 'sun', label: '周日' },
]

const dayKeyByIndex: DayKey[] = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

const hourOptions = Array.from({ length: 24 }, (_, index) => {
  const value = String(index).padStart(2, '0')
  return { label: value, value }
})

const minuteOptions = Array.from({ length: 12 }, (_, index) => {
  const value = String(index * 5).padStart(2, '0')
  return { label: value, value }
})

// 今日训练安排
const todayPlan = computed<WeeklyDayPlan | null>(() => getTodayPlan(weeklyPlan.value))

// 今日 dayIndex（0=周一 ... 6=周日）
const todayDayIndex = computed(() => {
  const jsDay = new Date().getDay()
  return jsDay === 0 ? 6 : jsDay - 1
})

const previewedDay = computed<WeeklyDayPlan | null>(() =>
  previewDay.value !== null
    ? weeklyPlan.value.find((d) => d.dayIndex === previewDay.value) ?? null
    : null,
)

const todayTitle = computed(() => {
  if (!todayPlan.value) return '今日训练'
  return todayPlan.value.isRestDay ? '今天休息' : `今天练${todayPlan.value.focus}`
})

const todayDescription = computed(() => {
  if (!todayPlan.value) return '尚未生成训练计划'
  if (todayPlan.value.isRestDay) return '主动恢复日，建议轻度活动与拉伸放松'
  return `${todayPlan.value.duration} 分钟 · 预计消耗 ${todayPlan.value.estimatedCalories} 千卡`
})

const reminderPreviewText = computed(() => {
  if (!todayPlan.value) return '请先生成训练计划'
  if (todayPlan.value.isRestDay) {
    return `【训练提醒】今天休息，可进行轻度散步或拉伸，注意补充水分与睡眠。`
  }
  const names = todayPlan.value.exercises.slice(0, 3).map((e) => e.name).join('、')
  const more = todayPlan.value.exercises.length > 3 ? ` 等 ${todayPlan.value.exercises.length} 个动作` : ''
  return `【训练提醒】今天练${todayPlan.value.focus}（${todayPlan.value.duration}分钟）：${names}${more}，准备好了就开始吧！`
})

const summaryText = computed(() => {
  const labels = days.filter((day) => selectedDays.value.includes(day.key)).map((day) => day.label)
  return `${time.value} / ${labels.length ? labels.join('、') : '未选择日期'} / ${pushEnabled.value ? '系统通知开启' : '系统通知关闭'}`
})

watch([hourValue, minuteValue], ([hour, minute]) => {
  time.value = `${hour}:${minute}`
})

const toggleDay = (key: DayKey) => {
  const next = [...selectedDays.value]
  const idx = next.indexOf(key)
  if (idx >= 0) next.splice(idx, 1)
  else next.push(key)
  selectedDays.value = next
}

const applyPreset = (preset: 'everyday' | 'workday' | 'weekend') => {
  if (preset === 'everyday') selectedDays.value = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']
  if (preset === 'workday') selectedDays.value = ['mon', 'tue', 'wed', 'thu', 'fri']
  if (preset === 'weekend') selectedDays.value = ['sat', 'sun']
}

/** 同步训练日：自动勾选周计划中的训练日（排除休息日） */
const syncTrainingDays = () => {
  if (!weeklyPlan.value.length) {
    warning('暂无计划', '请先到「智能健康计划」生成训练计划。')
    return
  }
  const trainingDays = weeklyPlan.value
    .filter((d) => !d.isRestDay)
    .map((d) => dayKeyByIndex[d.dayIndex])
    .filter((k): k is DayKey => Boolean(k))
  selectedDays.value = trainingDays
  success('已同步训练日', `已勾选 ${trainingDays.length} 个训练日。`)
}

const goSmartPlan = () => {
  router.push('/rehab/smart-plan')
}

const loadWeeklyPlan = async () => {
  try {
    const res = await getSavedWeeklyPlan()
    weeklyPlan.value = res.weeklyPlan
  } catch (err) {
    console.error('加载周训练计划失败', err)
  }
}

const loadReminder = async () => {
  try {
    const data = await getRehabPlanReminder()
    time.value = data.time
    const [hour = '08', minute = '00'] = String(data.time ?? '08:00').split(':')
    hourValue.value = hour.padStart(2, '0')
    minuteValue.value = minute.padStart(2, '0')
    selectedDays.value = (Array.isArray(data.days) ? data.days : []).filter((day): day is DayKey =>
      ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'].includes(day),
    )
    pushEnabled.value = data.pushEnabled
  } catch (err) {
    warning('加载失败', err instanceof Error ? err.message : '请稍后重试。')
  }
}

const saveReminder = async () => {
  if (!selectedDays.value.length) {
    warning('请选择提醒日期', '至少选择一天用于提醒。')
    return
  }

  saving.value = true
  try {
    await saveRehabPlanReminder({
      time: time.value,
      days: selectedDays.value,
      pushEnabled: pushEnabled.value,
    })
    success('提醒已保存', '整套训练计划的提醒已经更新。')
  } catch (err) {
    warning('保存失败', err instanceof Error ? err.message : '请稍后重试。')
  } finally {
    saving.value = false
  }
}

const goBack = () => {
  router.push('/rehab')
}

onMounted(() => {
  void loadReminder()
  void loadWeeklyPlan()
})
</script>
