import { http } from '@/api/http'
import { cloneMock, getMockDb, nextMockEntityId, nowIsoString, withMockFallback } from '@/dev/mockApi'

export type DeviceType = 'watch' | 'band' | 'ring' | 'other'

export type DeviceItem = {
  id: number
  name: string
  brand: string
  model: string
  type: DeviceType
  connected: boolean
  battery: number
  lastSyncAt: string
}

export type CreateDevicePayload = {
  name: string
  brand: string
  model: string
  type: DeviceType
}

function normalizeDeviceName(payload: CreateDevicePayload): string {
  return payload.name.trim() || `${payload.brand} ${payload.model}`.trim() || '未命名设备'
}

export async function getDevices(): Promise<DeviceItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<DeviceItem[]>('/devices')
      return data
    },
    () => {
      const db = getMockDb()
      return cloneMock(db.devices)
    },
  )
}

export async function createDevice(payload: CreateDevicePayload): Promise<DeviceItem> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<DeviceItem>('/devices', payload)
      return data
    },
    () => {
      const db = getMockDb()
      const item: DeviceItem = {
        id: nextMockEntityId('device'),
        name: normalizeDeviceName(payload),
        brand: payload.brand.trim() || 'Unknown',
        model: payload.model.trim() || 'Model',
        type: payload.type,
        connected: true,
        battery: 100,
        lastSyncAt: nowIsoString(),
      }
      db.devices.unshift(cloneMock(item))
      return item
    },
  )
}

export async function syncDevice(id: number): Promise<DeviceItem> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<DeviceItem>(`/devices/${id}/sync`)
      return data
    },
    () => {
      const db = getMockDb()
      const target = db.devices.find((item) => item.id === id)
      if (!target) {
        throw new Error('设备不存在')
      }
      target.connected = true
      target.lastSyncAt = nowIsoString()
      target.battery = Math.max(12, Math.min(100, target.battery - Math.floor(Math.random() * 4)))
      return cloneMock(target)
    },
  )
}

export async function deleteDevice(id: number): Promise<void> {
  await withMockFallback(
    async () => {
      await http.delete(`/devices/${id}`)
      return true
    },
    () => {
      const db = getMockDb()
      db.devices = db.devices.filter((item) => item.id !== id)
      return true
    },
  )
}
