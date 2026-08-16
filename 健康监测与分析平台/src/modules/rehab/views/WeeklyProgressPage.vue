<template>
  <div class="weekly-progress pb-6">
    <!-- Header -->
    <div class="mx-auto max-w-[420px] px-4 pt-6">
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-full transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--foreground);"
          aria-label="返回"
          @click="router.back()"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>
        <div class="min-w-0">
          <h1 class="text-[22px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">每周记录</h1>
          <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">记录身体数据变化，AI 自动分析趋势</p>
        </div>
      </div>
    </div>

    <!-- Trend Overview Card -->
    <div class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <section
        v-if="data?.trend"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-start gap-2">
          <div
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full"
            :style="trendDirectionStyle"
          >
            <iconify-icon :icon="trendDirectionIcon" width="16" height="16" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[11px] uppercase tracking-[0.12em]" style="color: var(--muted-foreground);">AI 趋势分析</p>
            <p class="mt-1 text-[14px] leading-6" style="color: var(--foreground);">{{ data.trend.insight }}</p>
          </div>
        </div>

        <div class="mt-4 grid grid-cols-3 gap-2">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">体重变化</p>
            <p class="mt-1 text-[16px] font-semibold tabular-nums" :style="deltaStyle(data.trend.weightDelta)">
              {{ formatDelta(data.trend.weightDelta) }} kg
            </p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">体脂变化</p>
            <p class="mt-1 text-[16px] font-semibold tabular-nums" :style="deltaStyle(data.trend.bodyFatDelta)">
              {{ formatDelta(data.trend.bodyFatDelta) }} %
            </p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">腰围变化</p>
            <p class="mt-1 text-[16px] font-semibold tabular-nums" :style="deltaStyle(data.trend.waistDelta)">
              {{ formatDelta(data.trend.waistDelta) }} cm
            </p>
          </div>
        </div>
      </section>

      <!-- Trend Chart -->
      <section
        v-if="data?.chartData"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">趋势图表</h2>
          <div class="flex items-center gap-3 text-[12px]" style="color: var(--muted-foreground);">
            <span class="flex items-center gap-1">
              <span class="inline-block h-2 w-2 rounded-full" style="background: var(--brand-500);"></span>
              体重
            </span>
            <span class="flex items-center gap-1">
              <span class="inline-block h-2 w-2 rounded-full" style="background: var(--chart-3);"></span>
              体脂率
            </span>
          </div>
        </div>

        <div class="mt-3 h-48 overflow-hidden rounded-[12px]" style="background: var(--secondary);">
          <EChartCanvas :option="chartOption" />
        </div>
      </section>

      <!-- Loading state -->
      <section
        v-if="loading && !data"
        class="flex h-32 items-center justify-center rounded-[19.2px] border"
        style="background: var(--card); border-color: var(--border);"
      >
        <span class="text-[13px]" style="color: var(--muted-foreground);">加载中…</span>
      </section>

      <!-- History List -->
      <section
        v-if="data?.entries?.length"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">历史记录</h2>
          <span class="text-[13px]" style="color: var(--muted-foreground);">{{ data.entries.length }} 条</span>
        </div>

        <div class="mt-3 space-y-3">
          <article
            v-for="entry in data.entries"
            :key="`${entry.date}-${entry.week}`"
            class="rounded-[12px] p-4"
            style="background: var(--secondary);"
          >
            <div class="flex items-center justify-between">
              <p class="text-[14px] font-semibold" style="color: var(--foreground);">{{ entry.date }}</p>
              <span
                class="rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                style="background: var(--brand-50); color: var(--brand-500);"
              >第 {{ entry.week }} 周</span>
            </div>

            <div class="mt-3 grid grid-cols-2 gap-x-4 gap-y-2">
              <div class="flex items-center justify-between">
                <span class="text-[12px]" style="color: var(--muted-foreground);">体重</span>
                <span class="text-[13px] font-medium tabular-nums" style="color: var(--foreground);">{{ entry.weight }} kg</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-[12px]" style="color: var(--muted-foreground);">腰围</span>
                <span class="text-[13px] font-medium tabular-nums" style="color: var(--foreground);">{{ entry.waistCircumference ?? '--' }} cm</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-[12px]" style="color: var(--muted-foreground);">臀围</span>
                <span class="text-[13px] font-medium tabular-nums" style="color: var(--foreground);">{{ entry.hipCircumference ?? '--' }} cm</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-[12px]" style="color: var(--muted-foreground);">体脂率</span>
                <span class="text-[13px] font-medium tabular-nums" style="color: var(--foreground);">{{ entry.bodyFatPercent ?? '--' }} %</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-[12px]" style="color: var(--muted-foreground);">肌肉量</span>
                <span class="text-[13px] font-medium tabular-nums" style="color: var(--foreground);">{{ entry.muscleMass ?? '--' }} kg</span>
              </div>
            </div>

            <p
              v-if="entry.note"
              class="mt-3 rounded-[8px] px-3 py-2 text-[12px] leading-5"
              style="background: var(--background-200); color: var(--muted-foreground);"
            >{{ entry.note }}</p>
          </article>
        </div>
      </section>

      <!-- New Entry Form Card -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <button
          type="button"
          class="flex w-full items-center justify-between transition active:scale-[0.98]"
          @click="showForm = !showForm"
        >
          <div class="flex items-center gap-2">
            <div
              class="flex h-8 w-8 items-center justify-center rounded-full"
              style="background: var(--brand-50); color: var(--brand-500);"
            >
              <iconify-icon icon="solar:add-circle-outline" width="18" height="18" />
            </div>
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">新增记录</h2>
          </div>
          <iconify-icon
            :icon="showForm ? 'solar:alt-arrow-up-outline' : 'solar:alt-arrow-down-outline'"
            width="18" height="18"
            style="color: var(--muted-foreground);"
            :class="{ 'transition-transform duration-200': true }"
          />
        </button>

        <div v-if="showForm" class="mt-4 space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <label class="block">
              <span class="text-[12px]" style="color: var(--muted-foreground);">体重 (kg)</span>
              <input
                v-model.number="newEntry.weight"
                type="number"
                step="0.1"
                class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] tabular-nums outline-none"
                style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[12px]" style="color: var(--muted-foreground);">腰围 (cm)</span>
              <input
                v-model.number="newEntry.waistCircumference"
                type="number"
                step="0.1"
                class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] tabular-nums outline-none"
                style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[12px]" style="color: var(--muted-foreground);">臀围 (cm)</span>
              <input
                v-model.number="newEntry.hipCircumference"
                type="number"
                step="0.1"
                class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] tabular-nums outline-none"
                style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[12px]" style="color: var(--muted-foreground);">体脂率 (%)</span>
              <input
                v-model.number="newEntry.bodyFatPercent"
                type="number"
                step="0.1"
                class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] tabular-nums outline-none"
                style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[12px]" style="color: var(--muted-foreground);">肌肉量 (kg)</span>
              <input
                v-model.number="newEntry.muscleMass"
                type="number"
                step="0.1"
                class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] tabular-nums outline-none"
                style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[12px]" style="color: var(--muted-foreground);">备注</span>
              <input
                v-model="newEntry.note"
                type="text"
                class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] outline-none"
                style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
                placeholder="可选"
              />
            </label>
          </div>

          <button
            type="button"
            class="flex h-[52px] w-full items-center justify-center gap-2 rounded-full text-[16px] font-semibold transition active:scale-[0.98]"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            :disabled="saving"
            @click="handleSave"
          >
            <iconify-icon icon="solar:diskette-outline" width="20" height="20" />
            {{ saving ? '保存中…' : '保存记录' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { EChartsCoreOption } from 'echarts'
import { useRouter } from 'vue-router'
import {
  getWeeklyProgress,
  saveWeeklyProgress,
  type WeeklyProgressResponse,
  type WeeklyProgressEntry,
} from '@/api/modules/rehab'
import { useToast } from '@/composables/useToast'
import EChartCanvas from '@/shared/components/EChartCanvas.vue'

const router = useRouter()
const { success, error } = useToast()

const data = ref<WeeklyProgressResponse | null>(null)
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)

const newEntry = ref<WeeklyProgressEntry>({
  date: new Date().toISOString().slice(0, 10),
  week: 1,
  weight: 70,
  waistCircumference: 80,
  hipCircumference: 95,
  bodyFatPercent: 20,
  muscleMass: 52,
  note: '',
})

const trendDirectionIcon = computed(() => {
  const direction = data.value?.trend.direction
  if (direction === 'improving') return 'solar:alt-arrow-up-outline'
  if (direction === 'regressing') return 'solar:alt-arrow-down-outline'
  return 'solar:minus-circle-outline'
})

const trendDirectionStyle = computed(() => {
  const direction = data.value?.trend.direction
  if (direction === 'improving') {
    return { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
  }
  if (direction === 'regressing') {
    return { background: 'var(--state-error-surface)', color: 'var(--state-error)' }
  }
  return { background: 'var(--secondary)', color: 'var(--muted-foreground)' }
})

const deltaStyle = (delta: number) => {
  if (delta > 0) return { color: 'var(--state-error)' }
  if (delta < 0) return { color: 'var(--state-success)' }
  return { color: 'var(--muted-foreground)' }
}

const formatDelta = (delta: number) => {
  if (delta > 0) return `+${delta.toFixed(1)}`
  return delta.toFixed(1)
}

const chartOption = computed<EChartsCoreOption>(() => {
  const chartData = data.value?.chartData
  return {
    tooltip: { trigger: 'axis' },
    legend: { show: false },
    grid: { left: 16, right: 16, top: 24, bottom: 20, containLabel: true },
    xAxis: {
      type: 'category',
      data: chartData?.labels ?? [],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'var(--background-300)' } },
      axisLabel: { color: 'var(--muted-foreground)', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'var(--background-200)' } },
      axisLabel: { color: 'var(--muted-foreground)', fontSize: 11 },
    },
    series: [
      {
        name: '体重',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: chartData?.weights ?? [],
        itemStyle: { color: 'var(--brand-500)' },
        lineStyle: { color: 'var(--brand-500)', width: 2 },
      },
      {
        name: '体脂率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: chartData?.bodyFats ?? [],
        itemStyle: { color: 'var(--chart-3)' },
        lineStyle: { color: 'var(--chart-3)', width: 2 },
      },
    ],
  }
})

const loadData = async () => {
  loading.value = true
  try {
    data.value = await getWeeklyProgress()
  } catch (err) {
    error('加载失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (saving.value) return

  const payload: WeeklyProgressEntry = {
    date: new Date().toISOString().slice(0, 10),
    week: (data.value?.entries?.length ?? 0) + 1,
    weight: Number(newEntry.value.weight) || 0,
    waistCircumference: newEntry.value.waistCircumference != null
      ? Number(newEntry.value.waistCircumference)
      : undefined,
    hipCircumference: newEntry.value.hipCircumference != null
      ? Number(newEntry.value.hipCircumference)
      : undefined,
    bodyFatPercent: newEntry.value.bodyFatPercent != null
      ? Number(newEntry.value.bodyFatPercent)
      : undefined,
    muscleMass: newEntry.value.muscleMass != null
      ? Number(newEntry.value.muscleMass)
      : undefined,
    note: newEntry.value.note?.trim() || undefined,
  }

  saving.value = true
  try {
    await saveWeeklyProgress(payload)
    success('保存成功', '已记录本周身体数据。')
    showForm.value = false
    await loadData()
  } catch (err) {
    error('保存失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadData()
})
</script>
