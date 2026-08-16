import { http } from '@/api/http'
import { withMockFallback } from '@/dev/mockApi'

export type DataSourceAuth = {
  dataSource: string
  authorized: boolean
  authorizationUrl: string
}

export type AuthorizedSources = {
  userId: string
  sources: Record<string, boolean>
}

export type PhysicalHealthSummary = {
  activity: { activeSeconds: number; restSeconds: number; highIntensitySeconds: number }
  calories: { expenditureKcal: number }
  distance: { steps: number; floorsClimbed: number }
  heartRate: { avgBpm: number; restingBpm: number; hrvAvgRmssd: number; hrvAvgSdnn: number }
  oxygenation: { avgSpo2: number; vo2Max: number }
  stress: { avgLevel: number; highStressDurationSeconds: number }
}

export type SleepHealthSummary = {
  duration: {
    totalSleepSeconds: number; deepSleepSeconds: number; remSleepSeconds: number
    lightSleepSeconds: number; timeToFallAsleepSeconds: number; timeAwakeDuringSleepSeconds: number
  }
  scores: { qualityRating: number; efficiency: number; continuityScore: number }
  heartRate: { avgBpm: number; hrvAvgRmssd: number }
  breathing: { breathsAvgPerMin: number; snoringEventsCount: number; spo2Avg: number }
}

export type ActivityEvent = {
  activityType: string; durationSeconds: number
  heartRate: { avgBpm: number; maxBpm: number }
  movement: { steps: number; avgPace: number }
}

export async function getRookStatus(): Promise<{ configured: boolean }> {
  return withMockFallback(
    async () => {
      const { data } = await http.get('/device/rook/status')
      return data as { configured: boolean }
    },
    () => ({ configured: false }),
    true,
  )
}

export async function authorizeDataSource(dataSource: string): Promise<DataSourceAuth> {
  return withMockFallback(
    async () => {
      const { data } = await http.get(`/device/rook/authorize/${dataSource}`)
      return data as DataSourceAuth
    },
    () => ({ dataSource, authorized: false, authorizationUrl: '' }),
    true,
  )
}

export async function getAuthorizedSources(): Promise<AuthorizedSources> {
  return withMockFallback(
    async () => {
      const { data } = await http.get('/device/rook/sources')
      return data as AuthorizedSources
    },
    () => ({ userId: '', sources: {} }),
    true,
  )
}

export async function getPhysicalHealth(date: string): Promise<PhysicalHealthSummary> {
  return withMockFallback(
    async () => {
      const { data } = await http.get(`/device/rook/physical/${date}`)
      return data as PhysicalHealthSummary
    },
    () => ({
      activity: { activeSeconds: 3600, restSeconds: 80000, highIntensitySeconds: 600 },
      calories: { expenditureKcal: 2100 },
      distance: { steps: 8500, floorsClimbed: 8 },
      heartRate: { avgBpm: 72, restingBpm: 64, hrvAvgRmssd: 45, hrvAvgSdnn: 52 },
      oxygenation: { avgSpo2: 97, vo2Max: 42 },
      stress: { avgLevel: 32, highStressDurationSeconds: 600 },
    }),
    true,
  )
}

export async function getSleepHealth(date: string): Promise<SleepHealthSummary> {
  return withMockFallback(
    async () => {
      const { data } = await http.get(`/device/rook/sleep/${date}`)
      return data as SleepHealthSummary
    },
    () => ({
      duration: {
        totalSleepSeconds: 25920, deepSleepSeconds: 5400, remSleepSeconds: 6480,
        lightSleepSeconds: 12960, timeToFallAsleepSeconds: 600, timeAwakeDuringSleepSeconds: 480,
      },
      scores: { qualityRating: 82, efficiency: 88, continuityScore: 85 },
      heartRate: { avgBpm: 58, hrvAvgRmssd: 50 },
      breathing: { breathsAvgPerMin: 14, snoringEventsCount: 0, spo2Avg: 96 },
    }),
    true,
  )
}

export async function getActivityEvents(date: string): Promise<ActivityEvent[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get(`/device/rook/activities/${date}`)
      return data as ActivityEvent[]
    },
    () => [],
    true,
  )
}

export type RookSyncResult = {
  synced: boolean
  hr: number
  sleepScore: number
  stressScore: number
  hrv: number
  steps: number
  vo2Max: number
  deepSleepHours: number
}

export async function syncRookData(): Promise<RookSyncResult> {
  return withMockFallback(
    async () => {
      const { data } = await http.post('/device/rook/sync')
      return data as RookSyncResult
    },
    () => ({ synced: true, hr: 72, sleepScore: 82, stressScore: 32, hrv: 48, steps: 8500, vo2Max: 42, deepSleepHours: 1.5 }),
    true,
  )
}
