/**
 * 健康数据 Store（示例）
 *
 * 这里适合承载：
 * - 设备数据（心率/睡眠/压力）缓存与聚合
 * - 上传资料列表、分析任务状态
 * - 统一的数据刷新策略（轮询/推送）
 */
import { defineStore } from 'pinia'

export type MetricPoint = { t: string; v: number }

export const useHealthStore = defineStore('health', {
  state: () => ({
    // 示例：7天心率
    hr7d: [] as MetricPoint[],
    sleep7d: [] as MetricPoint[],
    stress7d: [] as MetricPoint[],
  }),
  actions: {
    // TODO: 接入 API 获取
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
