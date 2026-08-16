import { describe, expect, it } from 'vitest'
import { buildInsightFromMonitor, buildHealthInsight } from './healthInsightService'

describe('healthInsightService data boundary', () => {
  it('does not create a score from an empty monitor response', () => {
    const insight = buildInsightFromMonitor({ hr: 0, sleep: 0, stress: 0 })

    expect(insight.dataQuality).toBe('none')
    expect(insight.overallScore).toBe(0)
    expect(insight.overallRisk).toBe('unknown')
    expect(insight.dataWarnings).not.toHaveLength(0)
  })

  it('normalizes the weighted score over available metrics only', () => {
    const insight = buildHealthInsight({ heartRate: 70, sleep: null, stress: null })

    expect(insight.dataQuality).toBe('partial')
    expect(insight.categories.filter((item) => item.dataAvailable)).toHaveLength(1)
    expect(insight.overallScore).toBeGreaterThan(0)
    expect(insight.dataWarnings).not.toHaveLength(0)
  })
})
