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
        <div class="flex items-center gap-2">
          <iconify-icon v-if="route?.icon" :icon="route.icon" width="22" height="22" :style="{ color: 'var(--brand-500)' }" />
          <h1 class="text-[24px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">{{ route?.metricLabel ?? metric }}</h1>
        </div>
      </div>
      <p class="mt-0.5 pl-11 text-[13px]" style="color: var(--muted-foreground);">推荐设备：{{ route ? deviceTypeLabel(route.preferredDeviceType) : '加载中…' }}</p>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="mx-auto mt-10 flex max-w-[420px] items-center justify-center px-4">
      <iconify-icon icon="solar:refresh-outline" width="24" height="24" class="animate-spin" style="color: var(--muted-foreground);" />
    </div>

    <template v-else-if="route">
      <!-- 1. 已连接设备（connectedSources） -->
      <div v-if="route.connectedSources.length > 0" class="mx-auto mt-4 max-w-[420px] px-4">
        <div class="flex items-center gap-1.5">
          <span class="h-2 w-2 rounded-full" style="background: #10b981;" />
          <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">已连接设备</h2>
        </div>
        <div class="mt-2 space-y-2">
          <div
            v-for="src in route.connectedSources"
            :key="src.provider"
            class="rounded-[19.2px] p-[14px]"
            style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          >
            <div class="flex items-center justify-between">
              <div class="flex min-w-0 items-center gap-3">
                <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--secondary);">
                  <iconify-icon :icon="deviceIcon(src.deviceType)" width="18" height="18" :style="{ color: 'var(--foreground)' }" />
                </div>
                <div class="min-w-0">
                  <p class="truncate text-[14px] font-semibold" style="color: var(--foreground);">{{ src.bindingDisplayName ?? src.displayName }}</p>
                  <p class="text-[11px]" style="color: var(--muted-foreground);">{{ src.lastSyncAt ? `上次同步 ${formatRelative(src.lastSyncAt)}` : '已就绪' }}</p>
                </div>
              </div>
              <button
                type="button"
                class="h-8 rounded-full px-3 text-[12px] font-medium transition active:scale-95"
                style="background: var(--brand-500); color: var(--primary-foreground);"
                :disabled="syncingProvider === src.provider"
                @click="onSyncConnected(src.provider)"
              >
                <iconify-icon v-if="syncingProvider === src.provider" icon="solar:refresh-outline" width="12" height="12" class="animate-spin inline-block" />
                立即同步
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. 待同步设备（staleSources） -->
      <div v-if="route.staleSources.length > 0" class="mx-auto mt-4 max-w-[420px] px-4">
        <div class="flex items-center gap-1.5">
          <span class="h-2 w-2 rounded-full" style="background: #f59e0b;" />
          <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">待重新同步</h2>
        </div>
        <p class="text-[12px]" style="color: var(--muted-foreground);">这些设备已绑定但超过 24 小时未同步数据</p>
        <div class="mt-2 space-y-2">
          <div
            v-for="src in route.staleSources"
            :key="src.provider"
            class="rounded-[19.2px] p-[14px]"
            style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          >
            <div class="flex items-center justify-between">
              <div class="flex min-w-0 items-center gap-3">
                <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: rgba(245,158,11,0.12);">
                  <iconify-icon :icon="deviceIcon(src.deviceType)" width="18" height="18" style="color: #f59e0b;" />
                </div>
                <div class="min-w-0">
                  <p class="truncate text-[14px] font-semibold" style="color: var(--foreground);">{{ src.bindingDisplayName ?? src.displayName }}</p>
                  <p class="text-[11px]" style="color: var(--muted-foreground);">{{ src.lastSyncAt ? `${formatRelative(src.lastSyncAt)}未同步` : '尚未同步' }}</p>
                </div>
              </div>
              <button
                type="button"
                class="h-8 rounded-full px-3 text-[12px] font-medium transition active:scale-95"
                style="background: var(--secondary); color: var(--foreground);"
                :disabled="syncingProvider === src.provider"
                @click="onSyncConnected(src.provider)"
              >重新同步</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. 可用设备（availableSources） -->
      <div v-if="route.availableSources.length > 0" class="mx-auto mt-4 max-w-[420px] px-4">
        <div class="flex items-center gap-1.5">
          <span class="h-2 w-2 rounded-full" style="background: var(--brand-500);" />
          <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">可连接设备</h2>
        </div>
        <p class="text-[12px]" style="color: var(--muted-foreground);">已配置但未绑定，点击连接</p>
        <div class="mt-2 grid grid-cols-2 gap-2">
          <button
            v-for="src in route.availableSources"
            :key="src.provider"
            type="button"
            class="rounded-[19.2px] p-[14px] text-left transition active:scale-[0.98]"
            style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
            @click="onConnect(src.provider)"
          >
            <iconify-icon :icon="deviceIcon(src.deviceType)" width="20" height="20" :style="{ color: 'var(--brand-500)' }" />
            <p class="mt-2 text-[13px] font-semibold" style="color: var(--foreground);">{{ src.displayName }}</p>
            <p class="text-[11px]" style="color: var(--muted-foreground);">{{ deviceTypeLabel(src.deviceType) }}</p>
          </button>
        </div>
      </div>

      <!-- 手动输入表单 -->
      <div v-if="route.manualInputSupported" class="mx-auto mt-5 max-w-[420px] px-4">
        <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">手动输入</h2>
        <p class="text-[12px]" style="color: var(--muted-foreground);">未连接设备时可直接录入数据</p>

        <div
          class="mt-2 rounded-[19.2px] p-[16px]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
        >
          <form class="space-y-3" @submit.prevent="onSubmitManual">
            <div>
              <label class="block text-[12px]" style="color: var(--muted-foreground);">{{ route.metricLabel }}数值</label>
              <div class="mt-1 flex items-center gap-2">
                <input
                  v-model.number="manualValue"
                  type="number"
                  step="0.1"
                  inputmode="decimal"
                  class="h-11 flex-1 rounded-xl px-3 text-[16px] outline-none"
                  style="background: var(--secondary); color: var(--foreground); border: 1px solid var(--border);"
                  :placeholder="placeholderForMetric(metric)"
                />
                <span class="text-[13px]" style="color: var(--muted-foreground);">{{ unitForMetric(metric) }}</span>
              </div>
            </div>

            <div>
              <label class="block text-[12px]" style="color: var(--muted-foreground);">备注（可选）</label>
              <input
                v-model="manualNote"
                type="text"
                class="mt-1 h-10 w-full rounded-xl px-3 text-[14px] outline-none"
                style="background: var(--secondary); color: var(--foreground); border: 1px solid var(--border);"
                placeholder="如：晨起测量"
              />
            </div>

            <button
              type="submit"
              class="h-11 w-full rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
              style="background: var(--brand-500); color: var(--primary-foreground);"
              :disabled="manualValue === null || submitting"
            >
              <iconify-icon v-if="submitting" icon="solar:refresh-outline" width="14" height="14" class="animate-spin inline-block" />
              提交数据
            </button>
          </form>
        </div>
      </div>

      <!-- 连接新设备入口 -->
      <div class="mx-auto mt-5 max-w-[420px] px-4">
        <button
          type="button"
          class="flex w-full items-center justify-between rounded-[19.2px] p-[14px] transition active:scale-[0.99]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
          @click="router.push(`/devices/brands?device_type=${route.preferredDeviceType}`)"
        >
          <div class="flex items-center gap-3">
            <div class="flex h-9 w-9 items-center justify-center rounded-full" style="background: var(--secondary);">
              <iconify-icon icon="solar:add-circle-outline" width="18" height="18" style="color: var(--foreground);" />
            </div>
            <div class="text-left">
              <p class="text-[14px] font-semibold" style="color: var(--foreground);">连接新设备</p>
              <p class="text-[11px]" style="color: var(--muted-foreground);">浏览所有支持{{ route.metricLabel }}的设备</p>
            </div>
          </div>
          <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
        </button>
      </div>
    </template>

    <!-- 错误态 -->
    <div v-else-if="error" class="mx-auto mt-10 max-w-[420px] px-4 text-center">
      <iconify-icon icon="solar:danger-triangle-outline" width="32" height="32" style="color: var(--destructive);" />
      <p class="mt-2 text-[14px]" style="color: var(--foreground);">无法加载路由信息</p>
      <p class="text-[12px]" style="color: var(--muted-foreground);">{{ error }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import {
  getMetricRoute,
  pushManualData,
  syncBinding,
  getBindings,
  type MetricRouteResponse,
  type BindingItem,
} from '@/api/modules/deviceAggregation'

const router = useRouter()
const toast = useToastStore()

const props = defineProps<{ metric: string }>()

const route = ref<MetricRouteResponse | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const syncingProvider = ref<string | null>(null)

const manualValue = ref<number | null>(null)
const manualNote = ref('')
const submitting = ref(false)

// 已连接设备的 binding 列表（用于按 provider 查 bindingId）
const bindings = ref<BindingItem[]>([])

onMounted(async () => {
  await Promise.all([loadRoute(), loadBindings()])
})

async function loadRoute() {
  loading.value = true
  error.value = null
  try {
    route.value = await getMetricRoute(props.metric)
  } catch (e: unknown) {
    error.value = (e as Error)?.message ?? '未知错误'
  } finally {
    loading.value = false
  }
}

async function loadBindings() {
  try {
    bindings.value = await getBindings()
  } catch {
    // ignored
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else void router.push('/devices')
}

async function onSyncConnected(provider: string) {
  // 通过 provider 找到 bindingId
  const binding = bindings.value.find((b) => b.provider === provider)
  if (!binding) {
    toast.error('同步失败', `未找到 ${provider} 的绑定记录`)
    return
  }
  syncingProvider.value = provider
  try {
    const result = await syncBinding(binding.id)
    if (result.success) {
      toast.success('同步成功', result.message)
      await Promise.all([loadRoute(), loadBindings()])
    } else {
      toast.error('同步失败', result.message)
    }
  } finally {
    syncingProvider.value = null
  }
}

async function onConnect(provider: string) {
  // 跳转到品牌授权页
  void router.push({ path: '/devices/brands', query: { connect: provider } })
}

async function onSubmitManual() {
  if (manualValue.value === null) return
  submitting.value = true
  try {
    const resp = await pushManualData({
      metric: props.metric,
      value: manualValue.value,
      note: manualNote.value || undefined,
    })
    if (resp.accepted) {
      toast.success('已提交', `已记录 ${resp.value} ${unitForMetric(props.metric)}`)
      manualValue.value = null
      manualNote.value = ''
    } else {
      toast.error('提交失败', '服务端未接受')
    }
  } finally {
    submitting.value = false
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

function unitForMetric(metric: string): string {
  const map: Record<string, string> = {
    weight: 'kg',
    bmi: '',
    heart_rate: 'bpm',
    hrv: 'ms',
    steps: '步',
    calories: 'kcal',
    blood_pressure: 'mmHg',
    blood_glucose: 'mmol/L',
    sleep_duration: 'h',
    sleep_stage: '',
    spo2: '%',
    respiratory_rate: '次/分',
    body_temperature: '℃',
    exercise_minutes: '分钟',
    stand_hours: '小时',
    vo2_max: 'mL/kg/min',
    rehab_motion: '次',
    rom: '度',
  }
  return map[metric] ?? ''
}

function placeholderForMetric(metric: string): string {
  const map: Record<string, string> = {
    weight: '如 65.5',
    bmi: '如 22.1',
    heart_rate: '如 72',
    hrv: '如 42',
    steps: '如 5320',
    calories: '如 320',
    blood_pressure: '如 120（收缩压）',
    blood_glucose: '如 5.5',
    sleep_duration: '如 7.8',
    spo2: '如 98',
    respiratory_rate: '如 16',
    body_temperature: '如 36.5',
    exercise_minutes: '如 26',
    stand_hours: '如 8',
    vo2_max: '如 38.5',
    rehab_motion: '如 15',
    rom: '如 90',
  }
  return map[metric] ?? '请输入数值'
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
