<template>
  <ProfileSubPage title="隐私与安全" subtitle="管理账户安全策略与数据保护">
    <div class="space-y-5">
      <!-- Status Card -->
      <section
        class="rounded-[19.2px] border p-5"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center gap-3">
          <div
            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full"
            style="background: var(--state-success-surface); color: var(--state-success);"
          >
            <iconify-icon icon="solar:shield-check-outline" width="24" height="24" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[17px] font-semibold" style="color: var(--foreground);">安全状态良好</p>
            <p class="mt-0.5 text-[14px]" style="color: var(--muted-foreground);">风险等级：{{ riskLevel }} · 加密已启用</p>
          </div>
        </div>
        <div class="mt-4 grid grid-cols-2 gap-3">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">最近登录</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">今天 09:42</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">密钥轮换</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ lastRotate }}</p>
          </div>
        </div>
      </section>

      <!-- Security Actions -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">安全操作</h2>
        <div class="mt-3 space-y-2">
          <button
            type="button"
            class="flex w-full items-center justify-between rounded-[12px] border px-4 py-3 text-left transition active:scale-[0.99]"
            style="background: var(--secondary); border-color: var(--border);"
            :disabled="rotating"
            @click="rotateKeys"
          >
            <div class="flex items-center gap-3">
              <div class="flex h-8 w-8 items-center justify-center rounded-full" style="background: var(--card);">
                <iconify-icon icon="solar:key-minimalistic-square-3-outline" width="16" height="16" style="color: var(--brand-500);" />
              </div>
              <div>
                <p class="text-[15px] font-medium" style="color: var(--foreground);">轮换访问密钥</p>
                <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">旧密钥将自动失效</p>
              </div>
            </div>
            <span v-if="rotating" class="text-[13px]" style="color: var(--muted-foreground);">处理中…</span>
            <iconify-icon v-else icon="solar:refresh-circle-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>

          <button
            type="button"
            class="flex w-full items-center justify-between rounded-[12px] border px-4 py-3 text-left transition active:scale-[0.99]"
            style="background: var(--secondary); border-color: var(--border);"
            :disabled="scanning"
            @click="runScan"
          >
            <div class="flex items-center gap-3">
              <div class="flex h-8 w-8 items-center justify-center rounded-full" style="background: var(--card);">
                <iconify-icon icon="solar:scanner-2-outline" width="16" height="16" style="color: var(--brand-500);" />
              </div>
              <div>
                <p class="text-[15px] font-medium" style="color: var(--foreground);">执行安全扫描</p>
                <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">检查异常登录与权限漂移</p>
              </div>
            </div>
            <span v-if="scanning" class="text-[13px]" style="color: var(--muted-foreground);">扫描中…</span>
            <iconify-icon v-else icon="solar:play-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          </button>
        </div>

        <!-- Scan progress -->
        <div
          v-if="scanning || scanProgress > 0"
          class="mt-3 rounded-[12px] border p-4"
          style="background: var(--secondary); border-color: var(--border);"
        >
          <div class="mb-2 flex items-center justify-between text-[13px]">
            <span style="color: var(--muted-foreground);">扫描进度</span>
            <span class="font-semibold" style="color: var(--foreground);">{{ scanProgress }}%</span>
          </div>
          <div class="w-full overflow-hidden rounded-full" style="height: 6px; background: var(--background-200);">
            <div style="height: 100%; border-radius: 9999px; transition: width 0.3s ease;" :style="{ width: `${scanProgress}%`, background: 'var(--state-success)' }"></div>
          </div>
          <p class="mt-2 text-[13px]" style="color: var(--muted-foreground);">{{ scanHint }}</p>
        </div>
      </section>

      <!-- Protection Settings -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">保护策略</h2>
        <div class="mt-3 space-y-2">
          <label
            v-for="item in shields"
            :key="item.key"
            class="flex items-center justify-between rounded-[12px] border px-4 py-3"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <div>
              <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ item.label }}</p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">{{ item.desc }}</p>
            </div>
            <input v-model="item.enabled" type="checkbox" class="h-5 w-5 accent-[color:var(--brand-500)]" />
          </label>
        </div>
      </section>

      <!-- Scan Results -->
      <section
        v-if="riskItems.length"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">扫描结果</h2>
        <div class="mt-3 space-y-2">
          <div
            v-for="item in riskItems"
            :key="item.title"
            class="rounded-[12px] border p-3"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <p class="text-[14px] font-medium" style="color: var(--foreground);">{{ item.title }}</p>
            <p class="mt-1 text-[13px] leading-5" style="color: var(--muted-foreground);">{{ item.desc }}</p>
          </div>
        </div>
      </section>
    </div>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from '@/composables/useToast'
import ProfileSubPage from '../components/ProfileSubPage.vue'

const { success } = useToast()

const lastRotate = ref('2026-01-18 21:15')
const rotating = ref(false)
const scanning = ref(false)
const scanProgress = ref(0)
const riskScore = ref(18)

const shields = ref([
  { key: 'confirm', label: '敏感操作二次确认', desc: '删除账户、导出数据等需要再次确认', enabled: true },
  { key: 'login', label: '登录验证增强', desc: '新设备登录时增加验证码确认', enabled: true },
  { key: 'lock', label: '远程锁定', desc: '发现异常时可一键锁定设备访问', enabled: false },
])

let timer: number | null = null

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

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
  { title: '权限配置正常', desc: shields.value[1].enabled ? '关键权限保持开启，建议继续保持。' : '建议开启登录验证增强策略。' },
  { title: '操作审计完整', desc: '导出、删除等关键操作均已记录审计日志。' },
  {
    title: shields.value[2].enabled ? '远程锁定已开启' : '远程锁定未开启',
    desc: shields.value[2].enabled ? '在异常情况下可快速锁定设备访问。' : '建议在公共设备较多时开启该策略。',
  },
])
</script>
