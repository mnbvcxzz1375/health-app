<template>
  <ProfileSubPage title="个人设置" subtitle="管理基础资料、健康目标和通知偏好">
    <form class="space-y-5" @submit.prevent="handleSave">
      <!-- 头像卡片 -->
      <section
        class="rounded-[19.2px] border p-4"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center gap-4">
          <div
            class="relative flex h-[72px] w-[72px] shrink-0 items-center justify-center overflow-hidden rounded-full"
            style="background: var(--brand-50);"
          >
            <img v-if="hasCustomAvatar" :src="avatarUrl" alt="用户头像" class="h-full w-full object-cover" />
            <span v-else class="text-[28px] font-semibold" style="color: var(--brand-500);">{{ userInitial }}</span>
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[17px] font-semibold" style="color: var(--foreground);">{{ userName }}</p>
            <p class="mt-0.5 truncate text-[14px]" style="color: var(--muted-foreground);">{{ userEmail || '未绑定邮箱' }}</p>
          </div>
          <button
            type="button"
            class="flex h-9 items-center gap-1 rounded-full px-4 text-[13px] font-medium transition active:scale-95"
            style="background: var(--secondary); color: var(--foreground);"
            @click="triggerAvatar"
          >
            <iconify-icon icon="solar:camera-outline" width="16" height="16" />
            更换
          </button>
        </div>
        <input ref="avatarInput" class="hidden" type="file" accept="image/*" @change="onAvatarChange" />
      </section>

      <!-- 基础信息 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">基础信息</h2>
        <div class="mt-4 space-y-4">
          <label class="block">
            <span class="text-[13px]" style="color: var(--muted-foreground);">昵称</span>
            <input
              v-model="form.name"
              type="text"
              class="mt-1.5 h-12 w-full rounded-[12px] border px-4 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
              style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
              placeholder="请输入昵称"
            />
          </label>
          <label class="block">
            <span class="text-[13px]" style="color: var(--muted-foreground);">邮箱</span>
            <input
              v-model="form.email"
              type="email"
              disabled
              class="mt-1.5 h-12 w-full rounded-[12px] border px-4 text-[15px] outline-none"
              style="background: var(--background-100); border-color: var(--border); color: var(--muted-foreground);"
            />
          </label>
          <div class="grid grid-cols-2 gap-3">
            <label class="block">
              <span class="text-[13px]" style="color: var(--muted-foreground);">年龄</span>
              <input
                v-model.number="form.age"
                type="number"
                min="1"
                class="mt-1.5 h-12 w-full rounded-[12px] border px-4 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
                style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[13px]" style="color: var(--muted-foreground);">性别</span>
              <select
                v-model="form.gender"
                class="mt-1.5 h-12 w-full rounded-[12px] border px-4 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
                style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
              >
                <option value="male">男</option>
                <option value="female">女</option>
                <option value="other">其他</option>
              </select>
            </label>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <label class="block">
              <span class="text-[13px]" style="color: var(--muted-foreground);">身高 (cm)</span>
              <input
                v-model.number="form.height"
                type="number"
                min="80"
                class="mt-1.5 h-12 w-full rounded-[12px] border px-4 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
                style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
              />
            </label>
            <label class="block">
              <span class="text-[13px]" style="color: var(--muted-foreground);">体重 (kg)</span>
              <input
                v-model.number="form.weight"
                type="number"
                min="30"
                class="mt-1.5 h-12 w-full rounded-[12px] border px-4 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
                style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
              />
            </label>
          </div>
        </div>
      </section>

      <!-- 健康目标 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">健康目标</h2>
        <textarea
          v-model="form.focus"
          rows="3"
          class="mt-3 w-full resize-none rounded-[12px] border p-3 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
          style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
          placeholder="例如：改善久坐带来的腰背不适"
        />
        <div class="mt-3 flex flex-wrap gap-2">
          <button
            v-for="tag in goalOptions"
            :key="tag"
            type="button"
            class="rounded-full px-3 py-1.5 text-[13px] font-medium transition active:scale-95"
            :style="selectedGoals.has(tag)
              ? { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }
              : { background: 'var(--secondary)', color: 'var(--foreground)', border: '1px solid var(--border)' }"
            @click="toggleGoal(tag)"
          >
            {{ tag }}
          </button>
        </div>
      </section>

      <!-- 通知偏好 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">通知偏好</h2>
        <div class="mt-3 space-y-2">
          <label
            v-for="item in reminderOptions"
            :key="item.key"
            class="flex items-center justify-between rounded-[12px] border px-4 py-3"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <div>
              <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ item.label }}</p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">{{ item.desc }}</p>
            </div>
            <input
              v-model="form[item.key as keyof SettingsForm]"
              type="checkbox"
              class="h-5 w-5 accent-[color:var(--brand-500)]"
            />
          </label>
        </div>
      </section>

      <!-- 操作按钮 -->
      <div class="flex gap-3 pt-2">
        <button
          type="button"
          class="flex h-[48px] flex-1 items-center justify-center rounded-full text-[15px] font-medium transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--foreground);"
          :disabled="saving"
          @click="handleReset"
        >
          重置
        </button>
        <button
          type="submit"
          class="flex h-[48px] flex-1 items-center justify-center rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
          style="background: var(--primary); color: var(--primary-foreground);"
          :disabled="saving"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </form>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getProfileSettings, saveProfileSettings, updateProfileAvatar } from '@/api/modules/profile'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import ProfileSubPage from '../components/ProfileSubPage.vue'

type SettingsForm = {
  name: string
  email: string
  age: number
  gender: 'male' | 'female' | 'other'
  height: number
  weight: number
  focus: string
  dailySummary: boolean
  riskAlert: boolean
  rehabReminder: boolean
}

type ReminderOption = {
  key: 'dailySummary' | 'riskAlert' | 'rehabReminder'
  label: string
  desc: string
}

const { success, warning, info } = useToast()
const authStore = useAuthStore()

const form = reactive<SettingsForm>({
  name: '',
  email: '',
  age: 28,
  gender: 'male',
  height: 170,
  weight: 65,
  focus: '',
  dailySummary: true,
  riskAlert: true,
  rehabReminder: true,
})

const goalOptions = ['姿势改善', '睡眠修复', '心率稳定', '减压恢复', '体重管理']
const reminderOptions: ReminderOption[] = [
  { key: 'dailySummary', label: '每日摘要', desc: '每天晚间推送完成度和趋势变化' },
  { key: 'riskAlert', label: '异常提醒', desc: '出现异常波动时及时通知' },
  { key: 'rehabReminder', label: '康复提醒', desc: '按训练节奏提醒动作执行' },
]

const selectedGoals = ref(new Set<string>())
const snapshot = ref('')
const saving = ref(false)

const avatarUrl = computed(() => authStore.avatarUrl)
const userName = computed(() => authStore.userName)
const userEmail = computed(() => authStore.user?.email ?? '')
const hasCustomAvatar = computed(() => {
  const url = authStore.user?.avatarUrl
  return Boolean(url) && !url!.startsWith('data:image/svg+xml')
})
const userInitial = computed(() => {
  const name = authStore.user?.name ?? ''
  return name.charAt(0) || '我'
})

const avatarInput = ref<HTMLInputElement | null>(null)
const triggerAvatar = () => avatarInput.value?.click()

const onAvatarChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    warning('文件类型不支持', '请选择图片文件。')
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
      success('头像已更新')
    } catch (err) {
      warning('头像更新失败', err instanceof Error ? err.message : '请稍后重试')
    }
  }
  reader.readAsDataURL(file)
  input.value = ''
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const toggleGoal = (tag: string) => {
  const next = new Set(selectedGoals.value)
  if (next.has(tag)) next.delete(tag)
  else next.add(tag)
  selectedGoals.value = next
}

const buildSnapshot = () => JSON.stringify({ form: { ...form }, goals: Array.from(selectedGoals.value) })

const loadSettings = async () => {
  try {
    const data = await getProfileSettings()
    Object.assign(form, data)
    selectedGoals.value = new Set(data.goals)
    authStore.updateProfile({ name: data.name, email: data.email })
    snapshot.value = buildSnapshot()
  } catch (err) {
    warning('加载失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const handleReset = () => {
  if (!snapshot.value) return
  const parsed = JSON.parse(snapshot.value) as { form: SettingsForm; goals: string[] }
  Object.assign(form, parsed.form)
  selectedGoals.value = new Set(parsed.goals)
  info('已恢复', '你可以继续调整后再保存。')
}

const handleSave = async () => {
  saving.value = true
  await sleep(260)
  const payload = { ...form, goals: Array.from(selectedGoals.value) }
  try {
    const saved = await saveProfileSettings(payload)
    Object.assign(form, saved)
    selectedGoals.value = new Set(saved.goals)
    authStore.updateProfile({ name: saved.name, email: saved.email })
    snapshot.value = buildSnapshot()
    success('设置已保存')
  } catch (err) {
    warning('保存失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>
