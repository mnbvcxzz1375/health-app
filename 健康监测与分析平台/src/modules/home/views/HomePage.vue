<template>
  <div class="apple-home pb-4">
    <!-- Loading -->
    <div v-if="viewState === 'loading'" class="space-y-4 px-4 pt-6">
      <div class="h-7 w-40 animate-pulse rounded-lg bg-[color:var(--background-300)]" />
      <div class="mx-auto mt-6 h-40 w-40 animate-pulse rounded-full bg-[color:var(--background-300)]" />
      <div class="flex gap-2">
        <div v-for="i in 3" :key="i" class="h-24 flex-1 animate-pulse rounded-2xl bg-[color:var(--background-300)]" />
      </div>
    </div>

    <!-- Error -->
    <div v-else-if="viewState === 'error'" class="px-4 pt-6">
      <ClinicalStateNotice
        tone="error"
        title="首页加载失败"
        :description="viewError || '暂时无法获取首页数据，请稍后重试。'"
        action-label="重新加载"
        @action="loadDashboard"
      />
    </div>

    <!-- Empty -->
    <div v-else-if="viewState === 'empty'" class="px-4 pt-6">
      <ClinicalStateNotice
        tone="empty"
        title="暂未形成健康画像"
        description="先上传资料或连接设备。"
        action-label="去上传"
        @action="goUpload"
      />
    </div>

    <!-- Main Content -->
    <template v-else>
      <div class="mx-auto max-w-[420px] px-4 pt-6">
        <header>
          <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground); line-height: 1.15;">总览</h1>
        </header>

        <!-- 1. Health Score Ring -->
        <section class="mt-8 flex flex-col items-center">
          <div
            class="flex items-center justify-center"
            style="width: 160px; height: 160px; border-radius: 9999px; background: conic-gradient(var(--brand-500) 0deg ${scoreDeg}deg, var(--background-200) ${scoreDeg}deg);"
          >
            <div
              class="flex flex-col items-center justify-center gap-0.5"
              style="width: 128px; height: 128px; border-radius: 9999px; background: var(--card);"
            >
              <span style="font-size: 48px; font-weight: 600; color: var(--foreground); font-variant-numeric: tabular-nums; line-height: 1;">{{ displayScore }}</span>
              <span style="font-size: 13px; color: var(--muted-foreground);">健康评分</span>
            </div>
          </div>
          <div v-if="scoreDelta" class="mt-3 inline-flex items-center gap-1 rounded-full px-2.5 py-1">
            <iconify-icon
              :icon="scoreDelta >= 0 ? 'solar:arrow-up-outline' : 'solar:arrow-down-outline'"
              width="12"
              height="12"
              :style="{ color: scoreDelta >= 0 ? 'var(--state-success)' : 'var(--state-error)' }"
            />
            <span
              class="whitespace-nowrap text-[12px] font-medium"
              :style="{ color: scoreDelta >= 0 ? 'var(--state-success)' : 'var(--state-error)' }"
            >
              较上周 {{ scoreDelta >= 0 ? '+' : '' }}{{ scoreDelta }}
            </span>
          </div>
          <p
            v-if="scoreDataWarning"
            class="mt-3 max-w-[320px] text-center text-[12px] leading-5"
            style="color: var(--muted-foreground);"
          >
            {{ scoreDataWarning }}
          </p>
        </section>

        <!-- 3. Key Metrics Row -->
        <section class="mt-6 flex gap-2">
          <button
            v-for="metric in keyMetricCards"
            :key="metric.key"
            type="button"
            class="min-w-0 flex-1 rounded-2xl p-[14px] text-left transition active:scale-[0.98]"
            style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
            @click="metric.route ? router.push(metric.route) : null"
          >
            <iconify-icon :icon="metric.icon" width="20" height="20" :style="{ color: metric.color }" />
            <div class="mt-2 flex items-baseline gap-1">
              <span class="text-[24px] font-semibold" style="color: var(--foreground); font-variant-numeric: tabular-nums;">{{ metric.value }}</span>
              <span class="text-[12px]" style="color: var(--muted-foreground);">{{ metric.unit }}</span>
            </div>
            <span class="block truncate text-[13px]" style="color: var(--muted-foreground);">{{ metric.label }}</span>
          </button>
        </section>

        <!-- 4. Today's Plan Card -->
        <section
          class="mt-3 rounded-[19.2px] p-[18px]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center justify-between">
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">今日计划</h2>
            <span class="text-[13px]" style="color: var(--muted-foreground); font-variant-numeric: tabular-nums;">{{ planSnapshot.exerciseCount }} 项训练</span>
          </div>
          <div class="mt-3" style="height: 1px; background: var(--border);" />

          <!-- Plan summary items -->
          <div class="flex items-center gap-3 py-3">
            <div
              class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full"
              style="background: var(--state-success);"
            >
              <iconify-icon icon="solar:check-circle-outline" width="14" height="14" style="color: var(--state-success-foreground);" />
            </div>
            <iconify-icon icon="solar:dumbbell-outline" width="20" height="20" class="shrink-0" style="color: var(--muted-foreground);" />
            <span class="min-w-0 flex-1 truncate text-[15px]" style="color: var(--foreground);">{{ planSnapshot.focus }}</span>
            <span class="shrink-0 whitespace-nowrap text-[13px]" style="color: var(--muted-foreground);">{{ planSnapshot.duration }}</span>
          </div>
          <div class="flex items-center gap-3 py-3">
            <div
              class="h-6 w-6 shrink-0 rounded-full"
              style="border: 1px solid var(--border);"
            />
            <iconify-icon icon="solar:repeat-outline" width="20" height="20" class="shrink-0" style="color: var(--muted-foreground);" />
            <span class="min-w-0 flex-1 truncate text-[15px]" style="color: var(--foreground);">{{ planSnapshot.frequency }}</span>
            <span class="shrink-0 whitespace-nowrap text-[13px]" style="color: var(--muted-foreground);">{{ planSnapshot.intensity }}</span>
          </div>

          <!-- Quick action buttons -->
          <div class="mt-2 flex gap-2">
            <button
              type="button"
              class="flex h-10 flex-1 items-center justify-center gap-1.5 rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
              style="background: var(--secondary); color: var(--secondary-foreground);"
              @click="goUpload"
            >
              <iconify-icon icon="solar:upload-outline" width="16" height="16" />
              上传资料
            </button>
            <button
              type="button"
              class="flex h-10 flex-1 items-center justify-center gap-1.5 rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
              style="background: var(--primary); color: var(--primary-foreground);"
              @click="goRehab"
            >
              <iconify-icon icon="solar:wheel-outline" width="16" height="16" />
              进入康复
            </button>
          </div>
        </section>

        <!-- 5. Quick Actions Row -->
        <section class="mt-3 flex gap-3">
          <button
            type="button"
            class="flex h-12 flex-1 items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98]"
            style="background: var(--primary); color: var(--primary-foreground);"
            @click="scrollToConsult"
          >
            <iconify-icon icon="solar:chat-round-line-outline" width="20" height="20" />
            AI 问诊
          </button>
          <button
            type="button"
            class="flex h-12 flex-1 items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--secondary-foreground);"
            @click="router.push('/monitor/hr')"
          >
            <iconify-icon icon="solar:graph-up-outline" width="20" height="20" />
            健康趋势
          </button>
        </section>
        <button
          type="button"
          class="mt-3 flex h-12 w-full items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--secondary-foreground);"
          @click="goUpload"
        >
          <iconify-icon icon="solar:upload-cloud-outline" width="20" height="20" />
          上传分析
        </button>

        <!-- 6. Feature Grid -->
        <section class="mt-4">
          <h2 class="mb-2 ml-1 text-[13px] font-normal uppercase tracking-[0.02em]" style="color: var(--muted-foreground);">全部功能</h2>
          <div class="grid grid-cols-4 gap-2">
            <button
              v-for="feat in featureGrid"
              :key="feat.to"
              type="button"
              class="flex flex-col items-center gap-1.5 rounded-[14px] border p-2.5 transition active:scale-[0.96]"
              style="background: var(--card); border-color: var(--border);"
              @click="router.push(feat.to)"
            >
              <div
                class="flex h-9 w-9 items-center justify-center rounded-[10px]"
                :style="{ background: feat.bg }"
              >
                <iconify-icon :icon="feat.icon" width="20" height="20" :style="{ color: feat.color }" />
              </div>
              <span class="text-[11px] font-medium leading-tight" style="color: var(--foreground);">{{ feat.label }}</span>
            </button>
          </div>
        </section>
      </div>

      <!-- Preserved sections: saved reports, suggestions, devices, consult panel -->
      <div class="mx-auto mt-6 max-w-[420px] space-y-3 px-4">
        <!-- Saved Reports (collapsible) -->
        <ClinicalSurfaceCard v-if="savedReports.length" title="已保存报告">
          <div class="space-y-3">
            <button
              type="button"
              class="flex w-full items-center justify-between rounded-[1.2rem] px-4 py-3 text-left"
              style="background: var(--secondary); border: 1px solid var(--border);"
              @click="reportSectionExpanded = !reportSectionExpanded"
            >
              <div>
                <p class="text-sm font-semibold" style="color: var(--foreground);">已保存 {{ savedReports.length }} 份分析报告</p>
                <p class="mt-1 text-xs" style="color: var(--muted-foreground);">默认折叠显示，展开后可查看最近结果。</p>
              </div>
              <iconify-icon
                :icon="reportSectionExpanded ? 'solar:alt-arrow-up-outline' : 'solar:alt-arrow-down-outline'"
                width="18"
                height="18"
                style="color: var(--muted-foreground);"
              />
            </button>

            <div v-if="reportSectionExpanded" class="space-y-3">
              <article
                v-for="item in savedReports"
                :key="item.taskId"
                class="rounded-[1.2rem] px-4 py-4"
                style="background: var(--card); border: 1px solid var(--border);"
              >
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold" style="color: var(--foreground);">{{ item.report.title }}</p>
                    <p class="mt-1 text-xs" style="color: var(--muted-foreground);">
                      {{ reportTypeLabel(item.type) }}{{ item.fileName ? ` · ${item.fileName}` : '' }} ·
                      {{ formatReportTime(item.updatedAt) }}
                    </p>
                  </div>
                  <div class="flex items-center gap-2">
                    <span
                      class="rounded-full px-2 py-0.5 text-xs font-medium"
                      :style="riskLevelStyle(item.report.riskLevel)"
                    >
                      {{ item.report.riskLevel }}
                    </span>
                    <button
                      type="button"
                      class="rounded-full border px-3 py-1 text-xs font-medium transition"
                      style="border-color: var(--border); color: var(--muted-foreground);"
                      @click.stop="selectedReport = item"
                    >
                      全览
                    </button>
                    <button
                      type="button"
                      class="rounded-full border px-3 py-1 text-xs font-medium transition"
                      style="border-color: var(--state-error); color: var(--state-error);"
                      @click.stop="removeSavedReport(item.taskId)"
                    >
                      删除
                    </button>
                  </div>
                </div>

                <p class="mt-3 text-sm leading-6" style="color: var(--foreground);">{{ item.report.summary }}</p>
                <ul class="mt-3 list-disc space-y-1.5 pl-5 text-sm leading-6" style="color: var(--foreground);">
                  <li v-for="point in item.report.points.slice(0, 2)" :key="point">{{ point }}</li>
                </ul>
                <p class="mt-3 text-xs leading-6" style="color: var(--muted-foreground);">康复重点：{{ item.report.rehabFocus }}</p>
              </article>
            </div>
          </div>
        </ClinicalSurfaceCard>

        <!-- Risk Suggestions -->
        <ClinicalSurfaceCard title="风险提示">
          <template #headerRight>
            <span
              class="rounded-full px-2 py-0.5 text-xs font-medium"
              :style="statusChipStyle"
            >
              {{ summary?.statusBadge ?? '总览' }}
            </span>
          </template>

          <div class="space-y-2">
            <div
              v-for="(item, idx) in suggestions"
              :key="idx"
              class="flex items-start gap-3 rounded-[1.2rem] px-4 py-3"
              style="background: var(--secondary); border: 1px solid var(--border);"
            >
              <iconify-icon icon="solar:shield-check-outline" width="18" height="18" class="mt-1 shrink-0" style="color: var(--state-success);" />
              <span class="text-sm leading-6" style="color: var(--foreground);">{{ item }}</span>
            </div>
          </div>
        </ClinicalSurfaceCard>

        <!-- AI Consult Panel -->
        <div ref="consultSectionRef">
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

        <!-- Device Management -->
        <ClinicalSurfaceCard title="设备管理">
          <template #headerRight>
            <span v-if="devices.length" class="rounded-full px-2 py-0.5 text-xs font-medium" style="background: var(--brand-50); color: var(--brand-500);">
              {{ devices.length }} 台
            </span>
          </template>

          <p class="text-sm" style="color: var(--muted-foreground);">连接 Apple Watch、蓝牙设备等，同步健康数据。</p>

          <ClinicalStateNotice
            v-if="!devices.length"
            tone="empty"
            title="暂未连接设备"
            description="前往设备管理页连接 Apple Watch 或蓝牙设备。"
          />

          <div v-else class="mt-3 space-y-2">
            <div
              v-for="item in devices.slice(0, 3)"
              :key="item.id"
              class="flex items-center gap-3 rounded-[1.2rem] px-3 py-2.5"
              style="background: var(--secondary); border: 1px solid var(--border);"
            >
              <div
                class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-xl"
                style="background: var(--card);"
              >
                <iconify-icon icon="solar:watch-square-outline" width="16" height="16" style="color: var(--foreground);" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-semibold" style="color: var(--foreground);">{{ item.name }}</p>
                <p class="text-xs" style="color: var(--muted-foreground);">{{ item.connected ? '已连接' : '未连接' }}</p>
              </div>
            </div>
          </div>

          <div class="mt-3">
            <button
              type="button"
              class="flex h-10 w-full items-center justify-center gap-1.5 rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
              style="background: var(--secondary); color: var(--secondary-foreground);"
              @click="router.push('/devices')"
            >
              <iconify-icon icon="solar:devices-outline" width="16" height="16" />
              进入设备管理
            </button>
          </div>
        </ClinicalSurfaceCard>

        <p class="px-1 text-center text-xs leading-6" style="color: var(--muted-foreground);">
          以上内容仅用于健康管理辅助，不替代医生诊断与治疗。
        </p>
      </div>
    </template>

    <!-- Report Detail Modal -->
    <div
      v-if="selectedReport"
      class="fixed inset-0 z-40 flex items-center justify-center overscroll-contain px-4 py-8"
      style="background: rgba(0, 0, 0, 0.45);"
      @click.self="selectedReport = null"
      @wheel.stop
      @touchmove.stop
    >
      <div
        class="max-h-[88vh] w-full max-w-3xl overflow-auto rounded-[1.75rem] p-5 overscroll-contain"
        style="background: var(--card); box-shadow: 0 30px 80px rgba(0, 0, 0, 0.25);"
        @wheel.stop
        @touchmove.stop
      >
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="text-lg font-semibold" style="color: var(--foreground);">{{ selectedReport.report.title }}</p>
            <p class="mt-1 text-xs" style="color: var(--muted-foreground);">
              {{ reportTypeLabel(selectedReport.type) }}{{ selectedReport.fileName ? ` / ${selectedReport.fileName}` : '' }} /
              {{ formatReportTime(selectedReport.updatedAt) }}
            </p>
          </div>
          <div class="flex items-center gap-2">
            <span
              class="rounded-full px-2 py-0.5 text-xs font-medium"
              :style="riskLevelStyle(selectedReport.report.riskLevel)"
            >
              {{ selectedReport.report.riskLevel }}
            </span>
            <button
              type="button"
              class="inline-flex h-10 w-10 items-center justify-center rounded-full border transition"
              style="border-color: var(--border); color: var(--muted-foreground);"
              aria-label="关闭"
              @click="selectedReport = null"
            >
              <iconify-icon icon="solar:close-circle-outline" width="20" height="20" />
            </button>
          </div>
        </div>

        <div class="mt-5 space-y-4">
          <section class="rounded-[1.2rem] px-4 py-4" style="background: var(--secondary); border: 1px solid var(--border);">
            <p class="text-sm font-semibold" style="color: var(--foreground);">报告摘要</p>
            <p class="mt-2 text-sm leading-6" style="color: var(--foreground);">{{ selectedReport.report.summary }}</p>
            <p class="mt-3 text-sm leading-6" style="color: var(--foreground);">康复重点：{{ selectedReport.report.rehabFocus }}</p>
          </section>

          <div class="grid gap-4 lg:grid-cols-2">
            <section class="rounded-[1.2rem] px-4 py-4" style="background: var(--secondary); border: 1px solid var(--border);">
              <p class="text-sm font-semibold" style="color: var(--foreground);">关注点</p>
              <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6" style="color: var(--foreground);">
                <li v-for="point in selectedReport.report.points" :key="point">{{ point }}</li>
              </ul>
            </section>

            <section class="rounded-[1.2rem] px-4 py-4" style="background: var(--secondary); border: 1px solid var(--border);">
              <p class="text-sm font-semibold" style="color: var(--foreground);">建议</p>
              <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6" style="color: var(--foreground);">
                <li v-for="advice in selectedReport.report.advice" :key="advice">{{ advice }}</li>
              </ul>
            </section>
          </div>

          <section class="rounded-[1.2rem] px-4 py-4" style="background: var(--secondary); border: 1px solid var(--border);">
            <p class="text-sm font-semibold" style="color: var(--foreground);">后续观察</p>
            <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6" style="color: var(--foreground);">
              <li v-for="item in selectedReport.report.followUp" :key="item">{{ item }}</li>
            </ul>
            <p class="mt-4 text-xs leading-6" style="color: var(--muted-foreground);">{{ selectedReport.report.caution }}</p>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { SavedAnalyzeReport } from '@/api/modules/upload'
import HomeConsultPanel from '@/modules/home/components/HomeConsultPanel.vue'
import { useHomeDashboard } from '@/modules/home/composables/useHomeDashboard'
import { getPlanSnapshot } from '@/modules/home/view-models'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import { useAuthStore } from '@/stores/auth'

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
  healthScore,
  consultQuestion,
  consultResponse,
  consultLoading,
  consultChips,
  loadDashboard,
  submitConsult,
  removeSavedReport,
} = useHomeDashboard()

const reportSectionExpanded = ref(false)
const selectedReport = ref<SavedAnalyzeReport | null>(null)
const consultSectionRef = ref<HTMLElement | null>(null)

// 健康评分：优先使用 healthScore API 返回的 overallScore，否则回退到 summary.healthScore
const displayScore = computed(() => {
  return healthScore.value?.overallScore ?? summary.value?.healthScore ?? healthInsight.value?.overallScore ?? 0
})
const scoreDeg = computed(() => {
  const score = displayScore.value
  return Math.round((score / 100) * 360)
})
const scoreDelta = computed(() => {
  // 暂无历史对比数据，返回 0 隐藏 badge
  return 0
})
const scoreDataWarning = computed(() => {
  const warning = healthScore.value?.dataWarnings?.[0]
  if (warning) return warning
  if (healthInsight.value?.dataQuality === 'none') return '暂无足够的健康监测数据，当前不生成个性化评分。'
  if (healthInsight.value?.dataQuality === 'partial') return '部分指标缺失，当前评分仅供数据完整性提示。'
  return ''
})

// 3 个关键指标卡片：心率 / 睡眠 / 步数
type KeyMetricCard = {
  key: string
  label: string
  value: string
  unit: string
  icon: string
  color: string
  route?: string
}
const keyMetricCards = computed<KeyMetricCard[]>(() => {
  const hrMetric = summary.value?.keyMetrics.find((m) => m.key === 'hr')
  const sleepCategory = healthInsight.value?.categories.find((c) => c.key === 'sleep')
  const stepsNow = summary.value?.stepsNow ?? 0

  return [
    {
      key: 'hr',
      label: '心率',
      value: hrMetric ? String(hrMetric.value) : '--',
      unit: 'bpm',
      icon: 'solar:heart-pulse-outline',
      color: 'var(--state-error)',
      route: '/monitor/hr',
    },
    {
      key: 'sleep',
      label: '睡眠',
      value: sleepCategory ? String(sleepCategory.score) : '--',
      unit: '分',
      icon: 'solar:moon-stars-outline',
      color: 'var(--chart-4)',
      route: '/monitor/sleep',
    },
    {
      key: 'steps',
      label: '步数',
      value: stepsNow.toLocaleString('en-US'),
      unit: '步',
      icon: 'solar:footprints-outline',
      color: 'var(--brand-500)',
    },
  ]
})

const planSnapshot = computed(() => getPlanSnapshot(rehabPlan.value))

const suggestions = computed(() => {
  const items = summary.value?.suggestions ?? []
  return items.length ? items : ['请先上传资料或连接设备。']
})

const statusChipStyle = computed(() => {
  const variant = summary.value?.statusBadgeVariant ?? 'default'
  const styles: Record<string, { background: string; color: string }> = {
    success: { background: 'var(--state-success-surface)', color: 'var(--state-success)' },
    warning: { background: '#fff4e6', color: '#ff9500' },
    danger: { background: 'var(--state-error-surface)', color: 'var(--state-error)' },
    info: { background: 'var(--brand-50)', color: 'var(--brand-500)' },
    default: { background: 'var(--secondary)', color: 'var(--muted-foreground)' },
  }
  return styles[variant] ?? styles.default
})

const riskLevelStyle = (riskLevel: string) => {
  if (riskLevel === '高风险') return { background: 'var(--state-error-surface)', color: 'var(--state-error)' }
  if (riskLevel === '中等风险') return { background: '#fff4e6', color: '#ff9500' }
  return { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
}

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

const scrollToConsult = () => {
  consultSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

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

const featureGrid = [
  { to: '/monitor/hr', label: '健康监测', icon: 'solar:graph-up-outline', color: 'var(--brand-500)', bg: 'var(--brand-50)' },
  { to: '/upload', label: '上传分析', icon: 'solar:upload-cloud-outline', color: 'var(--state-success)', bg: 'var(--state-success-surface)' },
  { to: '/medication', label: '用药管理', icon: 'solar:pills-3-outline', color: 'var(--state-error)', bg: 'var(--state-error-surface)' },
  { to: '/rehab', label: '康复训练', icon: 'solar:wheel-outline', color: 'var(--chart-3)', bg: 'color-mix(in srgb, var(--chart-3) 12%, var(--card))' },
  { to: '/diet', label: '饮食推荐', icon: 'solar:fork-knife-outline', color: 'var(--state-success)', bg: 'var(--state-success-surface)' },
  { to: '/knowledge', label: '健康知识', icon: 'solar:book-2-outline', color: 'var(--brand-500)', bg: 'var(--brand-50)' },
  { to: '/devices', label: '设备管理', icon: 'solar:smartwatch-outline', color: 'var(--chart-3)', bg: 'color-mix(in srgb, var(--chart-3) 12%, var(--card))' },
  { to: '/assistant', label: '智能助手', icon: 'solar:chat-round-line-outline', color: 'var(--brand-500)', bg: 'var(--brand-50)' },
]

onMounted(() => {
  if (!authStore.isAuthenticated) return
  void loadDashboard()
})
</script>
