<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      title="监测趋势"
      :meta="latestUpdatedAt"
      meta-label="最近同步"
    />

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <ClinicalStatCard label="静息心率" :value="`${latest.hr} bpm`" :hint="heartHint" icon="solar:heart-pulse-outline" :tone="latest.hr >= 95 ? 'danger' : 'success'" />
      <ClinicalStatCard label="睡眠评分" :value="`${latest.sleep}`" :hint="`深睡 ${latest.deepSleep} 小时，夜醒 ${latest.awake} 次`" icon="solar:moon-stars-outline" tone="info" />
      <ClinicalStatCard label="压力指数" :value="`${latest.stress}`" :hint="stressHint" icon="solar:shield-warning-outline" :tone="latest.stress >= 70 ? 'warning' : 'success'" />
    </section>

    <ClinicalSurfaceCard title="趋势解读">
      <div class="space-y-3">
        <div class="grid grid-cols-3 gap-2 rounded-[1.2rem] bg-[color:var(--surface-secondary)] p-1">
          <button
            v-for="t in tabs"
            :key="t.key"
            type="button"
            class="rounded-[1rem] px-2 py-2 text-sm transition"
            :class="activeTab === t.key ? 'bg-white text-slate-950 shadow-[var(--elevation-soft)]' : 'text-slate-500 hover:text-slate-900'"
            @click="activeTab = t.key"
          >
            {{ t.label }}
          </button>
        </div>

        <div class="grid grid-cols-3 gap-2 rounded-[1.2rem] bg-[color:var(--surface-secondary)] p-1">
          <button
            v-for="item in ranges"
            :key="item.key"
            type="button"
            class="rounded-[1rem] px-2 py-2 text-xs transition"
            :class="activeRange === item.key ? 'bg-white text-slate-950 shadow-[var(--elevation-soft)]' : 'text-slate-500 hover:text-slate-900'"
            @click="activeRange = item.key"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <ClinicalStateNotice
        v-if="trendLoading"
        class="mt-4"
        tone="loading"
        title="正在生成趋势解读"
        description="请稍候。"
      />

      <ClinicalStateNotice
        v-else-if="trendError"
        class="mt-4"
        tone="error"
        title="趋势数据暂不可用"
        :description="trendError"
      />

      <div v-else class="mt-4 space-y-4">
        <div class="h-60 overflow-hidden rounded-[1.4rem] border border-[color:var(--surface-border)] bg-white p-3">
          <EChartCanvas :option="chartOption" />
        </div>

        <div class="grid gap-3 lg:grid-cols-2">
          <div class="rounded-[1.3rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <p class="text-xs text-slate-500">当前趋势</p>
            <p class="mt-2 text-sm leading-6 text-slate-700">{{ insight }}</p>
          </div>
          <div class="rounded-[1.3rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <p class="text-xs text-slate-500">建议动作</p>
            <p class="mt-2 text-sm leading-6 text-slate-700">{{ suggestion }}</p>
          </div>
        </div>

        <div class="flex flex-wrap gap-2">
          <Button variant="secondary" @click="goUpload">
            <iconify-icon icon="solar:upload-outline" width="16" height="16" />
            上传资料
          </Button>
          <Button @click="goHome">
            <iconify-icon icon="solar:home-outline" width="16" height="16" />
            返回总览
          </Button>
        </div>
      </div>
    </ClinicalSurfaceCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { MonitorLatest, MonitorMetric, MonitorRange } from '@/api/modules/monitor'
import { getMonitorLatest, getMonitorTrend } from '@/api/modules/monitor'
import EChartCanvas from '@/shared/components/EChartCanvas.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Button from '@/shared/components/ui/Button.vue'
import type { EChartsCoreOption } from 'echarts'

const router = useRouter()

const tabs = [
  { key: 'hr', label: '心率' },
  { key: 'sleep', label: '睡眠' },
  { key: 'stress', label: '压力' },
] as const

const ranges = [
  { key: 'hour', label: '1 小时' },
  { key: 'day', label: '7 天' },
  { key: 'month', label: '6 个月' },
] as const

const latest = ref<MonitorLatest>({
  hr: 0,
  sleep: 0,
  deepSleep: 0,
  awake: 0,
  stress: 0,
  updatedAt: '',
})

const activeTab = ref<MonitorMetric>('hr')
const activeRange = ref<MonitorRange>('day')
const trendLabels = ref<string[]>([])
const trendValues = ref<number[]>([])
const insight = ref('暂无数据')
const suggestion = ref('暂无数据')
const trendLoading = ref(true)
const trendError = ref('')

const latestUpdatedAt = computed(() => {
  if (!latest.value.updatedAt) return '暂无数据'
  return new Date(latest.value.updatedAt).toLocaleString('zh-CN', { hour12: false })
})

const heartHint = computed(() => {
  if (latest.value.hr >= 95) return '训练前建议先观察恢复状态'
  if (latest.value.hr <= 60) return '当前偏低，注意热身与体感'
  return '处于相对稳定区间'
})

const stressHint = computed(() => {
  if (latest.value.stress >= 75) return '建议先做放松呼吸与恢复训练'
  if (latest.value.stress >= 60) return '建议减少今日额外负荷'
  return '可维持原定训练节奏'
})

const activeTabTitle = computed(() => tabs.find((t) => t.key === activeTab.value)?.label ?? '')

const chartOption = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 12, right: 12, top: 16, bottom: 18, containLabel: true },
  xAxis: {
    type: 'category',
    data: trendLabels.value,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#d8e5e1' } },
    axisLabel: { color: '#475569' },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#e6efec' } },
    axisLabel: { color: '#475569' },
  },
  series: [
    {
      name: activeTabTitle.value,
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: trendValues.value,
      lineStyle: { width: 3, color: '#115e59' },
      itemStyle: { color: '#115e59' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(17,94,89,0.28)' },
            { offset: 1, color: 'rgba(17,94,89,0)' },
          ],
        },
      },
    },
  ],
}))

const loadLatest = async () => {
  latest.value = await getMonitorLatest()
}

const loadTrend = async () => {
  trendLoading.value = true
  trendError.value = ''
  try {
    const data = await getMonitorTrend(activeTab.value, activeRange.value)
    trendLabels.value = data.labels
    trendValues.value = data.values
    insight.value = data.insight
    suggestion.value = data.suggestion
  } catch (error) {
    trendLabels.value = []
    trendValues.value = []
    trendError.value = error instanceof Error ? error.message : '请稍后重试或补充监测记录。'
    insight.value = '暂无趋势数据'
    suggestion.value = '请稍后重试或补充监测记录。'
  } finally {
    trendLoading.value = false
  }
}

watch(
  [activeTab, activeRange],
  () => {
    void loadTrend()
  },
  { immediate: true },
)

onMounted(() => {
  void loadLatest()
})

const goUpload = () => router.push('/upload')
const goHome = () => router.push('/home')
</script>
