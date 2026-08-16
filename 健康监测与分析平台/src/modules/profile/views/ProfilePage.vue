<template>
  <div class="apple-profile pb-6">
    <!-- Page Title -->
    <div class="mx-auto max-w-[420px] px-4">
      <h1
        class="pt-4 text-[28px] font-semibold tracking-[-0.02em]"
        style="color: var(--foreground); line-height: 1.1;"
      >我的</h1>
    </div>

    <div class="mx-auto mt-5 max-w-[420px] space-y-5 px-4">
      <!-- Profile Header Card：点击进入个人信息修改 -->
      <button
        type="button"
        class="flex w-full items-center gap-3.5 rounded-[19.2px] border p-[18px] text-left transition active:scale-[0.99]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        @click="openSettings"
      >
        <!-- Avatar -->
        <div
          class="relative flex h-[60px] w-[60px] shrink-0 items-center justify-center overflow-hidden rounded-full"
          style="background: var(--brand-50);"
        >
          <img v-if="hasCustomAvatar" :src="avatarUrl" alt="用户头像" class="h-full w-full object-cover" />
          <span
            v-else
            class="text-[24px] font-semibold leading-none"
            style="color: var(--brand-500);"
          >{{ userInitial }}</span>
        </div>

        <!-- Content -->
        <div class="min-w-0 flex-1">
          <p class="truncate text-[20px] font-semibold leading-tight" style="color: var(--foreground);">{{ userName }}</p>
          <p class="mt-0.5 truncate text-[14px]" style="color: var(--muted-foreground);">{{ profileMeta }}</p>
          <!-- Stats row -->
          <div class="mt-2.5 flex gap-4">
            <div class="flex flex-col">
              <span class="text-[15px] font-semibold leading-none tabular-nums" style="color: var(--foreground);">
                <template v-if="stats.devices">{{ stats.devices }}</template>
                <span v-else class="inline-block h-4 w-8 animate-pulse rounded" style="background: var(--background-200);"></span>
              </span>
              <span class="mt-1 text-[11px]" style="color: var(--muted-foreground);">设备</span>
            </div>
            <div class="flex flex-col">
              <span class="text-[15px] font-semibold leading-none tabular-nums" style="color: var(--foreground);">
                <template v-if="stats.uploads">{{ stats.uploads }}</template>
                <span v-else class="inline-block h-4 w-8 animate-pulse rounded" style="background: var(--background-200);"></span>
              </span>
              <span class="mt-1 text-[11px]" style="color: var(--muted-foreground);">上传</span>
            </div>
            <div class="flex flex-col">
              <span class="text-[15px] font-semibold leading-none tabular-nums" style="color: var(--foreground);">
                <template v-if="displayRiskScore">{{ displayRiskScore }}</template>
                <span v-else class="inline-block h-4 w-8 animate-pulse rounded" style="background: var(--background-200);"></span>
              </span>
              <span class="mt-1 text-[11px]" style="color: var(--muted-foreground);">风险</span>
            </div>
          </div>
        </div>

        <!-- Chevron -->
        <iconify-icon icon="solar:alt-arrow-right-outline" width="20" height="20" style="color: var(--muted-foreground);" />
      </button>

      <!-- Section 1: 个人资料 -->
      <section>
        <h2 class="mb-2 ml-1 text-[13px] font-normal uppercase tracking-[0.02em]" style="color: var(--muted-foreground);">个人资料</h2>
        <div
          class="overflow-hidden rounded-[19.2px] border"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <!-- 修改头像 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="triggerAvatar"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--brand-50);">
              <iconify-icon icon="solar:camera-outline" width="16" height="16" style="color: var(--brand-500);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">修改头像</span>
            <span class="text-[14px]" style="color: var(--muted-foreground);">{{ hasCustomAvatar ? '已上传' : '未上传' }}</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 昵称 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openSettings"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: color-mix(in srgb, var(--chart-3) 12%, var(--card));">
              <iconify-icon icon="solar:user-outline" width="16" height="16" style="color: var(--chart-3);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">昵称</span>
            <span class="truncate text-[14px]" style="color: var(--muted-foreground); max-width: 160px;">{{ userName }}</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 邮箱 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openSettings"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--state-success-surface);">
              <iconify-icon icon="solar:letter-outline" width="16" height="16" style="color: var(--state-success);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">邮箱</span>
            <span class="truncate text-[14px]" style="color: var(--muted-foreground); max-width: 160px;">{{ userEmail || '未设置' }}</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
        </div>
        <input ref="avatarInput" class="hidden" type="file" accept="image/*" @change="onAvatarChange" />
      </section>

      <!-- Section 2: 健康数据 -->
      <section>
        <h2 class="mb-2 ml-1 text-[13px] font-normal uppercase tracking-[0.02em]" style="color: var(--muted-foreground);">健康数据</h2>
        <div
          class="overflow-hidden rounded-[19.2px] border"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <!-- 健康数据源 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openDataSources"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--state-error-surface);">
              <iconify-icon icon="solar:heart-pulse-outline" width="16" height="16" style="color: var(--state-error);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">健康数据源</span>
            <span class="text-[14px]" style="color: var(--muted-foreground);">{{ connectedSourcesLabel }}</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 设备聚合平台 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="router.push('/devices')"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--brand-50);">
              <iconify-icon icon="solar:devices-outline" width="16" height="16" style="color: var(--brand-500);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">设备聚合平台</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 健康报告 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="exportData"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--brand-50);">
              <iconify-icon icon="solar:document-text-outline" width="16" height="16" style="color: var(--brand-500);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">健康报告</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 同步健康数据 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            :disabled="syncing"
            @click="handleSync"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--state-success-surface);">
              <iconify-icon icon="solar:refresh-circle-outline" width="16" height="16" style="color: var(--state-success);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">同步健康数据</span>
            <span v-if="syncing" class="text-[14px]" style="color: var(--muted-foreground);">同步中…</span>
            <iconify-icon v-else icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
        </div>

        <!-- Sync Result Stats -->
        <div
          v-if="syncResult"
          class="mt-3 rounded-[19.2px] border p-4"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <p class="mb-3 text-[13px] font-medium" style="color: var(--muted-foreground);">最近同步数据</p>
          <div class="grid grid-cols-3 gap-3">
            <div v-for="item in syncStats" :key="item.label" class="rounded-[10px] p-2.5" style="background: var(--secondary);">
              <p class="text-[11px]" style="color: var(--muted-foreground);">{{ item.label }}</p>
              <p class="mt-1 text-[15px] font-semibold tabular-nums" style="color: var(--foreground);">{{ item.value }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Section 3: 账户 -->
      <section>
        <h2 class="mb-2 ml-1 text-[13px] font-normal uppercase tracking-[0.02em]" style="color: var(--muted-foreground);">账户</h2>
        <div
          class="overflow-hidden rounded-[19.2px] border"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <!-- 通知设置 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openSettings"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: color-mix(in srgb, var(--chart-3) 12%, var(--card));">
              <iconify-icon icon="solar:bell-bing-outline" width="16" height="16" style="color: var(--chart-3);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">通知设置</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 隐私中心：合并原隐私与安全 + 隐私中心 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openPrivacy"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--state-success-surface);">
              <iconify-icon icon="solar:shield-check-outline" width="16" height="16" style="color: var(--state-success);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">隐私中心</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 权限与授权 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openPermissions"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--brand-50);">
              <iconify-icon icon="solar:keyhole-square-outline" width="16" height="16" style="color: var(--brand-500);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">权限与授权</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 帮助与反馈 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openHelpFaq"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--background-200);">
              <iconify-icon icon="solar:question-circle-outline" width="16" height="16" style="color: var(--muted-foreground);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">帮助与反馈</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
        </div>
      </section>

      <!-- Section 4: 其他 -->
      <section>
        <h2 class="mb-2 ml-1 text-[13px] font-normal uppercase tracking-[0.02em]" style="color: var(--muted-foreground);">其他</h2>
        <div
          class="overflow-hidden rounded-[19.2px] border"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <!-- 关于 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            @click="openAbout"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--background-200);">
              <iconify-icon icon="solar:info-circle-outline" width="16" height="16" style="color: var(--muted-foreground);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">关于</span>
            <span class="text-[14px]" style="color: var(--muted-foreground);">v1.0.0</span>
            <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 清理缓存 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--background-100)]"
            :disabled="clearing"
            @click="clearCache"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--background-200);">
              <iconify-icon icon="solar:broom-outline" width="16" height="16" style="color: var(--muted-foreground);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--foreground);">清理缓存</span>
            <span v-if="clearing" class="text-[14px]" style="color: var(--muted-foreground);">清理中…</span>
            <iconify-icon v-else icon="solar:alt-arrow-right-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
          <div class="ml-14 h-px" style="background: var(--border);"></div>
          <!-- 删除账户 -->
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-[color:var(--state-error-surface)]"
            :disabled="deleting"
            @click="dangerZone"
          >
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full" style="background: var(--state-error-surface);">
              <iconify-icon icon="solar:trash-bin-minimalistic-outline" width="16" height="16" style="color: var(--state-error);" />
            </div>
            <span class="flex-1 text-[16px] leading-tight" style="color: var(--state-error);">
              {{ confirmDelete ? '确认删除账户' : '删除账户' }}
            </span>
            <span v-if="deleting" class="text-[14px]" style="color: var(--muted-foreground);">处理中…</span>
            <span v-else-if="confirmDelete" class="text-[12px]" style="color: var(--state-error);">再次点击确认</span>
          </button>
        </div>
      </section>

      <!-- Logout Button -->
      <button
        type="button"
        class="flex w-full items-center justify-center rounded-[19.2px] py-3.5 text-[16px] font-medium transition active:scale-[0.99]"
        :disabled="loggingOut"
        style="background: var(--state-error-surface); color: var(--state-error); letter-spacing: 0.01em;"
        @click="handleLogout"
      >
        {{ loggingOut ? '退出中…' : '退出登录' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { syncRookData, authorizeDataSource, getAuthorizedSources, type RookSyncResult } from '@/api/modules/rook'
import { getProfileSummary, updateProfileAvatar } from '@/api/modules/profile'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { success, warning, info, error } = useToast()

const stats = ref({
  devices: '',
  uploads: '',
  riskScore: '',
})

const userName = computed(() => authStore.userName)
const userEmail = computed(() => authStore.user?.email ?? '')
const avatarUrl = computed(() => authStore.avatarUrl)
const hasCustomAvatar = computed(() => {
  const url = authStore.user?.avatarUrl
  return Boolean(url) && !url!.startsWith('data:image/svg+xml')
})
const userInitial = computed(() => {
  const name = authStore.user?.name ?? ''
  return name.charAt(0) || '我'
})
const profileMeta = computed(() => {
  const email = userEmail.value
  return email || '查看并编辑个人资料'
})
const displayRiskScore = computed(() =>
  stats.value.riskScore
    .replace(/\blow\b/gi, '低风险')
    .replace(/\bmedium\b/gi, '中风险')
    .replace(/\bhigh\b/gi, '高风险'),
)
const connectedSourcesLabel = computed(() => {
  const count = dataSources.value.filter((d) => d.authorized).length
  return count > 0 ? `${count} 个` : '未连接'
})

const syncStats = computed(() => {
  if (!syncResult.value) return []
  const r = syncResult.value
  return [
    { label: '心率', value: `${r.hr} bpm` },
    { label: '睡眠评分', value: String(r.sleepScore) },
    { label: '压力评分', value: String(r.stressScore) },
    { label: 'HRV', value: `${r.hrv} ms` },
    { label: '步数', value: String(r.steps) },
    { label: 'VO2 Max', value: `${r.vo2Max}` },
    { label: '深度睡眠', value: `${r.deepSleepHours} h` },
  ]
})

const avatarInput = ref<HTMLInputElement | null>(null)

const clearing = ref(false)
const deleting = ref(false)
const loggingOut = ref(false)
const confirmDelete = ref(false)
let confirmTimer: number | null = null

const syncing = ref(false)
const syncResult = ref<RookSyncResult | null>(null)

type DeviceSource = {
  id: string
  label: string
  icon: string
  authorized: boolean
}

const allDataSources: DeviceSource[] = [
  { id: 'garmin', label: 'Garmin', icon: 'solar:watch-square-bold-duotone', authorized: false },
  { id: 'apple_health', label: 'Apple Health', icon: 'solar:heart-pulse-bold-duotone', authorized: false },
  { id: 'fitbit', label: 'Fitbit', icon: 'solar:watch-round-bold-duotone', authorized: false },
  { id: 'oura', label: 'Oura', icon: 'solar:moon-stars-bold-duotone', authorized: false },
  { id: 'withings', label: 'Withings', icon: 'solar:scales-bold-duotone', authorized: false },
]

const dataSources = ref<DeviceSource[]>(allDataSources.map((d) => ({ ...d })))
const sourcesLoading = ref(false)
const authorizingSource = ref<string | null>(null)

const handleLoadSources = async () => {
  sourcesLoading.value = true
  try {
    const result = await getAuthorizedSources()
    const authMap = result.sources ?? {}
    dataSources.value = allDataSources.map((d) => ({
      ...d,
      authorized: authMap[d.id] ?? authMap[d.label.toLowerCase()] ?? false,
    }))
    const connectedCount = dataSources.value.filter((d) => d.authorized).length
    stats.value.devices = connectedCount > 0 ? `${connectedCount} 台` : '0 台'
    if (connectedCount > 0) {
      success('设备状态已更新', `已连接 ${connectedCount} 个数据源。`)
    } else {
      info('暂无数据源', '您尚未授权任何第三方数据源。')
    }
  } catch (err) {
    error('加载失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    sourcesLoading.value = false
  }
}

const handleAuthorize = async (sourceId: string) => {
  authorizingSource.value = sourceId
  try {
    const result = await authorizeDataSource(sourceId)
    if (result.authorizationUrl) {
      window.open(result.authorizationUrl, '_blank', 'noopener')
      info('授权窗口已打开', '请在新窗口中完成授权，完成后回到此页面刷新设备状态。')
    } else {
      warning('授权失败', '未能获取授权链接，请稍后重试。')
    }
  } catch (err) {
    error('授权失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    authorizingSource.value = null
  }
}

const handleSync = async () => {
  syncing.value = true
  syncResult.value = null
  try {
    const result = await syncRookData()
    if (result.synced) {
      syncResult.value = result
      success('同步完成', '健康数据已更新。')
    } else {
      warning('同步失败', '未能获取最新数据，请稍后重试。')
    }
  } catch (err) {
    error('同步失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    syncing.value = false
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const triggerAvatar = () => avatarInput.value?.click()

const onAvatarChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error('文件类型不支持', '请选择图片文件作为头像。')
    input.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = async () => {
    const result = typeof reader.result === 'string' ? reader.result : ''
    if (!result) return

    try {
      await updateProfileAvatar(result)
      authStore.updateAvatar(result)
      success('头像已更新', '新的头像已保存。')
    } catch (err) {
      error('头像更新失败', err instanceof Error ? err.message : '请稍后重试')
    }
  }
  reader.readAsDataURL(file)
  input.value = ''
}

const openSettings = () => router.push('/profile/settings')
const exportData = () => router.push('/profile/export')
const openPermissions = () => router.push('/profile/permissions')
const openPrivacy = () => router.push('/profile/privacy')
const openDataSources = () => router.push('/profile/data-sources')
const openHelpFaq = () => router.push({ path: '/profile/help', query: { topic: '常见问题' } })
const openAbout = () => info('关于应用', '健康监测与分析平台 v1.0.0')

const clearCache = async () => {
  clearing.value = true
  await sleep(480)
  clearing.value = false
  success('缓存已清理', '本地缓存数据已刷新。')
}

const resetDeleteConfirm = () => {
  confirmDelete.value = false
  if (confirmTimer) {
    window.clearTimeout(confirmTimer)
    confirmTimer = null
  }
}

const dangerZone = async () => {
  if (!confirmDelete.value) {
    confirmDelete.value = true
    warning('请再次确认', '再次点击将提交删除申请。')
    if (confirmTimer) window.clearTimeout(confirmTimer)
    confirmTimer = window.setTimeout(() => {
      confirmDelete.value = false
      confirmTimer = null
    }, 3500)
    return
  }

  deleting.value = true
  await sleep(800)
  deleting.value = false
  resetDeleteConfirm()
  info('删除申请已记录', '当前仅为演示流程。')
}

const handleLogout = async () => {
  loggingOut.value = true
  await sleep(220)
  await authStore.logout()
  loggingOut.value = false
  success('已退出登录')
  router.replace('/auth/login')
}

const loadProfileSummary = async () => {
  try {
    stats.value = await getProfileSummary()
  } catch (err) {
    error('加载失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

onMounted(() => {
  void loadProfileSummary()
  void handleLoadSources()
})
</script>
