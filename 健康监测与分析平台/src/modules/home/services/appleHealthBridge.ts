/**
 * Apple HealthKit 桥接层
 *
 * 覆盖 Apple Watch + iPhone 可采集的全部关键健康数据维度：
 *   心率、静息心率、HRV、血压、血氧、睡眠、步数、
 *   站立时间、锻炼时间、活动能量、VO2Max、飞行楼层、
 *   体温、呼吸频率、步行心率均值、正念分钟数
 *
 * 参考文档：
 * - HKObjectType: https://developer.apple.com/documentation/healthkit/hkobjecttype
 * - HKQuantityType: https://developer.apple.com/documentation/healthkit/hkquantitytype
 */

import { env } from '@/config/env'

export type AppleHealthAvailability = {
  available: boolean
  source: 'native' | 'mock'
  watchConnected?: boolean
}

export type SleepStage = {
  stage: 'core' | 'deep' | 'rem' | 'awake'
  startAt: string
  endAt: string
}

export type WorkoutRecord = {
  type: string
  startAt: string
  endAt: string
  durationMinutes: number
  activeKcal: number | null
  distanceMeters: number | null
  avgHeartRate: number | null
}

export type AppleHealthSnapshot = {
  source: 'apple_health'
  heartRate?: { avgBpm: number | null; minBpm: number | null; maxBpm: number | null; measuredAt: string } | null
  restingHeartRate?: { bpm: number | null; measuredAt: string } | null
  heartRateVariabilityMillis?: number | null
  walkingHeartRateAvg?: number | null
  bloodPressure?: { systolicMmHg: number | null; diastolicMmHg: number | null; measuredAt: string } | null
  oxygenSaturation?: { percentage: number | null; measuredAt: string } | null
  sleepSession?: {
    startAt: string
    endAt: string
    totalMinutes: number | null
    deepSleepMinutes: number | null
    remSleepMinutes: number | null
    awakeMinutes: number | null
    stages: SleepStage[]
  } | null
  stepsToday?: number | null
  standMinutesToday?: number | null
  standHoursToday?: number | null
  exerciseMinutesToday?: number | null
  activeEnergyKcal?: number | null
  restingEnergyKcal?: number | null
  vo2Max?: number | null
  flightsClimbedToday?: number | null
  distanceWalkingRunningMeters?: number | null
  bodyTemperature?: { celsius: number | null; measuredAt: string } | null
  respiratoryRate?: { ratePerMinute: number | null; measuredAt: string } | null
  mindfulMinutesToday?: number | null
  workouts?: WorkoutRecord[] | null
  raw?: unknown
}

export interface AppleHealthBridge {
  isAvailable: () => Promise<AppleHealthAvailability>
  requestRead: () => Promise<{ granted: boolean }>
  readSnapshot: () => Promise<AppleHealthSnapshot>
}

declare global {
  interface Window {
    AppleHealthBridge?: AppleHealthBridge
  }
}

function getNativeBridge(): AppleHealthBridge | null {
  if (typeof window === 'undefined') return null
  return window.AppleHealthBridge ?? null
}

export async function ensureAppleHealthAvailable(): Promise<AppleHealthBridge> {
  const bridge = getNativeBridge()
  if (bridge) {
    const availability = await bridge.isAvailable()
    if (availability.available) return bridge
  }
  throw new Error('当前环境未接入 Apple Health 原生桥')
}

export async function readAppleHealthSnapshot(): Promise<AppleHealthSnapshot> {
  const bridge = getNativeBridge()
  let snapshot: AppleHealthSnapshot
  if (bridge) {
    const availability = await bridge.isAvailable()
    if (availability.available) {
      await bridge.requestRead()
      snapshot = await bridge.readSnapshot()
    } else if (env.useDevMock) {
      snapshot = mockAppleHealthSnapshot()
    } else {
      throw new Error('当前环境未接入 Apple Health 原生桥')
    }
  } else if (env.useDevMock) {
    snapshot = mockAppleHealthSnapshot()
  } else {
    throw new Error('当前环境未接入 Apple Health 原生桥')
  }
  // 异步推送至后端入库（失败静默，不阻塞原流程）
  void pushAppleHealthSnapshotToBackend(snapshot).catch((err) => {
    console.warn('[appleHealthBridge] push snapshot to backend failed:', err)
  })
  return snapshot
}

/**
 * 将 Apple Health 快照推送到后端设备聚合平台，写入 monitor_records 扩展列。
 * 失败时静默，不影响前端原有流程。
 */
async function pushAppleHealthSnapshotToBackend(snapshot: AppleHealthSnapshot): Promise<void> {
  // 动态 import 避免循环依赖（deviceAggregation.ts 间接依赖 appleHealthBridge）
  const { pushAppleHealthSnapshot } = await import('@/api/modules/deviceAggregation')
  await pushAppleHealthSnapshot(snapshot)
}

function mockAppleHealthSnapshot(): AppleHealthSnapshot {
  const now = new Date()
  const iso = now.toISOString()
  const h = (hours: number) => new Date(now.getTime() - hours * 3600_000).toISOString()
  return {
    source: 'apple_health',
    heartRate: { avgBpm: 72, minBpm: 58, maxBpm: 96, measuredAt: iso },
    restingHeartRate: { bpm: 64, measuredAt: iso },
    heartRateVariabilityMillis: 42,
    walkingHeartRateAvg: 98,
    bloodPressure: { systolicMmHg: 118, diastolicMmHg: 76, measuredAt: iso },
    oxygenSaturation: { percentage: 98, measuredAt: iso },
    sleepSession: {
      startAt: h(8),
      endAt: iso,
      totalMinutes: 468,
      deepSleepMinutes: 108,
      remSleepMinutes: 102,
      awakeMinutes: 18,
      stages: [
        { stage: 'core', startAt: h(8), endAt: h(5) },
        { stage: 'deep', startAt: h(5), endAt: h(3) },
        { stage: 'rem', startAt: h(3), endAt: h(1) },
      ],
    },
    stepsToday: 5320,
    standMinutesToday: 48,
    standHoursToday: 8,
    exerciseMinutesToday: 26,
    activeEnergyKcal: 320,
    restingEnergyKcal: 1580,
    vo2Max: 38.5,
    flightsClimbedToday: 6,
    distanceWalkingRunningMeters: 3800,
    bodyTemperature: { celsius: 36.5, measuredAt: iso },
    respiratoryRate: { ratePerMinute: 16, measuredAt: iso },
    mindfulMinutesToday: 10,
    workouts: [
      {
        type: '步行',
        startAt: h(3),
        endAt: h(2.5),
        durationMinutes: 30,
        activeKcal: 150,
        distanceMeters: 2400,
        avgHeartRate: 102,
      },
    ],
  }
}
