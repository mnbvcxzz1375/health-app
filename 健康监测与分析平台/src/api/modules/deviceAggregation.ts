import { http } from '@/api/http'
import { cloneMock, mockDelay, nowIsoString, withMockFallback } from '@/dev/mockApi'
import type { AppleHealthSnapshot } from '@/modules/home/services/appleHealthBridge'

// ===== 类型定义 =====

export type ProviderInfo = {
  providerName: string
  displayName: string
  deviceType: string
  configured: boolean
  available: boolean
  supportedMetrics: string[]
}

export type BindingItem = {
  id: number
  provider: string
  displayName: string
  deviceType: string
  status: 'connected' | 'stale' | 'disconnected'
  lastSyncAt: string | null
  lastSyncStatus: string
  lastError: string | null
}

export type SourceItem = {
  provider: string
  displayName: string
  deviceType: string
  status: 'connected' | 'stale' | 'disconnected' | 'available'
  lastSyncAt: string | null
  bindingDisplayName: string | null
}

export type MetricRouteResponse = {
  metric: string
  metricLabel: string
  preferredDeviceType: string
  fallbackDeviceType: string
  pillar: 'physical' | 'body' | 'sleep' | 'rehab'
  icon: string
  connectedSources: SourceItem[]
  staleSources: SourceItem[]
  availableSources: SourceItem[]
  manualInputSupported: boolean
}

export type ManualInputPayload = {
  metric: string
  value: number
  recordedAt?: string
  note?: string
}

export type ManualInputResponse = {
  accepted: boolean
  metric: string
  value: number
  recordedAt: string
}

export type SyncLogItem = {
  id: number
  bindingId: number
  syncStartedAt: string | null
  syncEndedAt: string | null
  status: string
  recordsPulled: number
  recordsWritten: number
  errorMessage: string | null
}

export type AuthorizeResponse = {
  provider: string
  authorizationUrl: string
}

export type OperationResult = {
  success: boolean
  message: string
}

export type AppleHealthSnapshotResponse = {
  accepted: boolean
  recordedAt: string
  message: string
}

// ===== 内部 mock 状态 =====

interface MockBinding {
  id: number
  provider: string
  displayName: string
  deviceType: string
  status: BindingItem['status']
  lastSyncAt: string | null
  lastSyncStatus: string
  lastError: string | null
}

const mockBindings = ref<MockBinding[]>([
  {
    id: 1,
    provider: 'apple_health',
    displayName: 'Apple Health',
    deviceType: 'watch',
    status: 'connected',
    lastSyncAt: nowIsoString(),
    lastSyncStatus: 'success',
    lastError: null,
  },
])
let nextMockBindingId = 2

// ===== API 函数 =====

/** GET /api/devices/providers — 列出所有 Provider */
export async function getProviders(): Promise<ProviderInfo[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<ProviderInfo[]>('/devices/providers')
      return data
    },
    () => {
      // mock：返回基础 Provider 列表
      return cloneMock([
        { providerName: 'manual', displayName: '手动输入', deviceType: 'other', configured: true, available: true, supportedMetrics: ['weight', 'heart_rate', 'blood_pressure'] },
        { providerName: 'apple_health', displayName: 'Apple Health', deviceType: 'watch', configured: true, available: true, supportedMetrics: ['heart_rate', 'steps', 'sleep_duration'] },
        { providerName: 'bluetooth', displayName: '蓝牙设备', deviceType: 'watch', configured: true, available: true, supportedMetrics: ['heart_rate', 'spo2'] },
        { providerName: 'garmin', displayName: 'Garmin', deviceType: 'watch', configured: false, available: false, supportedMetrics: ['heart_rate', 'steps'] },
        { providerName: 'oura', displayName: 'Oura Ring', deviceType: 'ring', configured: false, available: false, supportedMetrics: ['sleep_duration', 'hrv'] },
        { providerName: 'fitbit', displayName: 'Fitbit', deviceType: 'watch', configured: false, available: false, supportedMetrics: ['heart_rate', 'steps'] },
        { providerName: 'withings', displayName: 'Withings', deviceType: 'scale', configured: false, available: false, supportedMetrics: ['weight', 'blood_pressure'] },
      ] satisfies ProviderInfo[])
    },
    true,
  )
}

/** GET /api/devices/bindings — 当前用户绑定列表 */
export async function getBindings(): Promise<BindingItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<BindingItem[]>('/devices/bindings')
      return data
    },
    () => cloneMock(mockBindings.value),
    true,
  )
}

/** POST /api/devices/bindings/{provider}/authorize — 启动 OAuth */
export async function startOAuth(provider: string): Promise<AuthorizeResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<AuthorizeResponse>(`/devices/bindings/${provider}/authorize`)
      return data
    },
    () => ({
      provider,
      authorizationUrl: `mock://oauth/${provider}?state=mock-state`,
    }),
    true,
  )
}

/** GET /api/devices/oauth/callback/{provider}?code=...&state=... — OAuth 回调 */
export async function handleOAuthCallback(
  provider: string,
  code: string,
  state: string,
): Promise<OperationResult> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<OperationResult>(
        `/devices/oauth/callback/${provider}`,
        { params: { code, state } },
      )
      return data
    },
    () => {
      // mock：直接绑定成功
      const item: MockBinding = {
        id: nextMockBindingId++,
        provider,
        displayName: provider,
        deviceType: 'watch',
        status: 'connected',
        lastSyncAt: nowIsoString(),
        lastSyncStatus: 'success',
        lastError: null,
      }
      mockBindings.value = [...mockBindings.value, item]
      return { success: true, message: `已绑定 ${provider}` }
    },
    true,
  )
}

/** DELETE /api/devices/bindings/{bindingId} — 解绑 */
export async function deleteBinding(bindingId: number): Promise<OperationResult> {
  return withMockFallback(
    async () => {
      const { data } = await http.delete<OperationResult>(`/devices/bindings/${bindingId}`)
      return data
    },
    () => {
      mockBindings.value = mockBindings.value.filter((b) => b.id !== bindingId)
      return { success: true, message: '已解绑' }
    },
    true,
  )
}

/** POST /api/devices/bindings/{bindingId}/sync — 手动触发同步 */
export async function syncBinding(bindingId: number): Promise<OperationResult> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<OperationResult>(`/devices/bindings/${bindingId}/sync`)
      return data
    },
    async () => {
      await mockDelay(800)
      const target = mockBindings.value.find((b) => b.id === bindingId)
      if (target) {
        target.lastSyncAt = nowIsoString()
        target.lastSyncStatus = 'success'
      }
      return { success: true, message: '同步成功，写入 3 条' }
    },
    true,
  )
}

/** GET /api/devices/route/{metric} — 自动路由 */
export async function getMetricRoute(metric: string): Promise<MetricRouteResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<MetricRouteResponse>(`/devices/route/${metric}`)
      return data
    },
    () => mockMetricRoute(metric),
    true,
  )
}

function mockMetricRoute(metric: string): MetricRouteResponse {
  const routeMap: Record<string, Partial<MetricRouteResponse>> = {
    weight: { metricLabel: '体重', preferredDeviceType: 'scale', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:scale-outline' },
    bmi: { metricLabel: 'BMI', preferredDeviceType: 'scale', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:chart-square-outline' },
    heart_rate: { metricLabel: '心率', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:heart-pulse-outline' },
    hrv: { metricLabel: '心率变异性', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:heart-broken-outline' },
    steps: { metricLabel: '步数', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:walking-outline' },
    calories: { metricLabel: '活动能量', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:fire-outline' },
    blood_pressure: { metricLabel: '血压', preferredDeviceType: 'bp_monitor', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:heart-rate-monitor-outline' },
    blood_glucose: { metricLabel: '血糖', preferredDeviceType: 'cgm', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:water-drop-outline' },
    sleep_duration: { metricLabel: '睡眠时长', preferredDeviceType: 'sleep_monitor', fallbackDeviceType: 'manual', pillar: 'sleep', icon: 'solar:moon-stars-outline' },
    sleep_stage: { metricLabel: '睡眠分期', preferredDeviceType: 'sleep_monitor', fallbackDeviceType: 'manual', pillar: 'sleep', icon: 'solar:bed-outline' },
    spo2: { metricLabel: '血氧', preferredDeviceType: 'pulse_ox', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:medical-kit-outline' },
    respiratory_rate: { metricLabel: '呼吸频率', preferredDeviceType: 'pulse_ox', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:air-outline' },
    body_temperature: { metricLabel: '体温', preferredDeviceType: 'thermometer', fallbackDeviceType: 'manual', pillar: 'body', icon: 'solar:thermometer-outline' },
    exercise_minutes: { metricLabel: '锻炼分钟', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:running-outline' },
    stand_hours: { metricLabel: '站立小时', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:body-outline' },
    vo2_max: { metricLabel: '最大摄氧量', preferredDeviceType: 'watch', fallbackDeviceType: 'manual', pillar: 'physical', icon: 'solar:airbuds-case-outline' },
    rehab_motion: { metricLabel: '康复动作', preferredDeviceType: 'rehab_sensor', fallbackDeviceType: 'manual', pillar: 'rehab', icon: 'solar:physical-therapy-outline' },
    rom: { metricLabel: '关节活动度', preferredDeviceType: 'rehab_sensor', fallbackDeviceType: 'manual', pillar: 'rehab', icon: 'solar:body-outline' },
  }
  const meta = routeMap[metric] ?? { metricLabel: metric, preferredDeviceType: 'other', fallbackDeviceType: 'manual', pillar: 'body' as const, icon: 'solar:widget-outline' }
  const connectedSources: SourceItem[] = mockBindings.value
    .filter((b) => b.status === 'connected')
    .map((b) => ({
      provider: b.provider,
      displayName: b.displayName,
      deviceType: b.deviceType,
      status: 'connected' as const,
      lastSyncAt: b.lastSyncAt,
      bindingDisplayName: b.displayName,
    }))
  return {
    metric,
    metricLabel: meta.metricLabel!,
    preferredDeviceType: meta.preferredDeviceType!,
    fallbackDeviceType: meta.fallbackDeviceType!,
    pillar: meta.pillar!,
    icon: meta.icon!,
    connectedSources,
    staleSources: [],
    availableSources: [],
    manualInputSupported: true,
  }
}

/** POST /api/devices/manual — 手动输入数据 */
export async function pushManualData(payload: ManualInputPayload): Promise<ManualInputResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<ManualInputResponse>('/devices/manual', payload)
      return data
    },
    () => ({
      accepted: true,
      metric: payload.metric,
      value: payload.value,
      recordedAt: payload.recordedAt ?? nowIsoString(),
    }),
    true,
  )
}

/** POST /api/devices/apple-health/snapshot — 接收 Apple Health 快照 */
export async function pushAppleHealthSnapshot(snapshot: AppleHealthSnapshot): Promise<AppleHealthSnapshotResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<AppleHealthSnapshotResponse>(
        '/devices/apple-health/snapshot',
        snapshot,
      )
      return data
    },
    () => ({
      accepted: true,
      recordedAt: nowIsoString(),
      message: '已接收 Apple Health 快照（mock）',
    }),
    true,
  )
}

/** GET /api/devices/sync-logs/{bindingId} — 同步日志 */
export async function getSyncLogs(bindingId: number): Promise<SyncLogItem[]> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<SyncLogItem[]>(`/devices/sync-logs/${bindingId}`)
      return data
    },
    () => [
      {
        id: 1,
        bindingId,
        syncStartedAt: nowIsoString(),
        syncEndedAt: nowIsoString(),
        status: 'success',
        recordsPulled: 3,
        recordsWritten: 3,
        errorMessage: null,
      },
    ],
    true,
  )
}

// ===== 需要 vue ref 导入 =====
import { ref } from 'vue'

// 导出 mock 状态供调试
export const _mockBindings = mockBindings
