<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="Permission Hub"
      title="权限与授权"
      description="集中管理设备连接、健康数据、通知和相册等系统权限，保证关键能力可用且边界明确。"
      :meta="`${enabledCount}/${permissions.length} 已开启`"
      meta-label="授权状态"
    >
      <div class="flex flex-wrap gap-2">
        <Button variant="secondary" @click="goBack">
          <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
          返回
        </Button>
        <Button :loading="syncing" @click="syncPermissions">同步权限</Button>
      </div>
    </ClinicalPageHeader>

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <ClinicalStatCard label="已开启权限" :value="`${enabledCount}`" hint="建议保持关键权限开启" icon="solar:shield-check-outline" tone="success" />
      <ClinicalStatCard label="关键权限" :value="`${criticalCount}`" hint="设备连接与健康数据属于关键能力" icon="solar:key-outline" tone="warning" />
      <ClinicalStatCard label="可选权限" :value="`${permissions.length - criticalCount}`" hint="按使用场景按需开启" icon="solar:tuning-outline" tone="default" />
    </section>

    <ClinicalSurfaceCard
      eyebrow="Permission Matrix"
      title="当前权限清单"
      description="关闭关键权限会影响趋势分析、设备同步和康复提醒。"
    >
      <div class="space-y-3">
        <article
          v-for="item in permissions"
          :key="item.key"
          class="flex items-center justify-between gap-3 rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
        >
          <div>
            <div class="flex items-center gap-2">
              <p class="text-sm font-semibold text-slate-950">{{ item.title }}</p>
              <Badge v-if="item.critical" variant="warning">关键</Badge>
            </div>
            <p class="mt-1 text-sm leading-6 text-slate-600">{{ item.desc }}</p>
          </div>
          <input :checked="item.enabled" type="checkbox" class="h-4 w-4" @change="togglePermission(item.key)" />
        </article>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard
      eyebrow="Recommendations"
      title="授权建议"
      description="权限不仅影响功能可见性，也影响系统判断风险和推送时机。"
    >
      <ul class="list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
        <li>健康数据与通知权限建议保持开启，便于及时提醒与趋势分析。</li>
        <li>若使用公共设备，建议关闭相册读取权限并及时退出登录。</li>
        <li>系统升级后可重新同步一次权限，避免功能受限。</li>
      </ul>
    </ClinicalSurfaceCard>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'

type PermissionKey = 'device' | 'album' | 'notify' | 'health' | 'location'

type PermissionItem = {
  key: PermissionKey
  title: string
  desc: string
  enabled: boolean
  critical?: boolean
}

const router = useRouter()
const { success, warning, info } = useToast()

const permissions = ref<PermissionItem[]>([
  { key: 'device', title: '设备连接', desc: '读取 Apple Watch 或其他健康设备的监测数据。', enabled: true, critical: true },
  { key: 'health', title: '健康数据', desc: 'Apple Health 用于生成趋势分析与风险提示。', enabled: true, critical: true },
  { key: 'notify', title: '系统通知', desc: '用于提醒计划与异常变化。', enabled: true },
  { key: 'album', title: '相册读取', desc: '用于上传影像与报告图片。', enabled: false },
  { key: 'location', title: '位置信息', desc: '用于安全审计与登录保护（可选）。', enabled: false },
])

const syncing = ref(false)

const enabledCount = computed(() => permissions.value.filter((item) => item.enabled).length)
const criticalCount = computed(() => permissions.value.filter((item) => item.critical).length)

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}

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
