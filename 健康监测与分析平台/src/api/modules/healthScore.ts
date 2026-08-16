import { http } from '@/api/http'
import { withMockFallback } from '@/dev/mockApi'

export type CategoryScore = {
  key: string
  label: string
  score: number
  currentValue: number
  baselineValue: number
  offset: number
  riskNote: string
  attentionType: string
  weight: number
  algorithmNote: string
  dataAvailable: boolean
}

export type TopRisk = {
  attentionType: string
  label: string
  description: string
  severity: number
}

export type HealthScoreResponse = {
  overallScore: number
  overallRisk: string
  categoryScores: CategoryScore[]
  topRisks: TopRisk[]
  recommendedActions: string[]
  summary: string
  dataQuality: 'none' | 'insufficient' | 'partial' | 'complete' | 'mock'
  dataWarnings: string[]
}

export async function getHealthScore(): Promise<HealthScoreResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<HealthScoreResponse>('/health/score')
      return data
    },
    () => ({
      overallScore: 82,
      overallRisk: 'low',
      categoryScores: [
        { key: 'hrv', label: '心率变异性', score: 85, currentValue: 48, baselineValue: 45, offset: 3, riskNote: '良好', attentionType: 'hrv', weight: 0.2, algorithmNote: 'HRV 高于基线', dataAvailable: true },
        { key: 'sleep', label: '睡眠质量', score: 78, currentValue: 7.2, baselineValue: 7.0, offset: 0.2, riskNote: '正常', attentionType: 'sleep', weight: 0.25, algorithmNote: '睡眠时长稳定', dataAvailable: true },
        { key: 'activity', label: '活动量', score: 80, currentValue: 8500, baselineValue: 8000, offset: 500, riskNote: '达标', attentionType: 'activity', weight: 0.2, algorithmNote: '步数超过目标', dataAvailable: true },
        { key: 'stress', label: '压力管理', score: 88, currentValue: 32, baselineValue: 35, offset: -3, riskNote: '低压力', attentionType: 'stress', weight: 0.15, algorithmNote: '压力水平低于基线', dataAvailable: true },
        { key: 'medication', label: '用药依从性', score: 90, currentValue: 95, baselineValue: 90, offset: 5, riskNote: '优秀', attentionType: 'medication', weight: 0.2, algorithmNote: '服药依从性高', dataAvailable: true },
      ],
      topRisks: [
        { attentionType: 'sleep', label: '睡眠深度不足', description: '深睡占比偏低，建议睡前减少屏幕使用', severity: 2 },
      ],
      recommendedActions: ['保持每日 30 分钟有氧运动', '睡前 1 小时远离电子设备', '按时服药'],
      summary: '整体健康状况良好，各项指标接近或优于基线水平。',
      dataQuality: 'mock',
      dataWarnings: ['开发模式演示数据，不代表真实健康监测结果。'],
    }),
    true,
  )
}
