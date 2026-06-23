<template>
  <section class="relative min-h-dvh overflow-hidden bg-[linear-gradient(180deg,#eef4ff_0%,#eef6f1_52%,#f9fbfa_100%)] px-4 py-6 lg:px-8 lg:py-10">
    <div class="absolute inset-0 overflow-hidden">
      <div class="absolute left-[-8%] top-[-6%] h-52 w-52 rounded-full bg-[rgba(15,118,110,0.08)] blur-3xl lg:h-72 lg:w-72" />
      <div class="absolute right-[-6%] top-[20%] h-44 w-44 rounded-full bg-[rgba(59,130,246,0.08)] blur-3xl lg:h-64 lg:w-64" />
    </div>

    <div class="relative mx-auto flex min-h-[calc(100dvh-3rem)] w-full max-w-[1080px] items-center justify-center">
      <div class="mx-auto w-full max-w-[420px]">
        <div class="rounded-[2rem] border border-white/80 bg-white/92 p-5 shadow-[0_30px_90px_rgba(15,23,42,0.12)] backdrop-blur lg:p-7">
          <div class="rounded-[1.6rem] bg-[linear-gradient(180deg,#f9fbfb_0%,#eef4f1_100%)] px-5 py-5">
            <p class="text-center text-sm font-medium text-[color:var(--accent-strong)]">康复智伴</p>
          </div>

          <div class="mt-6 space-y-4">
            <label class="block">
              <span class="text-xs font-medium text-slate-500">邮箱</span>
              <input
                v-model="email"
                class="mt-1.5 w-full rounded-[1.15rem] border border-slate-200 bg-[#fbfcfc] px-4 py-3.5 text-sm outline-none transition focus:border-[color:var(--accent-strong)] focus:bg-white"
                type="email"
                placeholder="请输入邮箱"
              />
            </label>

            <label class="block">
              <span class="text-xs font-medium text-slate-500">密码</span>
              <input
                v-model="password"
                class="mt-1.5 w-full rounded-[1.15rem] border border-slate-200 bg-[#fbfcfc] px-4 py-3.5 text-sm outline-none transition focus:border-[color:var(--accent-strong)] focus:bg-white"
                type="password"
                placeholder="请输入密码"
              />
            </label>

            <Button class="mt-2 w-full" :loading="submitting" @click="handleLogin">登录</Button>
          </div>

          <div class="mt-5 rounded-[1.25rem] border border-[rgba(15,23,42,0.06)] bg-white px-4 py-4">
            <p class="text-center text-sm text-slate-600">
              还没有账号？
              <RouterLink class="font-semibold text-slate-950" to="/auth/register">立即注册</RouterLink>
            </p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import Button from '@/shared/components/ui/Button.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { success, error } = useToast()

const email = ref('')
const password = ref('')
const submitting = ref(false)

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

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
