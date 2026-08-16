<template>
  <div class="apple-monitor pb-6">
    <!-- Page Header -->
    <div class="mx-auto max-w-[420px] px-4 pt-4">
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition active:scale-95"
          style="background: var(--secondary); color: var(--foreground);"
          aria-label="返回"
          @click="router.push('/home')"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">健康监测</h1>
      </div>
      <p class="mt-0.5 pl-11 text-[14px]" style="color: var(--muted-foreground);">{{ today }}</p>

      <!-- Metric tabs (replaces time range in design - this page is metric-specific) -->
      <div class="mt-4 flex items-center gap-2">
        <div class="flex flex-1 gap-2 overflow-x-auto">
          <button
            v-for="m in metricTabs"
            :key="m.key"
            type="button"
            class="h-8 whitespace-nowrap rounded-full px-4 text-[13px] font-medium transition active:scale-[0.98]"
            :style="currentMetric === m.key
              ? { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }
              : { background: 'var(--secondary)', color: 'var(--secondary-foreground)' }"
            @click="switchMetric(m.key)"
          >
            {{ m.label }}
          </button>
        </div>
        <button
          type="button"
          class="flex h-8 shrink-0 items-center gap-1 rounded-full px-3 text-[12px] font-medium transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--foreground);"
          @click="openSyncSheet"
        >
          <iconify-icon icon="solar:refresh-circle-outline" width="14" height="14" />
          同步设备
        </button>
      </div>
    </div>

    <!-- Latest stat cards -->
    <div class="mx-auto mt-3 flex max-w-[420px] flex-col gap-3 px-4">
      <!-- Current metric highlighted card -->
      <div
        class="rounded-[19.2px] p-[18px]"
        style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <iconify-icon :icon="metricIcon" width="18" height="18" :style="{ color: metricColor }" />
            <span class="text-[17px] font-semibold" style="color: var(--foreground);">{{ metricTitle }}</span>
          </div>
          <span class="whitespace-nowrap text-[15px] font-medium" style="color: var(--foreground);">{{ metricDisplayValue }}</span>
        </div>
        <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">{{ metricHint }}</p>

        <!-- Mini chart for current metric -->
        <div v-if="!trendLoading && !trendError" class="mt-3">
          <EChartCanvas :option="chartOption" style="height: 120px;" />
        </div>
        <div v-else-if="trendLoading" class="mt-3 flex h-[120px] items-center justify-center">
          <iconify-icon icon="solar:refresh-outline" width="20" height="20" class="animate-spin" style="color: var(--muted-foreground);" />
        </div>
      </div>

      <!-- Other metrics quick stats -->
      <div class="flex gap-2">
        <button
          v-for="stat in otherStats"
          :key="stat.key"
          type="button"
          class="min-w-0 flex-1 rounded-2xl p-[14px] text-left transition active:scale-[0.98]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          @click="switchMetric(stat.key)"
        >
          <iconify-icon :icon="stat.icon" width="18" height="18" :style="{ color: stat.color }" />
          <div class="mt-2 flex items-baseline gap-1">
            <span class="text-[20px] font-semibold" style="color: var(--foreground); font-variant-numeric: tabular-nums;">{{ stat.value }}</span>
            <span class="text-[11px]" style="color: var(--muted-foreground);">{{ stat.unit }}</span>
          </div>
          <span class="block truncate text-[12px]" style="color: var(--muted-foreground);">{{ stat.label }}</span>
        </button>
      </div>
    </div>

    <!-- Trend chart card with range selector -->
    <div class="mx-auto mt-3 max-w-[420px] px-4">
      <div
        class="rounded-[19.2px] p-[18px]"
        style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">{{ metricTitle }}趋势</h2>
          <span class="text-[13px]" style="color: var(--muted-foreground);">{{ latestUpdatedAt }}</span>
        </div>

        <!-- Range selector -->
        <div v-if="availableRanges.length > 1" class="mt-3 flex gap-2">
          <button
            v-for="item in availableRanges"
            :key="item.key"
            type="button"
            class="h-8 whitespace-nowrap rounded-full px-3.5 text-[13px] font-medium transition active:scale-[0.98]"
            :style="activeRange === item.key
              ? { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }
              : { background: 'var(--secondary)', color: 'var(--secondary-foreground)' }"
            @click="activeRange = item.key"
          >
            {{ item.label }}
          </button>
        </div>

        <!-- Loading -->
        <div v-if="trendLoading" class="mt-4 flex h-60 items-center justify-center">
          <iconify-icon icon="solar:refresh-outline" width="24" height="24" class="animate-spin" style="color: var(--muted-foreground);" />
        </div>

        <!-- Error -->
        <div v-else-if="trendError" class="mt-4">
          <ClinicalStateNotice
            tone="error"
            title="趋势数据暂不可用"
            :description="trendError"
          />
        </div>

        <!-- Chart + insight -->
        <div v-else class="mt-4 space-y-4">
          <div
            class="h-60 overflow-hidden rounded-[1.2rem] p-3"
            style="background: var(--secondary); border: 1px solid var(--border);"
          >
            <EChartCanvas :option="chartOption" />
          </div>

          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-[1.2rem] px-4 py-3" style="background: var(--secondary); border: 1px solid var(--border);">
              <p class="text-xs" style="color: var(--muted-foreground);">当前趋势</p>
              <p class="mt-2 text-sm leading-6" style="color: var(--foreground);">{{ insight }}</p>
            </div>
            <div class="rounded-[1.2rem] px-4 py-3" style="background: var(--secondary); border: 1px solid var(--border);">
              <p class="text-xs" style="color: var(--muted-foreground);">建议动作</p>
              <p class="mt-2 text-sm leading-6" style="color: var(--foreground);">{{ suggestion }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="mt-3 flex gap-3">
        <button
          type="button"
          class="flex h-11 flex-1 items-center justify-center gap-2 rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--secondary-foreground);"
          @click="goUpload"
        >
          <iconify-icon icon="solar:upload-outline" width="18" height="18" />
          上传资料
        </button>
        <button
          type="button"
          class="flex h-11 flex-1 items-center justify-center gap-2 rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
          style="background: var(--primary); color: var(--primary-foreground);"
          @click="goHome"
        >
          <iconify-icon icon="solar:home-outline" width="18" height="18" />
          返回总览
        </button>
      </div>
    </div>

    <!-- Device Sync Bottom Sheet -->
    <Teleport to="body">
      <Transition name="sheet">
        <div v-if="syncSheetOpen" class="fixed inset-0 z-50 flex items-end justify-center">
          <!-- Backdrop -->
          <div class="absolute inset-0 bg-black/40" @click="syncSheetOpen = false"></div>
          <!-- Sheet -->
          <div
            class="relative w-full max-w-[420px] rounded-t-[20px] px-5 pb-8 pt-4"
            style="background: var(--card); box-shadow: 0 -8px 32px rgba(0,0,0,0.12);"
          >
            <!-- Handle -->
            <div class="mx-auto mb-4 h-1 w-10 rounded-full" style="background: var(--border);"></div>

            <h3 class="text-[17px] font-semibold" style="color: var(--foreground);">同步设备 · {{ metricTitle }}</h3>

            <!-- Loading -->
            <div v-if="syncSheetLoading" class="flex h-32 items-center justify-center">
              <iconify-icon icon="solar:refresh-outline" width="24" height="24" class="animate-spin" style="color: var(--muted-foreground);" />
            </div>

            <div v-else class="mt-4 space-y-3">
              <!-- Connected sources -->
              <div v-if="syncRoute?.connectedSources?.length">
                <p class="mb-2 text-[12px] font-medium uppercase tracking-wide" style="color: var(--muted-foreground);">已连接</p>
                <div
                  v-for="src in syncRoute.connectedSources"
                  :key="src.provider"
                  class="flex items-center gap-3 rounded-[12px] p-3"
                  style="background: var(--secondary);"
                >
                  <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--state-success-surface);">
                    <iconify-icon icon="solar:check-circle-bold" width="18" height="18" style="color: var(--state-success);" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-[14px] font-medium" style="color: var(--foreground);">{{ src.displayName }}</p>
                    <p class="text-[12px]" style="color: var(--muted-foreground);">
                      {{ src.lastSyncAt ? `上次同步 ${new Date(src.lastSyncAt).toLocaleString('zh-CN', { hour12: false })}` : '从未同步' }}
                    </p>
                  </div>
                  <button
                    type="button"
                    class="shrink-0 rounded-full px-3 py-1.5 text-[12px] font-semibold transition active:scale-95"
                    style="background: var(--brand-500); color: var(--primary-foreground);"
                    :disabled="syncingProvider === src.provider"
                    @click="doSyncBinding(src)"
                  >
                    {{ syncingProvider === src.provider ? '同步中…' : '立即同步' }}
                  </button>
                </div>
              </div>

              <!-- Stale sources -->
              <div v-if="syncRoute?.staleSources?.length">
                <p class="mb-2 text-[12px] font-medium uppercase tracking-wide" style="color: var(--muted-foreground);">需要重新同步</p>
                <div
                  v-for="src in syncRoute.staleSources"
                  :key="src.provider"
                  class="flex items-center gap-3 rounded-[12px] p-3"
                  style="background: var(--secondary);"
                >
                  <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: color-mix(in srgb, var(--chart-3) 12%, var(--card));">
                    <iconify-icon icon="solar:clock-circle-outline" width="18" height="18" style="color: var(--chart-3);" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-[14px] font-medium" style="color: var(--foreground);">{{ src.displayName }}</p>
                    <p class="text-[12px]" style="color: var(--muted-foreground);">数据可能过期</p>
                  </div>
                  <button
                    type="button"
                    class="shrink-0 rounded-full px-3 py-1.5 text-[12px] font-semibold transition active:scale-95"
                    style="background: var(--secondary); color: var(--foreground); border: 1px solid var(--border);"
                    :disabled="syncingProvider === src.provider"
                    @click="doSyncBinding(src)"
                  >
                    {{ syncingProvider === src.provider ? '同步中…' : '重新同步' }}
                  </button>
                </div>
              </div>

              <!-- Actions -->
              <div class="flex gap-2 pt-2">
                <button
                  type="button"
                  class="flex h-11 flex-1 items-center justify-center gap-2 rounded-full text-[14px] font-medium transition active:scale-[0.98]"
                  style="background: var(--secondary); color: var(--foreground);"
                  @click="goManualInput"
                >
                  <iconify-icon icon="solar:pen-new-square-outline" width="16" height="16" />
                  手动输入
                </button>
                <button
                  type="button"
                  class="flex h-11 flex-1 items-center justify-center gap-2 rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
                  style="background: var(--brand-500); color: var(--primary-foreground);"
                  @click="goConnectDevice"
                >
                  <iconify-icon icon="solar:add-circle-outline" width="16" height="16" />
                  连接新设备
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MonitorLatest, MonitorMetric, MonitorRange } from '@/api/modules/monitor'
import { getMonitorLatest, getMonitorTrend } from '@/api/modules/monitor'
import type { MetricRouteResponse, SourceItem } from '@/api/modules/deviceAggregation'
import { getMetricRoute, syncBinding, getBindings } from '@/api/modules/deviceAggregation'
import EChartCanvas from '@/shared/components/EChartCanvas.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import { formatDateCN } from '@/shared/utils/date'
import type { EChartsCoreOption } from 'echarts'

const router = useRouter()
const route = useRoute()

const validMetrics: MonitorMetric[] = ['hr', 'sleep', 'stress']
const initialMetric = (): MonitorMetric => {
  const m = route.params.metric as string
  return validMetrics.includes(m as MonitorMetric) ? (m as MonitorMetric) : 'hr'
}

const metricTabs: { key: MonitorMetric; label: string }[] = [
  { key: 'hr', label: '心率' },
  { key: 'sleep', label: '睡眠' },
  { key: 'stress', label: '压力' },
]

const metricTitles: Record<MonitorMetric, string> = {
  hr: '静息心率',
  sleep: '睡眠评分',
  stress: '压力指数',
}

const metricIcons: Record<MonitorMetric, string> = {
  hr: 'solar:heart-pulse-outline',
  sleep: 'solar:moon-stars-outline',
  stress: 'solar:shield-warning-outline',
}

const metricColors: Record<MonitorMetric, string> = {
  hr: 'var(--state-error)',
  sleep: 'var(--chart-4)',
  stress: 'var(--chart-3)',
}

const metricRanges: Record<MonitorMetric, { key: MonitorRange; label: string }[]> = {
  hr: [
    { key: 'hour', label: '今日' },
    { key: 'day', label: '本周' },
    { key: 'month', label: '本月' },
  ],
  sleep: [
    { key: 'day', label: '本周' },
    { key: 'month', label: '本月' },
  ],
  stress: [
    { key: 'hour', label: '今日' },
    { key: 'day', label: '本周' },
    { key: 'month', label: '本月' },
  ],
}

const latest = ref<MonitorLatest>({
  hr: 0,
  sleep: 0,
  deepSleep: 0,
  awake: 0,
  stress: 0,
  updatedAt: '',
})

const currentMetric = ref<MonitorMetric>(initialMetric())
const activeRange = ref<MonitorRange>(metricRanges[currentMetric.value][0].key)

const availableRanges = computed(() => metricRanges[currentMetric.value])
const metricTitle = computed(() => metricTitles[currentMetric.value])
const metricIcon = computed(() => metricIcons[currentMetric.value])
const metricColor = computed(() => metricColors[currentMetric.value])
const today = computed(() => formatDateCN(new Date()))

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

const metricDisplayValue = computed(() => {
  if (currentMetric.value === 'hr') return `${latest.value.hr} bpm`
  if (currentMetric.value === 'sleep') return `${latest.value.sleep} 分`
  return `${latest.value.stress}`
})

const metricHint = computed(() => {
  if (currentMetric.value === 'hr') {
    if (latest.value.hr >= 95) return `最高 ${Math.round(latest.value.hr * 1.2)} · 最低 ${Math.round(latest.value.hr * 0.85)} · 偏高，注意恢复`
    if (latest.value.hr <= 60) return `最高 ${Math.round(latest.value.hr * 1.1)} · 最低 ${Math.round(latest.value.hr * 0.9)} · 偏低，注意热身`
    return `最高 ${Math.round(latest.value.hr * 1.2)} · 最低 ${Math.round(latest.value.hr * 0.85)} · 处于稳定区间`
  }
  if (currentMetric.value === 'sleep') {
    return `深睡 ${latest.value.deepSleep} 小时 · 夜醒 ${latest.value.awake} 次`
  }
  if (latest.value.stress >= 75) return '压力偏高，建议放松呼吸'
  if (latest.value.stress >= 60) return '压力中等，减少额外负荷'
  return '压力可控，维持当前节奏'
})

type QuickStat = {
  key: MonitorMetric
  label: string
  value: string
  unit: string
  icon: string
  color: string
}
const otherStats = computed<QuickStat[]>(() => {
  const all: QuickStat[] = [
    { key: 'hr', label: '心率', value: String(latest.value.hr), unit: 'bpm', icon: metricIcons.hr, color: metricColors.hr },
    { key: 'sleep', label: '睡眠', value: String(latest.value.sleep), unit: '分', icon: metricIcons.sleep, color: metricColors.sleep },
    { key: 'stress', label: '压力', value: String(latest.value.stress), unit: '', icon: metricIcons.stress, color: metricColors.stress },
  ]
  return all.filter((s) => s.key !== currentMetric.value)
})

const chartOption = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 12, right: 12, top: 16, bottom: 18, containLabel: true },
  xAxis: {
    type: 'category',
    data: trendLabels.value,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: 'var(--border)' } },
    axisLabel: { color: 'var(--muted-foreground)', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: 'var(--border)' } },
    axisLabel: { color: 'var(--muted-foreground)', fontSize: 11 },
  },
  series: [
    {
      name: metricTitle.value,
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: trendValues.value,
      lineStyle: { width: 2.5, color: 'var(--brand-500)' },
      itemStyle: { color: 'var(--brand-500)' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(0, 122, 255, 0.18)' },
            { offset: 1, color: 'rgba(0, 122, 255, 0)' },
          ],
        },
      },
    },
  ],
}))

const switchMetric = (metric: MonitorMetric) => {
  currentMetric.value = metric
  activeRange.value = metricRanges[metric][0].key
  router.replace(`/monitor/${metric}`)
}

const loadLatest = async () => {
  latest.value = await getMonitorLatest()
}

const loadTrend = async () => {
  trendLoading.value = true
  trendError.value = ''
  try {
    const data = await getMonitorTrend(currentMetric.value, activeRange.value)
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
  () => route.params.metric,
  (metric) => {
    if (validMetrics.includes(metric as MonitorMetric)) {
      currentMetric.value = metric as MonitorMetric
      activeRange.value = metricRanges[currentMetric.value][0].key
    }
  },
)

watch(
  [currentMetric, activeRange],
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

// ===== Device Sync Bottom Sheet =====
const syncSheetOpen = ref(false)
const syncSheetLoading = ref(false)
const syncRoute = ref<MetricRouteResponse | null>(null)
const syncingProvider = ref('')

/** metric key → device_metric_routes metric 映射 */
const metricToRouteKey: Record<MonitorMetric, string> = {
  hr: 'heart_rate',
  sleep: 'sleep_duration',
  stress: 'heart_rate',
}

const openSyncSheet = async () => {
  syncSheetOpen.value = true
  syncSheetLoading.value = true
  syncRoute.value = null
  try {
    const routeKey = metricToRouteKey[currentMetric.value] || 'heart_rate'
    syncRoute.value = await getMetricRoute(routeKey)
  } catch {
    syncRoute.value = null
  } finally {
    syncSheetLoading.value = false
  }
}

const doSyncBinding = async (src: SourceItem) => {
  syncingProvider.value = src.provider
  try {
    // 找到对应 binding id
    const bindings = await getBindings()
    const binding = bindings.find((b) => b.provider === src.provider)
    if (binding) {
      await syncBinding(binding.id)
    }
  } catch {
    // 静默失败
  } finally {
    syncingProvider.value = ''
    // 刷新路由信息
    await openSyncSheet()
  }
}

const goManualInput = () => {
  syncSheetOpen.value = false
  const routeKey = metricToRouteKey[currentMetric.value] || 'heart_rate'
  router.push(`/devices/metric/${routeKey}`)
}

const goConnectDevice = () => {
  syncSheetOpen.value = false
  router.push('/devices/brands')
}
</script>

<style scoped>
.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.25s ease;
}
.sheet-enter-active > div:last-child,
.sheet-leave-active > div:last-child {
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}
.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}
.sheet-enter-from > div:last-child,
.sheet-leave-to > div:last-child {
  transform: translateY(100%);
}
</style>
