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
          @click="goBack"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">连接设备</h1>
      </div>
      <p class="mt-0.5 pl-11 text-[14px]" style="color: var(--muted-foreground);">选择品牌完成 OAuth 授权</p>
    </div>

    <!-- 筛选 chip -->
    <div v-if="deviceTypeFilter" class="mx-auto mt-3 flex max-w-[420px] items-center gap-2 px-4">
      <span class="text-[12px]" style="color: var(--muted-foreground);">已筛选：</span>
      <span
        class="flex items-center gap-1 rounded-full px-2.5 py-1 text-[12px] font-medium"
        style="background: var(--secondary); color: var(--foreground);"
      >
        {{ deviceTypeLabel(deviceTypeFilter) }}
        <button type="button" aria-label="清除筛选" @click="deviceTypeFilter = ''">
          <iconify-icon icon="solar:close-circle-bold" width="14" height="14" />
        </button>
      </span>
    </div>

    <!-- 按设备类型分组 -->
    <div v-for="group in groupedProviders" :key="group.deviceType" class="mx-auto mt-4 max-w-[420px] px-4">
      <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">{{ deviceTypeLabel(group.deviceType) }}</h2>
      <div class="mt-2 grid grid-cols-2 gap-2">
        <button
          v-for="p in group.providers"
          :key="p.providerName"
          type="button"
          class="rounded-[19.2px] p-[14px] text-left transition active:scale-[0.98]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          @click="onProviderClick(p)"
        >
          <div class="flex items-center justify-between">
            <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--secondary);">
              <iconify-icon :icon="providerIcon(p.providerName)" width="18" height="18" :style="{ color: 'var(--foreground)' }" />
            </div>
            <span
              v-if="p.configured"
              class="rounded-full px-1.5 py-0.5 text-[10px]"
              style="background: rgba(16,185,129,0.12); color: #10b981;"
            >可用</span>
            <span
              v-else
              class="rounded-full px-1.5 py-0.5 text-[10px]"
              style="background: var(--secondary); color: var(--muted-foreground);"
            >未配置</span>
          </div>
          <p class="mt-2 text-[13px] font-semibold" style="color: var(--foreground);">{{ p.displayName }}</p>
          <p class="text-[11px]" style="color: var(--muted-foreground);">{{ deviceTypeLabel(p.deviceType) }}</p>
        </button>
      </div>
    </div>

    <!-- 特殊入口：Apple Health / 蓝牙 / 手动输入 / SDK -->
    <div class="mx-auto mt-5 max-w-[420px] px-4">
      <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">其他数据源</h2>
      <div class="mt-2 space-y-2">
        <button
          v-for="p in specialProviders"
          :key="p.providerName"
          type="button"
          class="flex w-full items-center gap-3 rounded-[19.2px] p-[14px] text-left transition active:scale-[0.99]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          @click="onProviderClick(p)"
        >
          <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--secondary);">
            <iconify-icon :icon="providerIcon(p.providerName)" width="18" height="18" :style="{ color: 'var(--foreground)' }" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-[14px] font-semibold" style="color: var(--foreground);">{{ p.displayName }}</p>
            <p class="truncate text-[11px]" style="color: var(--muted-foreground);">{{ p.supportedMetrics.join(' · ') }}</p>
          </div>
          <iconify-icon icon="solar:alt-arrow-right-outline" width="16" height="16" style="color: var(--muted-foreground);" />
        </button>
      </div>
    </div>

    <!-- 加载 -->
    <div v-if="loading" class="mx-auto mt-10 flex max-w-[420px] items-center justify-center px-4">
      <iconify-icon icon="solar:refresh-outline" width="24" height="24" class="animate-spin" style="color: var(--muted-foreground);" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import {
  getProviders,
  startOAuth,
  type ProviderInfo,
} from '@/api/modules/deviceAggregation'

const router = useRouter()
const route = useRoute()
const toast = useToastStore()

const providers = ref<ProviderInfo[]>([])
const loading = ref(true)
const deviceTypeFilter = ref<string>('')

const SPECIAL_PROVIDERS = ['manual', 'apple_health', 'bluetooth', 'sdk', 'rook']

onMounted(async () => {
  // 从 query 读取设备类型筛选
  if (typeof route.query.device_type === 'string') {
    deviceTypeFilter.value = route.query.device_type
  }
  // 自动连接指定 provider（来自 MetricRoutePage 的 onConnect）
  if (typeof route.query.connect === 'string' && route.query.connect) {
    await loadProviders()
    const target = providers.value.find((p) => p.providerName === route.query.connect)
    if (target) {
      void onProviderClick(target)
      return
    }
  }
  await loadProviders()
})

async function loadProviders() {
  loading.value = true
  try {
    providers.value = await getProviders()
  } catch {
    // ignored
  } finally {
    loading.value = false
  }
}

const filteredProviders = computed(() => {
  let list = providers.value
  if (deviceTypeFilter.value) {
    list = list.filter((p) => p.deviceType === deviceTypeFilter.value)
  }
  return list
})

const oauthProviders = computed(() => {
  return filteredProviders.value.filter((p) => !SPECIAL_PROVIDERS.includes(p.providerName))
})

const specialProviders = computed(() => {
  return filteredProviders.value.filter((p) => SPECIAL_PROVIDERS.includes(p.providerName))
})

const groupedProviders = computed(() => {
  const groups = new Map<string, ProviderInfo[]>()
  for (const p of oauthProviders.value) {
    if (!groups.has(p.deviceType)) groups.set(p.deviceType, [])
    groups.get(p.deviceType)!.push(p)
  }
  return Array.from(groups.entries()).map(([deviceType, providers]) => ({ deviceType, providers }))
})

async function onProviderClick(p: ProviderInfo) {
  // 特殊 provider 直接跳转
  if (p.providerName === 'manual') {
    toast.info('手动输入', '请前往具体指标页面录入数据')
    return
  }
  if (p.providerName === 'apple_health') {
    toast.info('Apple Health', '请前往首页 Apple Health 卡片读取')
    return
  }
  if (p.providerName === 'bluetooth') {
    void router.push('/devices/legacy')
    return
  }
  if (p.providerName === 'sdk') {
    toast.info('开放 SDK', '请前往开发者文档查看接入方式')
    return
  }

  // OAuth provider
  if (!p.configured) {
    toast.warning('未配置', `管理员未配置 ${p.displayName} 凭证`)
    return
  }

  try {
    const result = await startOAuth(p.providerName)
    if (result.authorizationUrl) {
      window.location.href = result.authorizationUrl
    }
  } catch (e: unknown) {
    toast.error('启动授权失败', (e as Error)?.message ?? '未知错误')
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else void router.push('/devices')
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

function providerIcon(providerName: string): string {
  const map: Record<string, string> = {
    manual: 'solar:hand-money-outline',
    apple_health: 'solar:apple-outline',
    bluetooth: 'solar:bluetooth-circle-outline',
    sdk: 'solar:code-circle-outline',
    rook: 'solar:cloud-outline',
    garmin: 'solar:watch-outline',
    oura: 'solar:emoji-funny-circle-outline',
    fitbit: 'solar:watch-outline',
    withings: 'solar:scale-outline',
    polar: 'solar:watch-outline',
    whoop: 'solar:watch-outline',
    dexcom: 'solar:water-drop-outline',
    strava: 'solar:running-outline',
    samsung_health: 'solar:watch-outline',
    mi_fitness: 'solar:watch-outline',
    zepp: 'solar:watch-outline',
    health_connect: 'solar:health-outline',
    android: 'solar:smartphone-outline',
  }
  return map[providerName] ?? 'solar:devices-outline'
}
</script>
