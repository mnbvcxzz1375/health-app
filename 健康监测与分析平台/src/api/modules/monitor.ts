import { http } from '@/api/http'
import { cloneMock, getMockDb, withMockFallback } from '@/dev/mockApi'

export type MonitorMetric = 'hr' | 'sleep' | 'stress'
export type MonitorRange = 'minute' | 'hour' | 'day' | 'month'

export type MonitorLatest = {
  hr: number
  sleep: number
  deepSleep: number
  awake: number
  stress: number
  updatedAt: string
}

export type MonitorTrend = {
  labels: string[]
  values: number[]
  insight: string
  suggestion: string
}

export async function getMonitorLatest(): Promise<MonitorLatest> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<MonitorLatest>('/monitor/latest')
      return data
    },
    () => {
      const db = getMockDb()
      return cloneMock(db.monitorLatest)
    },
  )
}

export async function getMonitorTrend(metric: MonitorMetric, range: MonitorRange): Promise<MonitorTrend> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<MonitorTrend>('/monitor/trends', {
        params: { metric, range },
      })
      return data
    },
    () => {
      const db = getMockDb()
      return cloneMock(db.monitorTrends[metric][range])
    },
  )
}
