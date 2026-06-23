<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="Security Review"
      title="数据安全"
      description="展示当前安全状态、关键策略和最近扫描结果，让安全能力从后台配置变成可见信息。"
      :meta="riskLevel"
      meta-label="风险等级"
    >
      <Button variant="secondary" @click="goBack">
        <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
        返回
      </Button>
    </ClinicalPageHeader>

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <ClinicalStatCard label="加密状态" value="已启用" hint="传输与静态加密开启" icon="solar:lock-keyhole-outline" tone="success" />
      <ClinicalStatCard label="最近登录" :value="lastLogin" hint="iPhone 15 Pro · 广州" icon="solar:smartphone-outline" tone="default" />
      <ClinicalStatCard label="风险评分" :value="`${riskScore}`" hint="建议每周扫描一次" icon="solar:shield-warning-outline" tone="warning" />
    </section>

    <div class="grid gap-4 xl:grid-cols-[0.95fr_1.05fr]">
      <ClinicalSurfaceCard
        eyebrow="Security Actions"
        title="安全操作"
        description="支持轮换访问密钥与执行扫描，保障设备访问和授权状态持续稳定。"
      >
        <div class="space-y-3">
          <div class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">轮换访问密钥</p>
              <p class="mt-1 text-xs text-slate-500">上次轮换：{{ lastRotate }}</p>
            </div>
            <Button variant="secondary" :loading="rotating" @click="rotateKeys">立即轮换</Button>
          </div>

          <div class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">执行安全扫描</p>
              <p class="mt-1 text-xs text-slate-500">检查异常登录与权限漂移</p>
            </div>
            <Button :loading="scanning" @click="runScan">开始扫描</Button>
          </div>

          <div v-if="scanning || scanProgress > 0" class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-white px-4 py-4">
            <div class="mb-2 flex items-center justify-between text-xs text-slate-500">
              <span>扫描进度</span>
              <Badge>{{ scanProgress }}%</Badge>
            </div>
            <div class="h-2 rounded-full bg-slate-100">
              <div class="h-full rounded-full bg-teal-600 transition-all" :style="{ width: `${scanProgress}%` }" />
            </div>
            <p class="mt-2 text-sm text-slate-600">{{ scanHint }}</p>
          </div>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard
        eyebrow="Protection Policy"
        title="防护策略"
        description="明确哪些策略已启用，哪些需要你主动打开，避免安全能力被忽略。"
      >
        <div class="space-y-3">
          <div class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">敏感操作二次确认</p>
              <p class="mt-1 text-xs text-slate-500">删除账户、导出数据等需要再次确认</p>
            </div>
            <Badge variant="success">已开启</Badge>
          </div>

          <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">登录验证增强</p>
              <p class="mt-1 text-xs text-slate-500">新设备登录时增加验证码确认</p>
            </div>
            <input v-model="loginShield" type="checkbox" class="h-4 w-4" />
          </label>

          <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">远程锁定</p>
              <p class="mt-1 text-xs text-slate-500">发现异常时可一键锁定设备访问</p>
            </div>
            <input v-model="remoteLock" type="checkbox" class="h-4 w-4" />
          </label>
        </div>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalSurfaceCard
      eyebrow="Scan Result"
      title="扫描结果"
      description="用可读摘要说明最近扫描发现，帮助你判断是否需要进一步处理。"
    >
      <div class="grid gap-3 lg:grid-cols-2">
        <article
          v-for="item in riskItems"
          :key="item.title"
          class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
        >
          <p class="text-sm font-semibold text-slate-950">{{ item.title }}</p>
          <p class="mt-2 text-sm leading-6 text-slate-600">{{ item.desc }}</p>
        </article>
      </div>
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

const router = useRouter()
const { success } = useToast()

const lastLogin = '今天 09:42'
const lastRotate = ref('2026-01-18 21:15')

const rotating = ref(false)
const scanning = ref(false)
const scanProgress = ref(0)
const riskScore = ref(18)

const loginShield = ref(true)
const remoteLock = ref(false)

let timer: number | null = null

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}

const clearTimer = () => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

const rotateKeys = async () => {
  rotating.value = true
  await sleep(650)
  lastRotate.value = new Date().toLocaleString('zh-CN', { hour12: false })
  rotating.value = false
  success('访问密钥已轮换', '旧密钥将在短时间内自动失效。')
}

const runScan = () => {
  clearTimer()
  scanning.value = true
  scanProgress.value = 8

  timer = window.setInterval(() => {
    scanProgress.value = Math.min(100, scanProgress.value + Math.round(10 + Math.random() * 14))
    if (scanProgress.value >= 100) {
      clearTimer()
      scanning.value = false
      riskScore.value = 16
      success('安全扫描完成', '未发现高风险问题。')
    }
  }, 380)
}

const scanHint = computed(() => {
  if (scanProgress.value < 35) return '正在校验设备指纹与登录轨迹…'
  if (scanProgress.value < 70) return '正在分析权限变化与关键操作…'
  return '正在生成风险摘要与建议…'
})

const riskLevel = computed(() => (riskScore.value <= 25 ? '低风险' : '需关注'))

const riskItems = computed(() => [
  { title: '登录轨迹稳定', desc: '近 7 天登录位置与设备一致，未发现异常漂移。' },
  { title: '权限配置正常', desc: loginShield.value ? '关键权限保持开启，建议继续保持。' : '建议开启登录验证增强策略。' },
  { title: '操作审计完整', desc: '导出、删除等关键操作均已记录审计日志。' },
  {
    title: remoteLock.value ? '远程锁定已开启' : '远程锁定未开启',
    desc: remoteLock.value ? '在异常情况下可快速锁定设备访问。' : '建议在公共设备较多时开启该策略。',
  },
])
</script>
