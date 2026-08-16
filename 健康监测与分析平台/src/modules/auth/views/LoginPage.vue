<template>
  <main
    class="flex min-h-dvh flex-col items-center px-6 pb-12 pt-[120px]"
    style="background: var(--background);"
  >
    <div class="flex w-full max-w-[400px] flex-col items-center">
      <!-- Brand Logo Area -->
      <div class="flex flex-col items-center">
        <div
          class="flex items-center justify-center rounded-full"
          style="width: 72px; height: 72px; background: var(--brand-500); color: var(--primary-foreground);"
        >
          <iconify-icon icon="solar:heart-pulse-bold-duotone" width="36" height="36" />
        </div>
        <h1
          class="mt-4 text-center text-[24px] font-semibold tracking-[-0.02em]"
          style="color: var(--foreground);"
        >健康监测</h1>
        <p class="mt-1 text-center text-[14px]" style="color: var(--muted-foreground);">守护您的每一天</p>
      </div>

      <!-- Form Card -->
      <div
        class="mt-12 w-full rounded-[19.2px] border p-6"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-sm);"
      >
        <!-- Email Input Group -->
        <div>
          <label
            for="email-input"
            class="mb-1.5 block text-[13px] font-medium"
            style="color: var(--muted-foreground);"
          >邮箱</label>
          <input
            id="email-input"
            v-model="email"
            type="email"
            placeholder="请输入邮箱地址"
            class="w-full rounded-[12px] border outline-none transition placeholder:opacity-100 focus:border-[color:var(--ring)] focus:outline-none focus:ring-2 focus:ring-[color:var(--ring)]"
            style="height: 48px; background: var(--background-50); border-color: var(--border); padding: 0 16px; font-size: 15px; color: var(--foreground);"
          />
        </div>

        <!-- Password Input Group -->
        <div class="mt-4">
          <label
            for="password-input"
            class="mb-1.5 block text-[13px] font-medium"
            style="color: var(--muted-foreground);"
          >密码</label>
          <div class="relative">
            <input
              id="password-input"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              class="w-full rounded-[12px] border outline-none transition placeholder:opacity-100 focus:border-[color:var(--ring)] focus:outline-none focus:ring-2 focus:ring-[color:var(--ring)]"
              style="height: 48px; background: var(--background-50); border-color: var(--border); padding: 0 48px 0 16px; font-size: 15px; color: var(--foreground);"
              @keyup.enter="handleLogin"
            />
            <button
              type="button"
              class="absolute flex items-center justify-center"
              style="right: 12px; top: 50%; transform: translateY(-50%); width: 44px; height: 44px; color: var(--muted-foreground);"
              aria-label="显示或隐藏密码"
              @click="showPassword = !showPassword"
            >
              <iconify-icon
                :icon="showPassword ? 'solar:eye-closed-outline' : 'solar:eye-outline'"
                width="20"
                height="20"
              />
            </button>
          </div>
        </div>
      </div>

      <!-- Login Button -->
      <button
        type="button"
        class="mt-6 flex w-full items-center justify-center rounded-full transition active:scale-[0.98] disabled:opacity-60"
        style="background: var(--primary); color: var(--primary-foreground); height: 52px; font-size: 17px; font-weight: 600;"
        :disabled="submitting"
        @click="handleLogin"
      >
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <!-- Secondary Links -->
      <div class="mt-5 flex w-full justify-between">
        <button
          type="button"
          class="text-[14px] transition active:opacity-70"
          style="color: var(--brand-500);"
          @click="forgotPassword"
        >忘记密码?</button>
        <RouterLink
          class="text-[14px] transition active:opacity-70"
          style="color: var(--brand-500);"
          to="/auth/register"
        >注册账号</RouterLink>
      </div>

      <!-- Footer Text -->
      <p
        class="mt-12 text-center text-[12px] leading-relaxed"
        style="color: var(--muted-foreground);"
      >登录即代表您同意《用户协议》和《隐私政策》</p>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { success, error, warning } = useToast()

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const forgotPassword = () => {
  warning('请联系管理员重置密码', '当前版本暂不支持自助找回。')
}

const handleLogin = async () => {
  const nextEmail = email.value.trim()
  const nextPassword = password.value.trim()
  if (!nextEmail || !nextPassword) {
    error('请填写完整信息', '邮箱和密码均为必填项。')
    return
  }

  submitting.value = true
  await sleep(160)
  try {
    await authStore.login({ email: nextEmail, password: nextPassword })
    success('登录成功', '欢迎回来。')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.replace(redirect)
  } catch (err) {
    error('登录失败', err instanceof Error ? err.message : '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>
