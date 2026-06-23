import { http } from '@/api/http'
import { cloneMock, getMockDb, withMockFallback } from '@/dev/mockApi'

export type HomeMetricKey = 'hr' | 'stress' | 'hydration'

export type HomeMetric = {
  key: HomeMetricKey
  value: number
  badge: string
  badgeVariant: 'success' | 'warning' | 'danger' | 'info' | 'default'
  hint: string
}

export type HomeSummary = {
  userName: string
  healthScore: number
  statusBadge: string
  statusBadgeVariant: 'success' | 'warning' | 'danger' | 'info' | 'default'
  statusSummary: string
  stepsTarget: number
  stepsNow: number
  keyMetrics: HomeMetric[]
  suggestions: string[]
}

const homeTextMap: Record<string, string> = {
  attention: '需关注',
  normal: '正常',
  elevated: '偏高',
  low: '不足',
  'Hydrate more and keep training intensity moderate today.': '建议今天优先补水，并保持中低强度训练。',
  'Resting heart rate is up by 6 versus yesterday.': '静息心率较昨日偏高，建议关注恢复状态。',
  'Try a 5 minute relaxation break.': '建议安排 5 分钟放松休息。',
  'Water intake is still below target in the last 24 hours.': '近 24 小时饮水量仍未达标。',
  'Drink water in three smaller rounds today.': '今天分 3 次小口补水更合适。',
  'Pick 20 to 30 minutes of low intensity cardio.': '今天可选择 20 到 30 分钟低强度有氧。',
  'Avoid screens for 30 minutes before sleep tonight.': '今晚睡前 30 分钟尽量避免屏幕刺激。',
}

function translateHomeText(value: string): string {
  const text = String(value ?? '').trim()
  return homeTextMap[text] ?? text
}

function normalizeHomeSummary(summary: HomeSummary): HomeSummary {
  return {
    ...summary,
    statusBadge: translateHomeText(summary.statusBadge),
    statusSummary: translateHomeText(summary.statusSummary),
    keyMetrics: summary.keyMetrics.map((metric) => ({
      ...metric,
      badge: translateHomeText(metric.badge),
      hint: translateHomeText(metric.hint),
    })),
    suggestions: summary.suggestions.map(translateHomeText),
  }
}

export async function getHomeSummary(): Promise<HomeSummary> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<HomeSummary>('/home/summary')
      return normalizeHomeSummary(data)
    },
    () => {
      const db = getMockDb()
      return normalizeHomeSummary(cloneMock(db.homeSummary))
    },
  )
}
