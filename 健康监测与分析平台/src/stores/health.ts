/**
 * 健康数据 Store（示例）
 *
 * 这里适合承载：
 * - 设备数据（心率/睡眠/压力）缓存与聚合
 * - 上传资料列表、分析任务状态
 * - 统一的数据刷新策略（轮询/推送）
 */
import { defineStore } from 'pinia'
import { getMonitorTrend, type MonitorRange } from '@/api/modules/monitor'

export type MetricPoint = { t: string; v: number }

export const useHealthStore = defineStore('health', {
  state: () => ({
    // 示例：7天心率
    hr7d: [] as MetricPoint[],
    sleep7d: [] as MetricPoint[],
    stress7d: [] as MetricPoint[],
  }),
  actions: {
    async refresh(range: MonitorRange = 'month') {
      const [hr, sleep, stress] = await Promise.all([
        getMonitorTrend('hr', range),
        getMonitorTrend('sleep', range),
        getMonitorTrend('stress', range),
      ])
      this.hr7d = hr.labels.map((t, index) => ({ t, v: Number(hr.values[index] ?? 0) }))
      this.sleep7d = sleep.labels.map((t, index) => ({ t, v: Number(sleep.values[index] ?? 0) }))
      this.stress7d = stress.labels.map((t, index) => ({ t, v: Number(stress.values[index] ?? 0) }))
    },

    /** Local-only fixture helper for screens that explicitly opt into mock data. */
    seedMock() {
      this.hr7d = [
        { t: '周一', v: 70 },
        { t: '周二', v: 74 },
        { t: '周三', v: 72 },
        { t: '周四', v: 76 },
        { t: '周五', v: 75 },
        { t: '周六', v: 73 },
        { t: '周日', v: 72 },
      ]
    },
  },
})
