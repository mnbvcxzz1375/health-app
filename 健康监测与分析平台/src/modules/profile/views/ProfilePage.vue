<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader title="我的" />

    <ClinicalSurfaceCard title="账户概览">
      <div class="flex items-center gap-3">
        <button class="relative h-16 w-16 overflow-hidden rounded-full border border-[color:var(--surface-border)]" type="button" @click="triggerAvatar">
          <img :src="avatarUrl" alt="用户头像" class="h-full w-full object-cover" />
        </button>
        <input ref="avatarInput" class="hidden" type="file" accept="image/*" @change="onAvatarChange" />

        <div class="min-w-0 flex-1">
          <p class="truncate text-base font-semibold text-slate-950">{{ userName }}</p>
          <p class="truncate text-sm text-slate-600">{{ userEmail }}</p>
        </div>

        <Badge variant="success">已验证</Badge>
      </div>

      <div class="mt-4 grid grid-cols-3 gap-3">
        <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-3">
          <p class="text-xs text-slate-500">设备</p>
          <p class="mt-2 text-sm font-semibold text-slate-950">{{ stats.devices }}</p>
        </div>
        <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-3">
          <p class="text-xs text-slate-500">上传资料</p>
          <p class="mt-2 text-sm font-semibold text-slate-950">{{ stats.uploads }}</p>
        </div>
        <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-3">
          <p class="text-xs text-slate-500">风险评分</p>
          <p class="mt-2 text-sm font-semibold text-slate-950">{{ displayRiskScore }}</p>
        </div>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <Button @click="openSettings">个人设置</Button>
        <Button variant="secondary" @click="exportData">导出数据</Button>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="健康数据同步">
      <p class="text-sm text-slate-600">从已连接设备同步最新健康数据。</p>

      <div class="mt-3">
        <Button :loading="syncing" @click="handleSync">
          <template v-if="!syncing" #leading>
            <iconify-icon icon="solar:refresh-circle-bold-duotone" width="18" height="18" />
          </template>
          同步健康数据
        </Button>
      </div>

      <div v-if="syncResult" class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <ClinicalStatCard label="心率" :value="`${syncResult.hr} bpm`" icon="solar:heart-pulse-bold-duotone" tone="danger" />
        <ClinicalStatCard label="睡眠评分" :value="String(syncResult.sleepScore)" icon="solar:moon-sleep-bold-duotone" tone="info" />
        <ClinicalStatCard label="压力评分" :value="String(syncResult.stressScore)" icon="solar:danger-bold-duotone" tone="warning" />
        <ClinicalStatCard label="HRV" :value="`${syncResult.hrv} ms`" icon="solar:pulse-2-bold-duotone" tone="success" />
        <ClinicalStatCard label="步数" :value="String(syncResult.steps)" icon="solar:walking-round-bold-duotone" />
        <ClinicalStatCard label="VO2 Max" :value="`${syncResult.vo2Max} ml/kg`" icon="solar:lungs-bold-duotone" tone="info" />
        <ClinicalStatCard label="深度睡眠" :value="`${syncResult.deepSleepHours} h`" icon="solar:moon-stars-bold-duotone" tone="default" />
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="健康设备">
      <p class="text-sm text-slate-600">连接健康设备数据源，获取更全面的健康数据。</p>

      <div class="mt-3 flex flex-wrap gap-2">
        <Button :loading="sourcesLoading" @click="handleLoadSources">
          <template v-if="!sourcesLoading" #leading>
            <iconify-icon icon="solar:devices-bold-duotone" width="18" height="18" />
          </template>
          刷新设备状态
        </Button>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="ds in dataSources"
          :key="ds.id"
          class="flex items-center gap-3 rounded-[1.4rem] border px-4 py-3.5 shadow-[var(--elevation-soft)]"
          :class="ds.authorized
            ? 'border-emerald-300 bg-emerald-50/60'
            : 'border-[color:var(--surface-border)] bg-[color:var(--surface-primary)]'"
        >
          <iconify-icon :icon="ds.icon" width="28" height="28" class="shrink-0 text-slate-700" />

          <div class="min-w-0 flex-1">
            <p class="text-sm font-semibold text-slate-950">{{ ds.label }}</p>
            <p class="text-xs" :class="ds.authorized ? 'text-emerald-600' : 'text-slate-500'">
              {{ ds.authorized ? '已连接' : '未连接' }}
            </p>
          </div>

          <Button
            :variant="ds.authorized ? 'secondary' : 'primary'"
            :loading="authorizingSource === ds.id"
            @click="handleAuthorize(ds.id)"
          >
            {{ ds.authorized ? '重新授权' : '连接' }}
          </Button>
        </div>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="数据与隐私">
      <div class="space-y-2.5">
        <ActionRow title="数据安全" icon="Database" @click="openSecurity" />
        <ActionRow title="权限与授权" icon="Shield" @click="openPermissions" />
        <ActionRow title="隐私中心" icon="FileText" @click="openPrivacy" />
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="帮助与支持">
      <div class="space-y-2.5">
        <ActionRow title="使用指南" icon="HelpCircle" @click="openHelpGuide" />
        <ActionRow title="常见问题" icon="HelpCircle" @click="openHelpFaq" />
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="账户操作">
      <div class="space-y-2.5">
        <Button variant="secondary" class="w-full" :loading="clearing" @click="clearCache">清理缓存</Button>
        <Button variant="danger" class="w-full" :loading="deleting" @click="dangerZone">
          {{ confirmDelete ? '确认删除账户' : '删除账户' }}
        </Button>
        <Button variant="ghost" class="w-full" :loading="loggingOut" @click="handleLogout">退出登录</Button>
        <p v-if="confirmDelete" class="text-sm text-slate-700">再次点击将提交删除申请。</p>
      </div>
    </ClinicalSurfaceCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { syncRookData, authorizeDataSource, getAuthorizedSources, type RookSyncResult } from '@/api/modules/rook'
import { getProfileSummary, updateProfileAvatar } from '@/api/modules/profile'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import ActionRow from '@/shared/components/ActionRow.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'

const router = useRouter()
const authStore = useAuthStore()
const { success, warning, info, error } = useToast()

const stats = ref({
  devices: '加载中',
  uploads: '加载中',
  riskScore: '加载中',
})

const userName = computed(() => authStore.userName)
const userEmail = computed(() => authStore.user?.email ?? '')
const avatarUrl = computed(() => authStore.avatarUrl)
const displayRiskScore = computed(() =>
  stats.value.riskScore
    .replace(/\blow\b/gi, '低风险')
    .replace(/\bmedium\b/gi, '中风险')
    .replace(/\bhigh\b/gi, '高风险'),
)

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
      info('暂无设备', '您尚未连接任何健康设备。')
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
const openSecurity = () => router.push('/profile/security')
const openPermissions = () => router.push('/profile/permissions')
const openPrivacy = () => router.push('/profile/privacy')
const openHelpGuide = () => router.push({ path: '/profile/help', query: { topic: '使用指南' } })
const openHelpFaq = () => router.push({ path: '/profile/help', query: { topic: '常见问题' } })

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
