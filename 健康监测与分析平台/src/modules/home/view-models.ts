import type { HomeMetric, HomeSummary } from '@/api/modules/home'
import type { RehabPlan } from '@/api/modules/rehab'
import type { PageStatCard, RiskTone } from '@/shared/types/ui'

export type HomeActionItem = {
  key: string
  title: string
  route: string
  icon: string
}

const metricLabelMap: Record<HomeMetric['key'], string> = {
  hr: '静息心率',
  stress: '压力指数',
  hydration: '补水情况',
}

const metricIconMap: Record<HomeMetric['key'], string> = {
  hr: 'solar:heart-pulse-outline',
  stress: 'solar:shield-warning-outline',
  hydration: 'solar:cup-hot-outline',
}

export function toRiskTone(value: string | undefined): RiskTone {
  if (value === 'success') return 'success'
  if (value === 'warning') return 'warning'
  if (value === 'danger') return 'danger'
  if (value === 'info') return 'info'
  return 'default'
}

export function formatMetricValue(metric: HomeMetric): string {
  if (metric.key === 'hydration') {
    return `${(metric.value / 1000).toFixed(1)} 升`
  }
  return `${metric.value}`
}

export function toHomeMetricCards(summary: HomeSummary | null): PageStatCard[] {
  if (!summary) return []

  return summary.keyMetrics.map((metric) => ({
    key: metric.key,
    label: metricLabelMap[metric.key],
    value: formatMetricValue(metric),
    hint: metric.hint,
    icon: metricIconMap[metric.key],
    tone: toRiskTone(metric.badgeVariant),
  }))
}

export function toPlanActions(_summary: HomeSummary | null, _rehabPlan: RehabPlan | null): HomeActionItem[] {
  return [
    {
      key: 'upload',
      title: '上传资料',
      route: '/upload',
      icon: 'solar:upload-outline',
    },
    {
      key: 'rehab',
      title: '康复计划',
      route: '/rehab',
      icon: 'solar:wheel-outline',
    },
    {
      key: 'medication',
      title: '用药提醒',
      route: '/medication',
      icon: 'solar:pills-3-outline',
    },
  ]
}

export function getPlanSnapshot(rehabPlan: RehabPlan | null) {
  if (!rehabPlan) {
    return {
      focus: '等待计划生成',
      frequency: '待补充',
      duration: '待补充',
      intensity: '待补充',
      exerciseCount: 0,
    }
  }

  return {
    ...rehabPlan.planSummary,
    exerciseCount: rehabPlan.exercises.length,
  }
}
