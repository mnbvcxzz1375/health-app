<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalFeatureNavBar title="康复计划提醒" back-to="/rehab" />
    <ClinicalPageHeader
      title="康复计划提醒"
      :description="summaryText"
      :meta="pushEnabled ? '已开启' : '未开启'"
      meta-label="系统通知"
    />

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
            <p class="mt-1 text-xs text-slate-500">按整套康复计划推送提醒</p>
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
import { getRehabPlanReminder, saveRehabPlanReminder } from '@/api/modules/rehab'
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

const days: { key: DayKey; label: string }[] = [
  { key: 'mon', label: '周一' },
  { key: 'tue', label: '周二' },
  { key: 'wed', label: '周三' },
  { key: 'thu', label: '周四' },
  { key: 'fri', label: '周五' },
  { key: 'sat', label: '周六' },
  { key: 'sun', label: '周日' },
]

const hourOptions = Array.from({ length: 24 }, (_, index) => {
  const value = String(index).padStart(2, '0')
  return { label: value, value }
})

const minuteOptions = Array.from({ length: 12 }, (_, index) => {
  const value = String(index * 5).padStart(2, '0')
  return { label: value, value }
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
    success('提醒已保存', '整套康复计划的提醒已经更新。')
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
})
</script>
