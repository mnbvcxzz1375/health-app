import { http } from '@/api/http'
import { cloneMock, getMockDb, withMockFallback } from '@/dev/mockApi'

export type ProfileSummary = {
  devices: string
  uploads: string
  riskScore: string
}

export type ProfileSettings = {
  name: string
  email: string
  age: number
  gender: 'male' | 'female' | 'other'
  height: number
  weight: number
  focus: string
  goals: string[]
  dailySummary: boolean
  riskAlert: boolean
  rehabReminder: boolean
}

export async function getProfileSummary(): Promise<ProfileSummary> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<ProfileSummary>('/profile/summary')
      return data
    },
    () => {
      const db = getMockDb()
      const deviceCount = db.devices.length
      const uploadCount = 6 + Object.keys(db.analyzeTasks).length
      return {
        devices: `${deviceCount} 台（设备）`,
        uploads: `${uploadCount} 份`,
        riskScore: db.profileRiskScore,
      }
    },
  )
}

export async function getProfileSettings(): Promise<ProfileSettings> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<ProfileSettings>('/profile/settings')
      return data
    },
    () => {
      const db = getMockDb()
      return cloneMock(db.profileSettings)
    },
  )
}

export async function saveProfileSettings(payload: ProfileSettings): Promise<ProfileSettings> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<ProfileSettings>('/profile/settings', payload)
      return data
    },
    () => {
      const db = getMockDb()
      db.profileSettings = cloneMock(payload)
      return cloneMock(db.profileSettings)
    },
  )
}

export async function updateProfileAvatar(avatarUrl: string): Promise<void> {
  await withMockFallback(
    async () => {
      await http.post('/profile/avatar', { avatarUrl })
      return true
    },
    () => true,
  )
}
