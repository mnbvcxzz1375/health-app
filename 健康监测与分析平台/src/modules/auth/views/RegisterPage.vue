<template>
  <section class="relative min-h-dvh overflow-hidden bg-[linear-gradient(180deg,#eef4ff_0%,#eef6f1_52%,#f9fbfa_100%)] px-4 py-6 lg:px-8 lg:py-10">
    <div class="absolute inset-0 overflow-hidden">
      <div class="absolute left-[-8%] top-[-6%] h-52 w-52 rounded-full bg-[rgba(15,118,110,0.08)] blur-3xl lg:h-72 lg:w-72" />
      <div class="absolute right-[-6%] top-[20%] h-44 w-44 rounded-full bg-[rgba(59,130,246,0.08)] blur-3xl lg:h-64 lg:w-64" />
    </div>

    <div class="relative mx-auto flex min-h-[calc(100dvh-3rem)] w-full max-w-[1080px] items-center justify-center">
      <div class="grid w-full items-center gap-6 lg:grid-cols-[minmax(0,1fr)_440px] lg:gap-10">
        <div class="hidden lg:flex lg:justify-center">
          <div class="h-[620px] w-[360px] rounded-[3.2rem] border border-white/75 bg-white p-4 shadow-[0_30px_90px_rgba(15,23,42,0.14)]">
            <div class="flex h-full flex-col overflow-hidden rounded-[2.7rem] bg-[linear-gradient(180deg,#184e4b_0%,#113534_100%)] px-6 py-7 text-white">
              <p class="text-xs tracking-[0.28em] text-white/60">康复智伴</p>
              <h2 class="mt-5 text-[2.05rem] font-semibold leading-tight">注册后把你的报告、设备和提醒连起来</h2>

              <div class="mt-8 space-y-3">
                <div class="rounded-[1.5rem] border border-white/12 bg-white/10 px-4 py-4">
                  <p class="text-xs text-white/60">总览</p>
                  <p class="mt-2 text-lg font-semibold">查看已保存分析结果</p>
                </div>
                <div class="rounded-[1.5rem] border border-white/12 bg-white/10 px-4 py-4">
                  <p class="text-xs text-white/60">提醒</p>
                  <p class="mt-2 text-lg font-semibold">统一管理用药与训练</p>
                </div>
              </div>

              <div class="mt-auto rounded-[1.7rem] bg-white px-5 py-4 text-slate-950 shadow-[0_18px_48px_rgba(15,23,42,0.18)]">
                <p class="text-xs text-slate-400">注册完成后</p>
                <p class="mt-3 text-lg font-semibold">直接进入总览</p>
              </div>
            </div>
          </div>
        </div>

        <div class="mx-auto w-full max-w-[440px]">
          <div class="rounded-[2rem] border border-white/80 bg-white/92 p-5 shadow-[0_30px_90px_rgba(15,23,42,0.12)] backdrop-blur lg:p-7">
            <div class="rounded-[1.6rem] bg-[linear-gradient(180deg,#f9fbfb_0%,#eef4f1_100%)] px-5 py-5">
              <h1 class="text-center text-[2.55rem] font-semibold leading-none text-[color:var(--accent-strong)] lg:text-[2.8rem]">
                康复智伴
              </h1>
            </div>

            <div class="mt-6 space-y-4">
              <label class="block">
                <span class="text-xs font-medium text-slate-500">姓名</span>
                <input
                  v-model="name"
                  class="mt-1.5 w-full rounded-[1.15rem] border border-slate-200 bg-[#fbfcfc] px-4 py-3.5 text-sm outline-none transition focus:border-[color:var(--accent-strong)] focus:bg-white"
                  type="text"
                  placeholder="请输入姓名"
                />
              </label>

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
                  placeholder="至少 6 位密码"
                />
              </label>

              <label class="block">
                <span class="text-xs font-medium text-slate-500">确认密码</span>
                <input
                  v-model="confirmPassword"
                  class="mt-1.5 w-full rounded-[1.15rem] border border-slate-200 bg-[#fbfcfc] px-4 py-3.5 text-sm outline-none transition focus:border-[color:var(--accent-strong)] focus:bg-white"
                  type="password"
                  placeholder="再次输入密码"
                />
              </label>

              <Button class="mt-2 w-full" :loading="submitting" @click="handleRegister">注册</Button>
            </div>

            <div class="mt-5 rounded-[1.25rem] border border-[rgba(15,23,42,0.06)] bg-white px-4 py-4">
              <p class="text-center text-sm text-slate-600">
                已有账号？
                <RouterLink class="font-semibold text-slate-950" to="/auth/login">去登录</RouterLink>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import Button from '@/shared/components/ui/Button.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { success, error, warning } = useToast()

const name = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const handleRegister = async () => {
  const nextName = name.value.trim()
  const nextEmail = email.value.trim()
  const nextPassword = password.value.trim()
  const nextConfirm = confirmPassword.value.trim()

  if (!nextName || !nextEmail || !nextPassword || !nextConfirm) {
    error('请填写完整信息', '所有字段均为必填项。')
    return
  }
  if (nextPassword.length < 6) {
    warning('密码过短', '建议至少使用 6 位密码。')
    return
  }
  if (nextPassword !== nextConfirm) {
    error('两次密码不一致', '请重新确认密码输入。')
    return
  }

  submitting.value = true
  await sleep(180)
  try {
    await authStore.register({ name: nextName, email: nextEmail, password: nextPassword })
    success('注册成功', '已为你自动登录。')
    router.replace('/home')
  } catch (err) {
    error('注册失败', err instanceof Error ? err.message : '注册失败')
  } finally {
    submitting.value = false
  }
}
</script>
