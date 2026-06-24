import { http } from '@/api/http'

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
  const { data } = await http.get('/device/rook/status')
  return data as { configured: boolean }
}

export async function authorizeDataSource(dataSource: string): Promise<DataSourceAuth> {
  const { data } = await http.get(`/device/rook/authorize/${dataSource}`)
  return data as DataSourceAuth
}

export async function getAuthorizedSources(): Promise<AuthorizedSources> {
  const { data } = await http.get('/device/rook/sources')
  return data as AuthorizedSources
}

export async function getPhysicalHealth(date: string): Promise<PhysicalHealthSummary> {
  const { data } = await http.get(`/device/rook/physical/${date}`)
  return data as PhysicalHealthSummary
}

export async function getSleepHealth(date: string): Promise<SleepHealthSummary> {
  const { data } = await http.get(`/device/rook/sleep/${date}`)
  return data as SleepHealthSummary
}

export async function getActivityEvents(date: string): Promise<ActivityEvent[]> {
  const { data } = await http.get(`/device/rook/activities/${date}`)
  return data as ActivityEvent[]
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
  const { data } = await http.post('/device/rook/sync')
  return data as RookSyncResult
}
