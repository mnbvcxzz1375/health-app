<template>
  <div class="apple-monitor pb-6">
    <!-- Page Header -->
    <div class="mx-auto max-w-[420px] px-4 pt-4">
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition active:scale-95"
          style="background: var(--secondary); color: var(--foreground);"
          aria-label="返回"
          @click="router.push('/home')"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">设备聚合</h1>
      </div>
      <p class="mt-0.5 pl-11 text-[14px]" style="color: var(--muted-foreground);">统一管理可穿戴设备与健康数据源</p>
    </div>

    <!-- 概览统计 -->
    <div class="mx-auto mt-3 flex max-w-[420px] gap-2 px-4">
      <div
        v-for="stat in overviewStats"
        :key="stat.label"
        class="flex-1 rounded-[19.2px] p-[14px]"
        style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center gap-1.5">
          <span class="h-2 w-2 rounded-full" :style="{ background: stat.color }" />
          <span class="text-[11px]" style="color: var(--muted-foreground);">{{ stat.label }}</span>
        </div>
        <div class="mt-1 text-[22px] font-semibold" style="color: var(--foreground); font-variant-numeric: tabular-nums;">{{ stat.value }}</div>
      </div>
    </div>

    <!-- 当前绑定列表 -->
    <div class="mx-auto mt-4 max-w-[420px] px-4">
      <div class="flex items-center justify-between">
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">已绑定设备</h2>
        <button
          type="button"
          class="flex h-8 items-center gap-1 rounded-full px-3 text-[13px] font-medium transition active:scale-[0.98]"
          style="background: var(--brand-500); color: var(--primary-foreground);"
          @click="router.push('/devices/brands')"
        >
          <iconify-icon icon="solar:add-circle-bold" width="16" height="16" />
          添加
        </button>
      </div>

      <div v-if="bindingsLoading" class="mt-3 flex h-24 items-center justify-center">
        <iconify-icon icon="solar:refresh-outline" width="20" height="20" class="animate-spin" style="color: var(--muted-foreground);" />
      </div>

      <div v-else-if="bindings.length === 0" class="mt-3">
        <div
          class="rounded-[19.2px] p-[18px] text-center"
          style="background: var(--card); border: 1px solid var(--border);"
        >
          <iconify-icon icon="solar:devices-outline" width="32" height="32" style="color: var(--muted-foreground);" />
          <p class="mt-2 text-[14px]" style="color: var(--muted-foreground);">尚未绑定任何设备</p>
          <button
            type="button"
            class="mt-3 h-9 rounded-full px-4 text-[13px] font-medium"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            @click="router.push('/devices/brands')"
          >
            浏览支持设备
          </button>
        </div>
      </div>

      <div v-else class="mt-3 space-y-2">
        <div
          v-for="b in bindings"
          :key="b.id"
          class="rounded-[19.2px] p-[14px]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center justify-between">
            <div class="flex min-w-0 items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full"
                :style="{ background: 'var(--secondary)' }"
              >
                <iconify-icon :icon="deviceIcon(b.deviceType)" width="20" height="20" :style="{ color: 'var(--foreground)' }" />
              </div>
              <div class="min-w-0">
                <p class="truncate text-[15px] font-semibold" style="color: var(--foreground);">{{ b.displayName }}</p>
                <p class="truncate text-[12px]" style="color: var(--muted-foreground);">{{ deviceTypeLabel(b.deviceType) }} · {{ providerLabel(b.provider) }}</p>
              </div>
            </div>
            <span
              class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
              :style="statusStyle(b.status)"
            >{{ statusLabel(b.status) }}</span>
          </div>

          <div class="mt-2 flex items-center justify-between text-[12px]" style="color: var(--muted-foreground);">
            <span>{{ b.lastSyncAt ? `上次同步 ${formatRelative(b.lastSyncAt)}` : '未同步' }}</span>
            <div class="flex gap-1.5">
              <button
                type="button"
                class="h-7 rounded-full px-3 text-[12px] font-medium transition active:scale-95"
                style="background: var(--secondary); color: var(--foreground);"
                :disabled="syncingId === b.id"
                @click="onSync(b.id)"
              >
                <iconify-icon v-if="syncingId === b.id" icon="solar:refresh-outline" width="12" height="12" class="animate-spin inline-block" />
                同步
              </button>
              <button
                type="button"
                class="h-7 rounded-full px-3 text-[12px] font-medium transition active:scale-95"
                style="background: var(--secondary); color: var(--foreground);"
                @click="onUnbind(b.id)"
              >解绑</button>
            </div>
          </div>

          <p v-if="b.lastError" class="mt-1.5 text-[11px]" style="color: var(--destructive);">{{ b.lastError }}</p>
        </div>
      </div>
    </div>

    <!-- 三大 Pillar metric 路由 -->
    <div v-for="pillar in pillarGroups" :key="pillar.key" class="mx-auto mt-5 max-w-[420px] px-4">
      <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">{{ pillar.label }}</h2>
      <p class="text-[12px]" style="color: var(--muted-foreground);">{{ pillar.description }}</p>

      <div class="mt-2 grid grid-cols-2 gap-2">
        <button
          v-for="m in pillar.metrics"
          :key="m.metric"
          type="button"
          class="rounded-[19.2px] p-[14px] text-left transition active:scale-[0.98]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          @click="router.push(`/devices/metric/${m.metric}`)"
        >
          <div class="flex items-center justify-between">
            <iconify-icon :icon="m.icon" width="18" height="18" :style="{ color: 'var(--brand-500)' }" />
            <span
              v-if="m.connectedCount > 0"
              class="rounded-full px-1.5 py-0.5 text-[10px]"
              style="background: var(--brand-500); color: var(--primary-foreground);"
            >{{ m.connectedCount }}</span>
            <span
              v-else
              class="rounded-full px-1.5 py-0.5 text-[10px]"
              style="background: var(--secondary); color: var(--muted-foreground);"
            >手动</span>
          </div>
          <p class="mt-2 text-[13px] font-semibold" style="color: var(--foreground);">{{ m.metricLabel }}</p>
          <p class="text-[11px]" style="color: var(--muted-foreground);">{{ deviceTypeLabel(m.preferredDeviceType) }}</p>
        </button>
      </div>
    </div>

    <!-- 底部入口 -->
    <div class="mx-auto mt-6 max-w-[420px] space-y-2 px-4">
      <button
        type="button"
        class="flex w-full items-center justify-between rounded-[19.2px] p-[14px] transition active:scale-[0.99]"
        style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
        @click="router.push('/devices/brands')"
      >
        <div class="flex items-center gap-3">
          <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--secondary);">
            <iconify-icon icon="solar:devices-outline" width="18" height="18" style="color: var(--foreground);" />
          </div>
          <div class="text-left">
            <p class="text-[14px] font-semibold" style="color: var(--foreground);">浏览全部支持设备</p>
            <p class="text-[11px]" style="color: var(--muted-foreground);">14 家可穿戴厂商 + Apple Health + 蓝牙 + SDK</p>
          </div>
        </div>
        <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
      </button>

      <button
        type="button"
        class="flex w-full items-center justify-between rounded-[19.2px] p-[14px] transition active:scale-[0.99]"
        style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
        @click="router.push('/devices/sdk-keys')"
      >
        <div class="flex items-center gap-3">
          <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--secondary);">
            <iconify-icon icon="solar:key-outline" width="18" height="18" style="color: var(--foreground);" />
          </div>
          <div class="text-left">
            <p class="text-[14px] font-semibold" style="color: var(--foreground);">开放 SDK 密钥</p>
            <p class="text-[11px]" style="color: var(--muted-foreground);">为第三方应用签发 API Key 接入设备聚合</p>
          </div>
        </div>
        <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import {
  deleteBinding,
  getBindings,
  syncBinding,
  type BindingItem,
} from '@/api/modules/deviceAggregation'

const router = useRouter()
const toast = useToastStore()

const bindings = ref<BindingItem[]>([])
const bindingsLoading = ref(true)
const syncingId = ref<number | null>(null)

// 18 个 metric 分组
const allMetrics = [
  // physical
  { metric: 'heart_rate', metricLabel: '心率', preferredDeviceType: 'watch', icon: 'solar:heart-pulse-outline' },
  { metric: 'hrv', metricLabel: '心率变异性', preferredDeviceType: 'watch', icon: 'solar:heart-broken-outline' },
  { metric: 'steps', metricLabel: '步数', preferredDeviceType: 'watch', icon: 'solar:walking-outline' },
  { metric: 'calories', metricLabel: '活动能量', preferredDeviceType: 'watch', icon: 'solar:fire-outline' },
  { metric: 'exercise_minutes', metricLabel: '锻炼分钟', preferredDeviceType: 'watch', icon: 'solar:running-outline' },
  { metric: 'stand_hours', metricLabel: '站立小时', preferredDeviceType: 'watch', icon: 'solar:body-outline' },
  { metric: 'vo2_max', metricLabel: '最大摄氧量', preferredDeviceType: 'watch', icon: 'solar:airbuds-case-outline' },
  // body
  { metric: 'weight', metricLabel: '体重', preferredDeviceType: 'scale', icon: 'solar:scale-outline' },
  { metric: 'bmi', metricLabel: 'BMI', preferredDeviceType: 'scale', icon: 'solar:chart-square-outline' },
  { metric: 'blood_pressure', metricLabel: '血压', preferredDeviceType: 'bp_monitor', icon: 'solar:heart-rate-monitor-outline' },
  { metric: 'blood_glucose', metricLabel: '血糖', preferredDeviceType: 'cgm', icon: 'solar:water-drop-outline' },
  { metric: 'spo2', metricLabel: '血氧', preferredDeviceType: 'pulse_ox', icon: 'solar:medical-kit-outline' },
  { metric: 'respiratory_rate', metricLabel: '呼吸频率', preferredDeviceType: 'pulse_ox', icon: 'solar:air-outline' },
  { metric: 'body_temperature', metricLabel: '体温', preferredDeviceType: 'thermometer', icon: 'solar:thermometer-outline' },
  // sleep
  { metric: 'sleep_duration', metricLabel: '睡眠时长', preferredDeviceType: 'sleep_monitor', icon: 'solar:moon-stars-outline' },
  { metric: 'sleep_stage', metricLabel: '睡眠分期', preferredDeviceType: 'sleep_monitor', icon: 'solar:bed-outline' },
  // rehab
  { metric: 'rehab_motion', metricLabel: '康复动作', preferredDeviceType: 'rehab_sensor', icon: 'solar:physical-therapy-outline' },
  { metric: 'rom', metricLabel: '关节活动度', preferredDeviceType: 'rehab_sensor', icon: 'solar:body-outline' },
] as const

const pillarGroups = computed(() => {
  const physicalMetrics = allMetrics
    .filter((m) => ['heart_rate', 'hrv', 'steps', 'calories', 'exercise_minutes', 'stand_hours', 'vo2_max'].includes(m.metric))
    .map((m) => ({ ...m, connectedCount: connectedCountFor(m.metric) }))
  const bodyMetrics = allMetrics
    .filter((m) => ['weight', 'bmi', 'blood_pressure', 'blood_glucose', 'spo2', 'respiratory_rate', 'body_temperature'].includes(m.metric))
    .map((m) => ({ ...m, connectedCount: connectedCountFor(m.metric) }))
  const sleepMetrics = allMetrics
    .filter((m) => ['sleep_duration', 'sleep_stage'].includes(m.metric))
    .map((m) => ({ ...m, connectedCount: connectedCountFor(m.metric) }))
  const rehabMetrics = allMetrics
    .filter((m) => ['rehab_motion', 'rom'].includes(m.metric))
    .map((m) => ({ ...m, connectedCount: connectedCountFor(m.metric) }))

  return [
    { key: 'physical', label: '运动健康', description: '心率、步数、活动能量等', metrics: physicalMetrics },
    { key: 'body', label: '体征健康', description: '体重、血压、血糖等', metrics: bodyMetrics },
    { key: 'sleep', label: '睡眠健康', description: '睡眠时长与分期', metrics: sleepMetrics },
    { key: 'rehab', label: '康复训练', description: '康复动作与关节活动度', metrics: rehabMetrics },
  ]
})

function connectedCountFor(_metric: string): number {
  // 简化：用绑定数代替，未来可改为 getMetricRoute(metric).connectedSources.length
  return bindings.value.filter((b) => b.status === 'connected').length
}

const overviewStats = computed(() => {
  const connected = bindings.value.filter((b) => b.status === 'connected').length
  const stale = bindings.value.filter((b) => b.status === 'stale').length
  const disconnected = bindings.value.filter((b) => b.status === 'disconnected').length
  return [
    { label: '已连接', value: connected, color: '#10b981' },
    { label: '待同步', value: stale, color: '#f59e0b' },
    { label: '已断开', value: disconnected, color: '#ef4444' },
  ]
})

onMounted(async () => {
  await loadBindings()
})

async function loadBindings() {
  bindingsLoading.value = true
  try {
    bindings.value = await getBindings()
  } catch {
    // toast 由 http interceptor 处理
  } finally {
    bindingsLoading.value = false
  }
}

async function onSync(bindingId: number) {
  syncingId.value = bindingId
  try {
    const result = await syncBinding(bindingId)
    if (result.success) {
      toast.success('同步成功', result.message)
    } else {
      toast.error('同步失败', result.message)
    }
    await loadBindings()
  } finally {
    syncingId.value = null
  }
}

async function onUnbind(bindingId: number) {
  if (!window.confirm('确认解绑此设备？')) return
  try {
    const result = await deleteBinding(bindingId)
    if (result.success) {
      toast.success('已解绑', result.message)
      await loadBindings()
    } else {
      toast.error('解绑失败', result.message)
    }
  } catch {
    // ignored
  }
}

function deviceIcon(deviceType: string): string {
  const map: Record<string, string> = {
    watch: 'solar:clock-circle-outline',
    scale: 'solar:scale-outline',
    bp_monitor: 'solar:heart-rate-monitor-outline',
    cgm: 'solar:water-drop-outline',
    sleep_monitor: 'solar:moon-stars-outline',
    pulse_ox: 'solar:medical-kit-outline',
    thermometer: 'solar:thermometer-outline',
    rehab_sensor: 'solar:physical-therapy-outline',
    ring: 'solar:emoji-funny-circle-outline',
    other: 'solar:devices-outline',
  }
  return map[deviceType] ?? 'solar:devices-outline'
}

function deviceTypeLabel(deviceType: string): string {
  const map: Record<string, string> = {
    watch: '智能手表',
    scale: '体脂秤',
    bp_monitor: '血压计',
    cgm: '动态血糖仪',
    sleep_monitor: '睡眠监测器',
    pulse_ox: '血氧仪',
    thermometer: '体温计',
    rehab_sensor: '康复传感器',
    ring: '智能戒指',
    other: '其他设备',
  }
  return map[deviceType] ?? deviceType
}

function providerLabel(provider: string): string {
  const map: Record<string, string> = {
    manual: '手动输入',
    apple_health: 'Apple Health',
    bluetooth: '蓝牙',
    sdk: '开放 SDK',
    rook: 'ROOK',
    garmin: 'Garmin',
    oura: 'Oura Ring',
    fitbit: 'Fitbit',
    withings: 'Withings',
    polar: 'Polar',
    whoop: 'Whoop',
    dexcom: 'Dexcom',
    strava: 'Strava',
    samsung_health: 'Samsung Health',
    mi_fitness: 'Mi Fitness',
    zepp: 'Zepp',
    health_connect: 'Health Connect',
    android: 'Android',
  }
  return map[provider] ?? provider
}

function statusLabel(status: string): string {
  return { connected: '已连接', stale: '待同步', disconnected: '已断开' }[status] ?? status
}

function statusStyle(status: string): { background: string; color: string } {
  if (status === 'connected') return { background: 'rgba(16,185,129,0.12)', color: '#10b981' }
  if (status === 'stale') return { background: 'rgba(245,158,11,0.12)', color: '#f59e0b' }
  return { background: 'rgba(239,68,68,0.12)', color: '#ef4444' }
}

function formatRelative(iso: string): string {
  const d = new Date(iso)
  const diffMs = Date.now() - d.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr} 小时前`
  const diffDay = Math.floor(diffHr / 24)
  return `${diffDay} 天前`
}
</script>
