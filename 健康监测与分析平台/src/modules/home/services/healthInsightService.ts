import type { AppleHealthSnapshot } from '@/modules/home/services/appleHealthBridge'

export type RiskLevel = 'low' | 'medium' | 'high' | 'unknown'

type ScoreInput = {
  heartRate?: number | null
  stress?: number | null
  sleep?: number | null
  steps?: number | null
  systolic?: number | null
  diastolic?: number | null
  oxygen?: number | null
  vo2Max?: number | null
  exerciseMinutes?: number | null
  standHours?: number | null
  activeEnergyKcal?: number | null
  flightsClimbed?: number | null
  hrvMillis?: number | null
  mindfulMinutes?: number | null
}

export type HealthCategoryInsight = {
  key: string
  label: string
  score: number
  risk: RiskLevel
  weight: number
  hint: string
  recommendations: string[]
  dataAvailable: boolean
}

export type HealthInsight = {
  overallScore: number
  overallRisk: RiskLevel
  summary: string
  categories: HealthCategoryInsight[]
  recommendations: string[]
  consultQuestion: string
  consultChips: string[]
  dataQuality: 'none' | 'partial' | 'complete'
  dataWarnings: string[]
}

const WEIGHTS = {
  sleep: 0.20,
  stress: 0.15,
  heartRate: 0.15,
  bloodPressure: 0.10,
  activity: 0.15,
  vo2Max: 0.10,
  standAndExercise: 0.10,
  recovery: 0.05,
} as const

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}

function riskFromScore(score: number): RiskLevel {
  if (score >= 80) return 'low'
  if (score >= 60) return 'medium'
  return 'high'
}

function riskLabel(risk: RiskLevel): string {
  if (risk === 'low') return '低风险'
  if (risk === 'medium') return '中风险'
  if (risk === 'high') return '高风险'
  return '数据不足'
}

function limitText(value: string, maxLength: number): string {
  if (value.length <= maxLength) return value
  return `${value.slice(0, maxLength - 1)}...`
}

function scoreRestingHeartRate(bpm?: number | null): number | null {
  if (bpm == null || !Number.isFinite(bpm)) return null
  const value = clamp(bpm, 40, 120)
  if (value <= 55) return 82 + clamp((55 - value) * 0.6, 0, 12)
  if (value <= 65) return 90 - (value - 55) * 1.2
  if (value <= 75) return 78 - (value - 65) * 1.1
  if (value <= 90) return 66 - (value - 75) * 1.6
  return clamp(42 - (value - 90) * 0.8, 0, 100)
}

function scoreStress(value?: number | null): number | null {
  if (value == null || !Number.isFinite(value)) return null
  const x = clamp(value, 0, 100)
  if (x <= 30) return 92 - x * 0.1
  if (x <= 55) return 86 - (x - 30) * 0.8
  if (x <= 75) return 66 - (x - 55) * 1.4
  return clamp(38 - (x - 75) * 0.9, 0, 100)
}

function scoreSleep(value?: number | null): number | null {
  if (value == null || !Number.isFinite(value)) return null
  return clamp(value, 0, 100)
}

function scoreSteps(value?: number | null): number | null {
  if (value == null || !Number.isFinite(value)) return null
  const x = clamp(value, 0, 20000)
  if (x >= 8000) return 94 + clamp((x - 8000) / 4000, 0, 1) * 6
  if (x >= 5000) return 80 + ((x - 5000) / 3000) * 14
  if (x >= 2000) return 60 + ((x - 2000) / 3000) * 20
  return clamp(20 + (x / 2000) * 40, 0, 100)
}

function scoreBloodPressure(systolic?: number | null, diastolic?: number | null): number | null {
  if (systolic == null || diastolic == null) return null
  if (!Number.isFinite(systolic) || !Number.isFinite(diastolic)) return null
  let score = 100
  if (systolic < 90) score -= (90 - systolic) * 1.6
  else if (systolic <= 120) score -= (systolic - 90) * 0.15
  else if (systolic <= 135) score -= 4.5 + (systolic - 120) * 1.2
  else score -= 22.5 + (systolic - 135) * 1.6

  if (diastolic < 55) score -= (55 - diastolic) * 1.2
  else if (diastolic <= 78) score -= (diastolic - 55) * 0.1
  else if (diastolic <= 88) score -= 2.3 + (diastolic - 78) * 1.0
  else score -= 12.3 + (diastolic - 88) * 1.4

  if (systolic > 145 || diastolic > 95) score -= 8
  if (systolic > 160 || diastolic > 100) score -= 12

  return clamp(Math.round(score), 0, 100)
}

/**
 * VO2Max 评分 — 基于 ACSM 年龄段分级
 * ml/kg/min，男性 20-29 岁参考区间 38-52
 */
function scoreVO2Max(vo2?: number | null): number | null {
  if (vo2 == null || !Number.isFinite(vo2)) return null
  const x = clamp(vo2, 15, 65)
  if (x >= 48) return 96
  if (x >= 42) return 85 + ((x - 42) / 6) * 11
  if (x >= 36) return 70 + ((x - 36) / 6) * 15
  if (x >= 30) return 50 + ((x - 30) / 6) * 20
  return clamp(20 + ((x - 15) / 15) * 30, 0, 100)
}

/**
 * 锻炼时间评分 — Apple Watch exerciseMinutes
 * 推荐每天 >= 30 分钟
 */
function scoreExerciseMinutes(minutes?: number | null): number | null {
  if (minutes == null || !Number.isFinite(minutes)) return null
  const x = clamp(minutes, 0, 120)
  if (x >= 30) return 90 + clamp((x - 30) / 60, 0, 1) * 10
  if (x >= 15) return 70 + ((x - 15) / 15) * 20
  if (x >= 5) return 45 + ((x - 5) / 10) * 25
  return clamp(10 + (x / 5) * 35, 0, 100)
}

/**
 * 站立小时数评分 — Apple Watch standHours
 * 满分 >= 10 小时
 */
function scoreStandHours(hours?: number | null): number | null {
  if (hours == null || !Number.isFinite(hours)) return null
  const x = clamp(hours, 0, 16)
  if (x >= 10) return 95
  if (x >= 8) return 80 + ((x - 8) / 2) * 15
  if (x >= 5) return 55 + ((x - 5) / 3) * 25
  return clamp(10 + (x / 5) * 45, 0, 100)
}

/**
 * HRV 评分 — SDNN(ms)
 * 健康成人参考区间 20-200ms，越高越好
 */
function scoreHRV(ms?: number | null): number | null {
  if (ms == null || !Number.isFinite(ms)) return null
  const x = clamp(ms, 5, 200)
  if (x >= 60) return 92
  if (x >= 40) return 75 + ((x - 40) / 20) * 17
  if (x >= 25) return 55 + ((x - 25) / 15) * 20
  return clamp(20 + ((x - 5) / 20) * 35, 0, 100)
}

function riskForBloodPressure(systolic?: number | null, diastolic?: number | null): RiskLevel {
  if (systolic == null || diastolic == null) return 'unknown'
  if (systolic > 160 || diastolic > 100) return 'high'
  if (systolic > 140 || diastolic > 90) return 'high'
  if (systolic > 135 || diastolic > 85) return 'medium'
  if (systolic < 85 || diastolic < 55) return 'medium'
  return 'low'
}

function riskForStress(score: number): RiskLevel {
  if (score <= 45) return 'high'
  if (score <= 65) return 'medium'
  return 'low'
}

function riskForSleep(score: number): RiskLevel {
  if (score >= 80) return 'low'
  if (score >= 60) return 'medium'
  return 'high'
}

function riskForActivity(score: number): RiskLevel {
  if (score >= 80) return 'low'
  if (score >= 60) return 'medium'
  return 'high'
}

function heartRateRecommendation(score: number, bpm?: number | null): string[] {
  if (score >= 85) return ['维持规律作息与适度有氧，避免长时间久坐。']
  if (score >= 70) return ['训练前先观察恢复状态，避免连续高强度安排。']
  if (bpm && bpm > 90) return ['静息心率偏高，建议优先进行呼吸放松与轻量恢复。']
  return ['心率异常风险较高，建议减少刺激性饮品并及时复查。']
}

function stressRecommendation(score: number): string[] {
  if (score >= 85) return ['压力状态可控，继续保持番茄钟节奏与短间隔休息。']
  if (score >= 65) return ['建议增加 5 分钟呼吸放松或轻量步行。']
  return ['当前压力负荷偏高，建议先安排放松训练再处理高强度任务。']
}

function sleepRecommendation(score: number): string[] {
  if (score >= 85) return ['保持固定入睡时间，减少睡前屏幕刺激。']
  if (score >= 65) return ['建议睡前进行 6-10 分钟放松训练，避免咖啡因过晚摄入。']
  return ['睡眠质量偏低，建议优先稳定作息并降低晚间负荷。']
}

function bpRecommendation(risk: RiskLevel): string[] {
  if (risk === 'unknown') return ['暂未读取到完整血压数据，先完成测量后再给出个性化建议。']
  if (risk === 'low') return ['血压处于相对稳定区间，建议继续监测并保持规律活动。']
  if (risk === 'medium') return ['建议减少高盐饮食并关注清晨与睡前血压变化。']
  return ['血压偏高风险明显，建议尽快线下复查并记录连续数据。']
}

function activityRecommendation(score: number): string[] {
  if (score >= 85) return ['活动量充足，可保持当前步行与恢复节奏。']
  if (score >= 65) return ['建议午后增加 10-15 分钟步行，降低久坐负担。']
  return ['活动量偏低，建议先完成 10-20 分钟轻量步行或室内拉伸。']
}

function vo2MaxRecommendation(score: number): string[] {
  if (score >= 85) return ['心肺能力优秀，可维持当前有氧训练强度。']
  if (score >= 65) return ['建议每周增加 2-3 次中等强度有氧运动提升摄氧量。']
  return ['最大摄氧量偏低，建议从快走或游泳等低冲击有氧开始逐步提升。']
}

function standExerciseRecommendation(score: number): string[] {
  if (score >= 85) return ['站立与锻炼时间充足，继续保持活跃习惯。']
  if (score >= 65) return ['建议每小时起身活动 1 分钟，日间穿插短时锻炼。']
  return ['站立与运动时间不足，建议设置 Apple Watch 站立提醒并增加日常步行。']
}

function recoveryRecommendation(score: number): string[] {
  if (score >= 85) return ['HRV 和正念指标良好，神经恢复状态稳定。']
  if (score >= 65) return ['建议增加 5-10 分钟正念呼吸或冥想练习。']
  return ['恢复指标偏低，建议优先保证睡眠并减少夜间刺激。']
}

function topCategory(categories: HealthCategoryInsight[]): HealthCategoryInsight | null {
  const available = categories.filter((item) => item.dataAvailable)
  if (!available.length) return null
  return available.reduce((prev, current) => (current.score < prev.score ? current : prev))
}

function riskFromOverall(score: number): RiskLevel {
  if (score >= 82) return 'low'
  if (score >= 62) return 'medium'
  return 'high'
}

export function buildHealthInsight(input: ScoreInput): HealthInsight {
  const hrScore = scoreRestingHeartRate(input.heartRate)
  const stressRaw = scoreStress(input.stress)
  const sleepRaw = scoreSleep(input.sleep)
  const activityRaw = scoreSteps(input.steps)
  const bpRaw = scoreBloodPressure(input.systolic, input.diastolic)
  const vo2Raw = scoreVO2Max(input.vo2Max)
  const exerciseRaw = scoreExerciseMinutes(input.exerciseMinutes)
  const standRaw = scoreStandHours(input.standHours)
  const hrvRaw = scoreHRV(input.hrvMillis)

  const observedCount = [hrScore, stressRaw, sleepRaw, activityRaw, bpRaw, vo2Raw, exerciseRaw, standRaw, hrvRaw]
    .filter((value) => value != null).length
  const dataQuality: HealthInsight['dataQuality'] = observedCount === 0 ? 'none' : observedCount >= 7 ? 'complete' : 'partial'
  const stressScore = stressRaw ?? 0
  const sleepScore = sleepRaw ?? 0
  const activityScore = activityRaw ?? 0
  const bpScore = bpRaw ?? 0
  const heartRateScore = hrScore ?? 0
  const vo2Score = vo2Raw ?? 0
  const standExerciseScore = (exerciseRaw != null && standRaw != null)
    ? Math.round(exerciseRaw * 0.6 + standRaw * 0.4)
    : (exerciseRaw ?? standRaw ?? 0)
  const recoveryScore = (hrvRaw != null && stressRaw != null)
    ? Math.round(hrvRaw * 0.6 + (100 - input.stress!) * 0.4)
    : (hrvRaw ?? 0)

  const categories: HealthCategoryInsight[] = [
    {
      key: 'sleep',
      label: '睡眠质量',
      score: sleepScore,
      risk: sleepRaw != null ? riskForSleep(sleepScore) : 'unknown',
      weight: WEIGHTS.sleep,
      hint: sleepScore >= 80 ? '整体睡眠质量稳定' : '睡眠恢复仍需关注',
      recommendations: sleepRecommendation(sleepScore),
      dataAvailable: sleepRaw != null,
    },
    {
      key: 'stress',
      label: '压力负荷',
      score: stressScore,
      risk: stressRaw != null ? riskForStress(stressScore) : 'unknown',
      weight: WEIGHTS.stress,
      hint: stressScore >= 70 ? '压力状态可控' : '压力波动偏高',
      recommendations: stressRecommendation(stressScore),
      dataAvailable: stressRaw != null,
    },
    {
      key: 'heartRate',
      label: '静息心率',
      score: heartRateScore,
      risk: hrScore != null ? riskFromScore(heartRateScore) : 'unknown',
      weight: WEIGHTS.heartRate,
      hint: input.heartRate ? `${input.heartRate} bpm` : '暂无最新心率',
      recommendations: heartRateRecommendation(heartRateScore, input.heartRate),
      dataAvailable: hrScore != null,
    },
    {
      key: 'bloodPressure',
      label: '血压状态',
      score: bpScore,
      risk: riskForBloodPressure(input.systolic, input.diastolic),
      weight: WEIGHTS.bloodPressure,
      hint: input.systolic && input.diastolic ? `${input.systolic}/${input.diastolic} mmHg` : '未读取到血压数据',
      recommendations: bpRecommendation(riskForBloodPressure(input.systolic, input.diastolic)),
      dataAvailable: bpRaw != null,
    },
    {
      key: 'activity',
      label: '步行活动',
      score: activityScore,
      risk: activityRaw != null ? riskForActivity(activityScore) : 'unknown',
      weight: WEIGHTS.activity,
      hint: input.steps ? `${Math.round(input.steps)} 步` : '今日活动量待补充',
      recommendations: activityRecommendation(activityScore),
      dataAvailable: activityRaw != null,
    },
    {
      key: 'vo2Max',
      label: '最大摄氧量',
      score: vo2Score,
      risk: vo2Raw != null ? riskFromScore(vo2Score) : 'unknown',
      weight: WEIGHTS.vo2Max,
      hint: input.vo2Max ? `${input.vo2Max} ml/kg/min` : '暂无 VO2Max 数据',
      recommendations: vo2MaxRecommendation(vo2Score),
      dataAvailable: vo2Raw != null,
    },
    {
      key: 'standAndExercise',
      label: '站立与锻炼',
      score: standExerciseScore,
      risk: exerciseRaw != null || standRaw != null ? riskForActivity(standExerciseScore) : 'unknown',
      weight: WEIGHTS.standAndExercise,
      hint: [
        input.standHours ? `站立 ${input.standHours}h` : '',
        input.exerciseMinutes ? `锻炼 ${input.exerciseMinutes}min` : '',
      ].filter(Boolean).join('，') || '暂无站立/锻炼数据',
      recommendations: standExerciseRecommendation(standExerciseScore),
      dataAvailable: exerciseRaw != null || standRaw != null,
    },
    {
      key: 'recovery',
      label: '恢复状态',
      score: recoveryScore,
      risk: hrvRaw != null || stressRaw != null ? riskFromScore(recoveryScore) : 'unknown',
      weight: WEIGHTS.recovery,
      hint: input.hrvMillis ? `HRV ${input.hrvMillis}ms` : '暂无 HRV 数据',
      recommendations: recoveryRecommendation(recoveryScore),
      dataAvailable: hrvRaw != null || stressRaw != null,
    },
  ]

  const availableCategories = categories.filter((item) => item.dataAvailable)
  const availableWeight = availableCategories.reduce((sum, item) => sum + item.weight, 0)
  const overallScore = availableWeight > 0
    ? clamp(Math.round(availableCategories.reduce((sum, item) => sum + item.score * item.weight, 0) / availableWeight), 0, 100)
    : 0

  const riskOrder: Record<RiskLevel, number> = { unknown: -1, low: 0, medium: 1, high: 2 }
  const overallRisk = availableCategories.length === 0
    ? 'unknown'
    : availableCategories.reduce<RiskLevel>((risk, item) => {
      return riskOrder[item.risk] > riskOrder[risk] ? item.risk : risk
    }, riskFromOverall(overallScore))

  const weak = topCategory(categories)
  const summary = dataQuality === 'none'
    ? '暂无足够的健康监测数据，暂不生成综合评分。'
    : weak
    ? `综合健康评分 ${overallScore}，当前主要风险点在${weak.label}。${weak.hint}。`
    : `综合健康评分 ${overallScore}，整体状态相对稳定。`

  const recommendations = categories
    .filter((item) => item.risk !== 'low')
    .flatMap((item) => item.recommendations)
    .slice(0, 5)

  const fallbackRecommendations = categories.flatMap((item) => item.recommendations).slice(0, 4)
  const finalRecommendations = dataQuality === 'none'
    ? ['先同步 Apple Health 或智能穿戴数据，再生成个性化建议。']
    : recommendations.length ? recommendations : fallbackRecommendations

  const consultQuestion = dataQuality === 'none'
    ? '我还没有可用的健康监测数据，请告诉我需要先同步哪些数据。'
    : weak
    ? `基于我当前 ${overallScore} 分的健康状态，${weak.label}评分 ${weak.score}，请给出今晚到明天的改善建议。`
    : `基于我当前 ${overallScore} 分的健康状态，请给出今晚到明天的改善建议。`

  const consultChips = [
    weak ? `我${weak.label}偏低，该怎么调整？` : '帮我看看今天健康状态',
    '请根据我的数据给出今晚恢复方案',
    '请生成明天的健康行动计划',
  ].map((text) => limitText(text, 24))

  return {
    overallScore,
    overallRisk,
    summary: limitText(summary, 80),
    categories,
    recommendations: finalRecommendations,
    consultQuestion,
    consultChips,
    dataQuality,
    dataWarnings: dataQuality === 'complete'
      ? []
      : ['部分健康指标缺失，当前评分仅供数据完整性提示，不应视为医学结论。'],
  }
}

/**
 * 从 Apple Health 快照构建评分输入
 */
export function buildScoreInputFromSnapshot(snapshot: AppleHealthSnapshot): ScoreInput {
  return {
    heartRate: snapshot.heartRate?.avgBpm ?? snapshot.restingHeartRate?.bpm ?? null,
    stress: null,
    sleep: snapshot.sleepSession?.totalMinutes
      ? Math.round(clamp(snapshot.sleepSession.totalMinutes / 4.8, 0, 100))
      : null,
    steps: snapshot.stepsToday ?? null,
    systolic: snapshot.bloodPressure?.systolicMmHg ?? null,
    diastolic: snapshot.bloodPressure?.diastolicMmHg ?? null,
    oxygen: snapshot.oxygenSaturation?.percentage ?? null,
    vo2Max: snapshot.vo2Max ?? null,
    exerciseMinutes: snapshot.exerciseMinutesToday ?? null,
    standHours: snapshot.standHoursToday ?? null,
    activeEnergyKcal: snapshot.activeEnergyKcal ?? null,
    flightsClimbed: snapshot.flightsClimbedToday ?? null,
    hrvMillis: snapshot.heartRateVariabilityMillis ?? null,
    mindfulMinutes: snapshot.mindfulMinutesToday ?? null,
  }
}

export function buildInsightFromMonitor(latest: {
  hr: number
  sleep: number
  stress: number
  stepsNow?: number
  vo2Max?: number
  exerciseMinutes?: number
  standHours?: number
  activeEnergyKcal?: number
  flightsClimbed?: number
  hrvMillis?: number
}): HealthInsight {
  return buildHealthInsight({
    heartRate: latest.hr > 0 ? latest.hr : null,
    stress: latest.stress > 0 ? latest.stress : null,
    sleep: latest.sleep > 0 ? latest.sleep : null,
    steps: latest.stepsNow && latest.stepsNow > 0 ? latest.stepsNow : null,
    vo2Max: latest.vo2Max && latest.vo2Max > 0 ? latest.vo2Max : null,
    exerciseMinutes: latest.exerciseMinutes && latest.exerciseMinutes > 0 ? latest.exerciseMinutes : null,
    standHours: latest.standHours && latest.standHours > 0 ? latest.standHours : null,
    activeEnergyKcal: latest.activeEnergyKcal && latest.activeEnergyKcal > 0 ? latest.activeEnergyKcal : null,
    flightsClimbed: latest.flightsClimbed && latest.flightsClimbed > 0 ? latest.flightsClimbed : null,
    hrvMillis: latest.hrvMillis && latest.hrvMillis > 0 ? latest.hrvMillis : null,
  })
}

export function quickConsultFromInsight(insight: HealthInsight, scene: string): string {
  const weak = topCategory(insight.categories)
  const base = weak
    ? `${scene} 当前综合分${insight.overallScore}，${weak.label} ${weak.score}`
    : `${scene} 当前综合分${insight.overallScore}`
  return limitText(`${base}。请给出最紧急的两条改善建议。`, 64)
}

export function riskBadgeVariant(risk: RiskLevel): 'success' | 'warning' | 'danger' {
  if (risk === 'low') return 'success'
  if (risk === 'medium' || risk === 'unknown') return 'warning'
  return 'danger'
}

export function riskBadgeText(risk: RiskLevel): string {
  if (risk === 'low') return '总体稳定'
  if (risk === 'medium') return '需关注'
  if (risk === 'unknown') return '数据不足'
  return '高风险'
}
