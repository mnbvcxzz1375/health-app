<template>
  <div class="space-y-5 pb-4 text-slate-950 lg:space-y-6 lg:pb-6">
    <HomeHeroOverview :summary="summary" :display-name="displayName" :today="today" />

    <v-card v-if="viewState === 'loading'" class="pa-4" rounded="xl" elevation="0">
      <v-skeleton-loader type="card, card, card" />
    </v-card>

    <ClinicalStateNotice
      v-else-if="viewState === 'error'"
      tone="error"
      title="首页加载失败"
      :description="viewError || '暂时无法获取首页数据，请稍后重试。'"
      action-label="重新加载"
      @action="loadDashboard"
    />

    <ClinicalStateNotice
      v-else-if="viewState === 'empty'"
      tone="empty"
      title="暂未形成健康画像"
      description="先上传资料或连接设备。"
      action-label="去上传"
      @action="goUpload"
    />

    <template v-else>
      <section class="grid grid-cols-1 gap-3 lg:grid-cols-3">
        <ClinicalStatCard
          v-for="card in metricCards"
          :key="card.key"
          :label="card.label"
          :value="card.value"
          :hint="card.hint"
          :icon="card.icon"
          :tone="card.tone"
        />
      </section>

      <ClinicalSurfaceCard title="监测趋势">
        <div class="grid gap-3 lg:grid-cols-3">
          <ClinicalStatCard
            label="静息心率"
            :value="`${latest.hr} bpm`"
            :hint="heartHint"
            icon="solar:heart-pulse-outline"
            :tone="latest.hr >= 95 ? 'danger' : 'success'"
          />
          <ClinicalStatCard
            label="睡眠评分"
            :value="`${latest.sleep}`"
            :hint="`深睡 ${latest.deepSleep} 小时，夜醒 ${latest.awake} 次`"
            icon="solar:moon-stars-outline"
            tone="info"
          />
          <ClinicalStatCard
            label="压力指数"
            :value="`${latest.stress}`"
            :hint="stressHint"
            icon="solar:shield-warning-outline"
            :tone="latest.stress >= 70 ? 'warning' : 'success'"
          />
        </div>

        <div class="mt-4 space-y-3">
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

        <v-card v-if="trendLoading" class="mt-4 pa-4" rounded="xl" elevation="0">
          <v-skeleton-loader type="article, actions" />
        </v-card>

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
        </div>
      </ClinicalSurfaceCard>

      <section class="grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(340px,0.85fr)]">
        <div class="space-y-5">
          <ClinicalSurfaceCard v-if="savedReports.length" title="已保存报告">
            <div class="space-y-3">
              <button
                type="button"
                class="flex w-full items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-left"
                @click="reportSectionExpanded = !reportSectionExpanded"
              >
                <div>
                  <p class="text-sm font-semibold text-slate-950">已保存 {{ savedReports.length }} 份分析报告</p>
                  <p class="mt-1 text-xs text-slate-500">默认折叠显示，展开后可查看最近结果。</p>
                </div>
                <iconify-icon
                  :icon="reportSectionExpanded ? 'solar:alt-arrow-up-outline' : 'solar:alt-arrow-down-outline'"
                  width="18"
                  height="18"
                  class="text-slate-500"
                />
              </button>

              <div v-if="reportSectionExpanded" class="space-y-3">
                <article
                  v-for="item in savedReports"
                  :key="item.taskId"
                  class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-white px-4 py-4"
                >
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="truncate text-sm font-semibold text-slate-950">{{ item.report.title }}</p>
                      <p class="mt-1 text-xs text-slate-500">
                        {{ reportTypeLabel(item.type) }}{{ item.fileName ? ` · ${item.fileName}` : '' }} ·
                        {{ formatReportTime(item.updatedAt) }}
                      </p>
                    </div>
                    <div class="flex items-center gap-2">
                      <v-chip
                        :color="riskLevelColor(item.report.riskLevel)"
                        size="small"
                        variant="tonal"
                        label
                      >
                        {{ item.report.riskLevel }}
                      </v-chip>
                      <button
                        type="button"
                        class="rounded-full border border-[color:var(--surface-border)] px-3 py-1 text-xs font-medium text-slate-600 transition hover:bg-[color:var(--surface-secondary)]"
                        @click.stop="selectedReport = item"
                      >
                        全览
                      </button>
                      <button
                        type="button"
                        class="rounded-full border border-rose-200 px-3 py-1 text-xs font-medium text-rose-600 transition hover:bg-rose-50"
                        @click.stop="removeSavedReport(item.taskId)"
                      >
                        删除
                      </button>
                    </div>
                  </div>

                  <p class="mt-3 text-sm leading-6 text-slate-700">{{ item.report.summary }}</p>
                  <ul class="mt-3 list-disc space-y-1.5 pl-5 text-sm leading-6 text-slate-700">
                    <li v-for="point in item.report.points.slice(0, 2)" :key="point">{{ point }}</li>
                  </ul>
                  <p class="mt-3 text-xs leading-6 text-slate-500">康复重点：{{ item.report.rehabFocus }}</p>
                </article>
              </div>
            </div>
          </ClinicalSurfaceCard>

          <ClinicalSurfaceCard title="快捷入口">
            <div class="grid gap-3 sm:grid-cols-3">
              <button
                v-for="action in actionItems"
                :key="action.key"
                type="button"
                class="flex items-center justify-between rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4 text-left transition hover:border-[color:var(--accent-strong)] hover:bg-white"
                @click="router.push(action.route)"
              >
                <div class="flex items-center gap-3">
                  <div class="inline-flex h-10 w-10 items-center justify-center rounded-2xl bg-white text-slate-900 shadow-[var(--elevation-soft)]">
                    <iconify-icon :icon="action.icon" width="18" height="18" />
                  </div>
                  <span class="text-sm font-semibold text-slate-950">{{ action.title }}</span>
                </div>

                <iconify-icon icon="solar:alt-arrow-right-outline" width="16" height="16" class="text-slate-400" />
              </button>
            </div>
          </ClinicalSurfaceCard>

          <ClinicalSurfaceCard title="风险提示">
            <template #headerRight>
              <v-chip
                :color="statusChipColor"
                size="small"
                variant="tonal"
                label
              >
                {{ summary?.statusBadge ?? '总览' }}
              </v-chip>
            </template>

            <div class="space-y-2">
              <div
                v-for="item in suggestions"
                :key="item"
                class="flex items-start gap-3 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
              >
                <iconify-icon icon="solar:shield-check-outline" width="18" height="18" class="mt-1 text-emerald-700" />
                <span class="text-sm leading-6 text-slate-700">{{ item }}</span>
              </div>
            </div>
          </ClinicalSurfaceCard>

          <HomeConsultPanel
            :question="consultQuestion"
            :chips="consultChips"
            :loading="consultLoading"
            :response="consultResponse"
            @update:question="consultQuestion = $event"
            @pick-chip="consultQuestion = $event"
            @submit="submitConsult"
          />
        </div>

        <div class="space-y-5">
          <ClinicalSurfaceCard title="今日计划">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
              <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
                <p class="text-xs text-slate-500">训练重点</p>
                <p class="mt-2 text-base font-semibold text-slate-950">{{ planSnapshot.focus }}</p>
              </div>
              <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
                <p class="text-xs text-slate-500">频次</p>
                <p class="mt-2 text-base font-semibold text-slate-950">{{ planSnapshot.frequency }}</p>
              </div>
              <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
                <p class="text-xs text-slate-500">时长</p>
                <p class="mt-2 text-base font-semibold text-slate-950">{{ planSnapshot.duration }}</p>
              </div>
              <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
                <p class="text-xs text-slate-500">强度</p>
                <p class="mt-2 text-base font-semibold text-slate-950">{{ planSnapshot.intensity }}</p>
              </div>
              <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 sm:col-span-2 xl:col-span-1 2xl:col-span-2">
                <p class="text-xs text-slate-500">任务数</p>
                <p class="mt-2 text-base font-semibold text-slate-950">{{ planSnapshot.exerciseCount }} 项</p>
              </div>
            </div>

            <div class="mt-4 flex flex-wrap gap-2">
              <Button variant="secondary" @click="goUpload">
                <iconify-icon icon="solar:upload-outline" width="16" height="16" />
                上传资料
              </Button>
              <Button @click="goRehab">
                <iconify-icon icon="solar:wheel-outline" width="16" height="16" />
                进入康复
              </Button>
            </div>
          </ClinicalSurfaceCard>

          <HomeDevicePanel
            :devices="devices"
            :show-form="showAddDeviceForm"
            :creating="creatingDevice"
            :syncing-id="syncingId"
            :form="deviceForm"
            @open-form="showAddDeviceForm = true"
            @cancel-form="cancelAddDevice"
            @save-device="confirmAddDevice"
            @sync-device="syncOneDevice"
            @remove-device="removeOneDevice"
            @update:form="updateDeviceForm"
            @connect-ble="connectBluetoothDevice"
            @sync-apple-health="syncAppleHealthDevice"
          />
        </div>
      </section>
    </template>

    <div
      v-if="selectedReport"
      class="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 px-4 py-8 overscroll-contain"
      @click.self="selectedReport = null"
      @wheel.stop
      @touchmove.stop
    >
      <div
        class="max-h-[88vh] w-full max-w-3xl overflow-auto rounded-[1.75rem] bg-white p-5 shadow-[0_30px_80px_rgba(15,23,42,0.25)] overscroll-contain"
        @wheel.stop
        @touchmove.stop
      >
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="text-lg font-semibold text-slate-950">{{ selectedReport.report.title }}</p>
            <p class="mt-1 text-xs text-slate-500">
              {{ reportTypeLabel(selectedReport.type) }}{{ selectedReport.fileName ? ` / ${selectedReport.fileName}` : '' }} /
              {{ formatReportTime(selectedReport.updatedAt) }}
            </p>
          </div>
          <div class="flex items-center gap-2">
            <v-chip
              :color="riskLevelColor(selectedReport.report.riskLevel)"
              size="small"
              variant="tonal"
              label
            >
              {{ selectedReport.report.riskLevel }}
            </v-chip>
            <button
              type="button"
              class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-[color:var(--surface-border)] text-slate-500 transition hover:bg-[color:var(--surface-secondary)] hover:text-slate-900"
              aria-label="关闭"
              @click="selectedReport = null"
            >
              <iconify-icon icon="solar:close-circle-outline" width="20" height="20" />
            </button>
          </div>
        </div>

        <div class="mt-5 space-y-4">
          <section class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <p class="text-sm font-semibold text-slate-950">报告摘要</p>
            <p class="mt-2 text-sm leading-6 text-slate-700">{{ selectedReport.report.summary }}</p>
            <p class="mt-3 text-sm leading-6 text-slate-700">康复重点：{{ selectedReport.report.rehabFocus }}</p>
          </section>

          <div class="grid gap-4 lg:grid-cols-2">
            <section class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
              <p class="text-sm font-semibold text-slate-950">关注点</p>
              <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
                <li v-for="point in selectedReport.report.points" :key="point">{{ point }}</li>
              </ul>
            </section>

            <section class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
              <p class="text-sm font-semibold text-slate-950">建议</p>
              <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
                <li v-for="advice in selectedReport.report.advice" :key="advice">{{ advice }}</li>
              </ul>
            </section>
          </div>

          <section class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <p class="text-sm font-semibold text-slate-950">后续观察</p>
            <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
              <li v-for="item in selectedReport.report.followUp" :key="item">{{ item }}</li>
            </ul>
            <p class="mt-4 text-xs leading-6 text-slate-500">{{ selectedReport.report.caution }}</p>
          </section>
        </div>
      </div>
    </div>

    <p class="px-1 text-center text-xs leading-6 text-slate-500">
      以上内容仅用于健康管理辅助，不替代医生诊断与治疗。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CreateDevicePayload } from '@/api/modules/device'
import { getMonitorLatest, getMonitorTrend, type MonitorLatest, type MonitorMetric, type MonitorRange } from '@/api/modules/monitor'
import type { SavedAnalyzeReport } from '@/api/modules/upload'
import HomeConsultPanel from '@/modules/home/components/HomeConsultPanel.vue'
import HomeDevicePanel from '@/modules/home/components/HomeDevicePanel.vue'
import HomeHeroOverview from '@/modules/home/components/HomeHeroOverview.vue'
import { useHomeDashboard } from '@/modules/home/composables/useHomeDashboard'
import { getPlanSnapshot, toHomeMetricCards, toPlanActions, toRiskTone } from '@/modules/home/view-models'
import EChartCanvas from '@/shared/components/EChartCanvas.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Button from '@/shared/components/ui/Button.vue'
import { formatDateCN } from '@/shared/utils/date'
import { useAuthStore } from '@/stores/auth'
import type { EChartsCoreOption } from 'echarts'

const router = useRouter()
const authStore = useAuthStore()
const {
  viewState,
  summary,
  rehabPlan,
  devices,
  savedReports,
  viewError,
  healthInsight,
  consultQuestion,
  consultResponse,
  consultLoading,
  consultChips,
  showAddDeviceForm,
  creatingDevice,
  syncingId,
  deviceForm,
  loadDashboard,
  submitConsult,
  cancelAddDevice,
  confirmAddDevice,
  syncOneDevice,
  removeOneDevice,
  removeSavedReport,
  connectBluetoothDevice,
  syncAppleHealthDevice,
} = useHomeDashboard()

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
const reportSectionExpanded = ref(false)
const selectedReport = ref<SavedAnalyzeReport | null>(null)
const trendLabels = ref<string[]>([])
const trendValues = ref<number[]>([])
const insight = ref('暂无数据')
const suggestion = ref('暂无数据')
const trendLoading = ref(true)
const trendError = ref('')

const today = computed(() => formatDateCN(new Date()))
const displayName = computed(() => summary.value?.userName ?? authStore.userName)
const metricCards = computed(() => toHomeMetricCards(summary.value))
const actionItems = computed(() => toPlanActions(summary.value, rehabPlan.value))
const planSnapshot = computed(() => getPlanSnapshot(rehabPlan.value))
const suggestions = computed(() => {
  const items = summary.value?.suggestions ?? []
  return items.length ? items : ['请先上传资料或连接设备。']
})
const statusTone = computed(() => toRiskTone(summary.value?.statusBadgeVariant))
const statusChipColor = computed(() => {
  const tone = statusTone.value
  if (tone === 'danger') return 'error'
  if (tone === 'warning') return 'warning'
  if (tone === 'success') return 'success'
  if (tone === 'info') return 'info'
  return 'grey'
})

const riskLevelColor = (riskLevel: string) => {
  if (riskLevel === '高风险') return 'error'
  if (riskLevel === '中等风险') return 'warning'
  return 'success'
}

const heartHint = computed(() => {
  if (latest.value.hr >= 95) return '训练前建议先观察恢复状态。'
  if (latest.value.hr <= 60) return '当前偏低，注意热身与体感。'
  return '处于相对稳定区间。'
})

const stressHint = computed(() => {
  if (latest.value.stress >= 75) return '建议先做放松呼吸与恢复训练。'
  if (latest.value.stress >= 60) return '建议减少今日额外负荷。'
  return '可维持原定训练节奏。'
})

const activeTabTitle = computed(() => tabs.find((item) => item.key === activeTab.value)?.label ?? '')

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

const reportTypeLabel = (type: string) => {
  if (type === 'image') return '影像资料'
  if (type === 'lab') return '化验报告'
  if (type === 'symptom') return '症状描述'
  return '文字报告'
}

const formatReportTime = (value: string) => {
  if (!value) return '刚刚保存'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

const updateDeviceForm = (payload: { key: keyof CreateDevicePayload; value: string }) => {
  if (payload.key === 'type') {
    deviceForm.type = payload.value as CreateDevicePayload['type']
    return
  }
  deviceForm[payload.key] = payload.value
}

const loadLatest = async () => {
  if (!authStore.isAuthenticated) return
  latest.value = await getMonitorLatest()
}

const loadTrend = async () => {
  if (!authStore.isAuthenticated) return
  trendLoading.value = true
  trendError.value = ''
  try {
    const data = await getMonitorTrend(activeTab.value, activeRange.value)
    trendLabels.value = data.labels
    trendValues.value = data.values
    insight.value = data.insight
    suggestion.value = data.suggestion
  } catch (err) {
    trendLabels.value = []
    trendValues.value = []
    trendError.value = err instanceof Error ? err.message : '请稍后重试或补充监测记录。'
    insight.value = '暂无趋势数据'
    suggestion.value = '请稍后重试或补充监测记录。'
  } finally {
    trendLoading.value = false
  }
}

watch(
  [activeTab, activeRange],
  () => {
    if (authStore.isAuthenticated) {
      void loadTrend()
    }
  },
  { immediate: true },
)

watch(
  selectedReport,
  (value) => {
    if (typeof document === 'undefined') return
    document.body.style.overflow = value ? 'hidden' : ''
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.body.style.overflow = ''
  }
})

const goUpload = () => router.push('/upload')
const goRehab = () => router.push('/rehab')

onMounted(() => {
  if (!authStore.isAuthenticated) return
  void loadDashboard()
  void loadLatest()
})
</script>
