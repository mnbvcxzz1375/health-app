<template>
  <div class="apple-monitor flex min-h-screen flex-col items-center justify-center px-6 pb-6">
    <div
      class="w-full max-w-[420px] rounded-[19.2px] p-8 text-center"
      style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
    >
      <!-- Loading -->
      <template v-if="status === 'loading'">
        <iconify-icon icon="solar:refresh-outline" width="40" height="40" class="animate-spin mx-auto" style="color: var(--brand-500);" />
        <h1 class="mt-4 text-[20px] font-semibold" style="color: var(--foreground);">正在绑定 {{ providerLabel }}...</h1>
        <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">请稍候，正在交换授权码</p>
      </template>

      <!-- Success -->
      <template v-else-if="status === 'success'">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full" style="background: rgba(16,185,129,0.12);">
          <iconify-icon icon="solar:check-circle-bold" width="40" height="40" style="color: #10b981;" />
        </div>
        <h1 class="mt-4 text-[20px] font-semibold" style="color: var(--foreground);">绑定成功</h1>
        <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">{{ message }}</p>
        <button
          type="button"
          class="mt-5 h-11 w-full rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
          style="background: var(--brand-500); color: var(--primary-foreground);"
          @click="router.replace('/devices')"
        >返回设备管理</button>
      </template>

      <!-- Error -->
      <template v-else>
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full" style="background: rgba(239,68,68,0.12);">
          <iconify-icon icon="solar:danger-triangle-bold" width="40" height="40" style="color: var(--destructive);" />
        </div>
        <h1 class="mt-4 text-[20px] font-semibold" style="color: var(--foreground);">绑定失败</h1>
        <p class="mt-1 text-[13px] break-words" style="color: var(--muted-foreground);">{{ message }}</p>
        <div class="mt-5 space-y-2">
          <button
            type="button"
            class="h-11 w-full rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            @click="router.replace('/devices/brands')"
          >返回品牌列表</button>
          <button
            type="button"
            class="h-11 w-full rounded-full text-[14px] font-medium transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            @click="router.replace('/devices')"
          >返回设备管理</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { handleOAuthCallback } from '@/api/modules/deviceAggregation'

const router = useRouter()
const route = useRoute()

const provider = computed(() => String(route.params.provider ?? ''))
const providerLabel = computed(() => {
  const map: Record<string, string> = {
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
  }
  return map[provider.value] ?? provider.value
})

const status = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('')

onMounted(async () => {
  const code = route.query.code
  const state = route.query.state

  if (!code || typeof code !== 'string') {
    status.value = 'error'
    message.value = '未收到授权码（code 参数缺失）'
    return
  }

  try {
    const result = await handleOAuthCallback(
      provider.value,
      code,
      typeof state === 'string' ? state : '',
    )
    if (result.success) {
      status.value = 'success'
      message.value = result.message
      // 3 秒后自动跳转
      setTimeout(() => {
        void router.replace('/devices')
      }, 3000)
    } else {
      status.value = 'error'
      message.value = result.message
    }
  } catch (e: unknown) {
    status.value = 'error'
    message.value = (e as Error)?.message ?? '未知错误'
  }
})
</script>
