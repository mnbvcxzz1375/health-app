import { computed, ref } from 'vue'
import { env } from '@/config/env'
import { createMockDb, type MockDb } from './mockData'

type EntityIdKind = 'medication' | 'device'
type TaskIdKind = 'analyze' | 'rehabVideo' | 'consult'

const mockDb = createMockDb()
const fallbackActivated = ref(false)

const entityIdSeed: Record<EntityIdKind, number> = {
  medication: Math.max(0, ...mockDb.medications.map((item) => item.id)),
  device: Math.max(0, ...mockDb.devices.map((item) => item.id)),
}

const taskIdSeed: Record<TaskIdKind, number> = {
  analyze: 0,
  rehabVideo: 0,
  consult: 0,
}

export const isUsingMockData = computed(() => env.useDevMock || fallbackActivated.value)

export function getMockDb(): MockDb {
  return mockDb
}

export function cloneMock<T>(value: T): T {
  if (typeof structuredClone === 'function') {
    return structuredClone(value)
  }
  return JSON.parse(JSON.stringify(value)) as T
}

export function nextMockEntityId(kind: EntityIdKind): number {
  entityIdSeed[kind] += 1
  return entityIdSeed[kind]
}

export function nextMockTaskId(kind: TaskIdKind): string {
  taskIdSeed[kind] += 1
  const suffix = String(taskIdSeed[kind]).padStart(4, '0')
  if (kind === 'analyze') return `task_mock_${suffix}`
  if (kind === 'rehabVideo') return `rehab_video_${suffix}`
  return `consult_${suffix}`
}

export function nowIsoString(): string {
  return new Date().toISOString()
}

export async function mockDelay(ms = 180): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
}

export async function withMockFallback<T>(
  remoteExecutor: () => Promise<T>,
  mockExecutor: () => T | Promise<T>,
  _fallbackOnError = false,
): Promise<T> {
  // Mock data is an explicit development mode only. The legacy third argument
  // is retained for call-site compatibility, but production failures must be
  // surfaced instead of being silently replaced with plausible-looking data.
  if (env.useDevMock) {
    fallbackActivated.value = true
    await mockDelay()
    return mockExecutor()
  }
  return await remoteExecutor()
}
