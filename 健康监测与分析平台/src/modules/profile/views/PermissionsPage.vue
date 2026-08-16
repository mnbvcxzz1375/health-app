<template>
  <ProfileSubPage title="权限与授权" subtitle="管理系统权限，保证关键健康能力可用且边界明确">
    <div class="space-y-5">
      <!-- 状态概览 -->
      <section
        class="rounded-[19.2px] border p-5"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center gap-3">
          <div
            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full"
            style="background: var(--brand-50); color: var(--brand-500);"
          >
            <iconify-icon icon="solar:keyhole-square-outline" width="24" height="24" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[17px] font-semibold" style="color: var(--foreground);">{{ enabledCount }} / {{ permissions.length }} 已开启</p>
            <p class="mt-0.5 text-[14px]" style="color: var(--muted-foreground);">关键权限 {{ criticalCount }} 项，建议保持开启</p>
          </div>
        </div>
        <div class="mt-4 grid grid-cols-3 gap-3">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">已开启</p>
            <p class="mt-1 text-[17px] font-semibold tabular-nums" style="color: var(--foreground);">{{ enabledCount }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">关键权限</p>
            <p class="mt-1 text-[17px] font-semibold tabular-nums" style="color: var(--foreground);">{{ criticalCount }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">可选权限</p>
            <p class="mt-1 text-[17px] font-semibold tabular-nums" style="color: var(--foreground);">{{ permissions.length - criticalCount }}</p>
          </div>
        </div>
      </section>

      <!-- 权限清单 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">权限清单</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">关闭关键权限可能影响趋势分析、设备同步与提醒。</p>
        <div class="mt-3 space-y-2">
          <label
            v-for="item in permissions"
            :key="item.key"
            class="flex items-center justify-between gap-3 rounded-[12px] border px-4 py-3 transition active:scale-[0.99]"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <div class="flex min-w-0 items-center gap-3">
              <div
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
                :style="{ background: item.iconBg, color: item.iconColor }"
              >
                <iconify-icon :icon="item.icon" width="18" height="18" />
              </div>
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ item.title }}</p>
                  <span
                    v-if="item.critical"
                    class="rounded-full px-2 py-0.5 text-[10px] font-medium"
                    style="background: color-mix(in srgb, var(--chart-3) 12%, var(--card)); color: var(--chart-3);"
                  >关键</span>
                </div>
                <p class="mt-0.5 text-[12px] leading-4" style="color: var(--muted-foreground);">{{ item.desc }}</p>
              </div>
            </div>
            <input
              :checked="item.enabled"
              type="checkbox"
              class="h-5 w-5 shrink-0 accent-[color:var(--brand-500)]"
              @change="togglePermission(item.key)"
            />
          </label>
        </div>
      </section>

      <!-- 授权建议 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">授权建议</h2>
        <ul class="mt-3 space-y-2 text-[13px] leading-5" style="color: var(--muted-foreground);">
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>健康数据与通知权限建议保持开启，便于及时提醒与趋势分析。</span>
          </li>
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>若使用公共设备，建议关闭相册读取权限并及时退出登录。</span>
          </li>
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>系统升级后可重新同步一次权限，避免功能受限。</span>
          </li>
        </ul>
        <button
          type="button"
          class="mt-4 flex h-[48px] w-full items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
          style="background: var(--primary); color: var(--primary-foreground);"
          :disabled="syncing"
          @click="syncPermissions"
        >
          <iconify-icon v-if="syncing" icon="solar:refresh-outline" width="18" height="18" class="animate-spin" />
          <iconify-icon v-else icon="solar:refresh-circle-outline" width="18" height="18" />
          {{ syncing ? '同步中…' : '同步权限状态' }}
        </button>
      </section>
    </div>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from '@/composables/useToast'
import ProfileSubPage from '../components/ProfileSubPage.vue'

type PermissionKey = 'device' | 'album' | 'notify' | 'health' | 'location'

type PermissionItem = {
  key: PermissionKey
  title: string
  desc: string
  enabled: boolean
  critical?: boolean
  icon: string
  iconBg: string
  iconColor: string
}

const { success, warning, info } = useToast()

const permissions = ref<PermissionItem[]>([
  {
    key: 'device',
    title: '设备连接',
    desc: '读取 Apple Watch 等健康设备的监测数据。',
    enabled: true,
    critical: true,
    icon: 'solar:watch-square-outline',
    iconBg: 'var(--brand-50)',
    iconColor: 'var(--brand-500)',
  },
  {
    key: 'health',
    title: '健康数据',
    desc: 'Apple Health 用于生成趋势分析与风险提示。',
    enabled: true,
    critical: true,
    icon: 'solar:heart-pulse-outline',
    iconBg: 'var(--state-error-surface)',
    iconColor: 'var(--state-error)',
  },
  {
    key: 'notify',
    title: '系统通知',
    desc: '用于提醒计划与异常变化。',
    enabled: true,
    icon: 'solar:bell-bing-outline',
    iconBg: 'color-mix(in srgb, var(--chart-3) 12%, var(--card))',
    iconColor: 'var(--chart-3)',
  },
  {
    key: 'album',
    title: '相册读取',
    desc: '用于上传影像与报告图片。',
    enabled: false,
    icon: 'solar:gallery-outline',
    iconBg: 'var(--background-200)',
    iconColor: 'var(--foreground)',
  },
  {
    key: 'location',
    title: '位置信息',
    desc: '用于安全审计与登录保护（可选）。',
    enabled: false,
    icon: 'solar:map-point-outline',
    iconBg: 'var(--state-success-surface)',
    iconColor: 'var(--state-success)',
  },
])

const syncing = ref(false)

const enabledCount = computed(() => permissions.value.filter((item) => item.enabled).length)
const criticalCount = computed(() => permissions.value.filter((item) => item.critical).length)

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const togglePermission = (key: PermissionKey) => {
  const target = permissions.value.find((item) => item.key === key)
  if (!target) return

  target.enabled = !target.enabled

  if (target.critical && !target.enabled) {
    warning('已关闭关键权限', `${target.title} 关闭后可能影响核心功能。`)
    return
  }

  if (target.enabled) success('权限已开启', `${target.title} 可用于提供完整体验。`)
  else info('权限已关闭', `${target.title} 已关闭，必要时可再次开启。`)
}

const syncPermissions = async () => {
  syncing.value = true
  await sleep(650)
  syncing.value = false
  success('权限已同步', '系统授权状态已更新。')
}
</script>
