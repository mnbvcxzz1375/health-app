<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="个人设置"
      title="个人设置"
      description="统一维护基础档案、健康目标和提醒偏好。"
      :meta="`${selectedGoals.size} 项目标`"
      meta-label="当前关注"
    >
      <Button variant="secondary" @click="goBack">
        <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
        返回
      </Button>
    </ClinicalPageHeader>

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <ClinicalStatCard
        label="姓名"
        :value="form.name || '未填写'"
        :hint="form.email || '未绑定邮箱'"
        icon="solar:user-outline"
        tone="default"
      />
      <ClinicalStatCard
        label="身体数据"
        :value="`${form.height}cm / ${form.weight}kg`"
        :hint="`${form.age} 岁 · ${genderLabel}`"
        icon="solar:heart-pulse-outline"
        tone="info"
      />
      <ClinicalStatCard
        label="提醒偏好"
        :value="`${enabledReminderCount}/3`"
        hint="每日摘要、异常提醒、康复提醒"
        icon="solar:bell-outline"
        tone="success"
      />
    </section>

    <div class="grid gap-4 xl:grid-cols-[1fr_0.95fr]">
      <ClinicalSurfaceCard eyebrow="基础档案" title="基础信息" description="保持资料最新，建议才会更准确。">
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs text-slate-500">姓名</span>
            <input
              v-model="form.name"
              class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
            />
          </label>

          <label class="block">
            <span class="text-xs text-slate-500">邮箱</span>
            <input
              v-model="form.email"
              class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-slate-50 px-3 py-2.5 text-sm text-slate-500 outline-none"
              disabled
            />
          </label>

          <label class="block">
            <span class="text-xs text-slate-500">年龄</span>
            <input
              v-model.number="form.age"
              type="number"
              min="1"
              class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
            />
          </label>

          <label class="block">
            <span class="text-xs text-slate-500">性别</span>
            <AppSelect
              v-model="form.gender"
              class="mt-1"
              ariaLabel="性别"
              placeholder="请选择"
              :options="genderOptions"
            />
          </label>

          <label class="block">
            <span class="text-xs text-slate-500">身高 (cm)</span>
            <input
              v-model.number="form.height"
              type="number"
              min="80"
              class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
            />
          </label>

          <label class="block">
            <span class="text-xs text-slate-500">体重 (kg)</span>
            <input
              v-model.number="form.weight"
              type="number"
              min="30"
              class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
            />
          </label>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard eyebrow="健康目标" title="近期关注" description="这些目标会影响后续建议重点。">
        <label class="block">
          <span class="text-xs text-slate-500">近期关注</span>
          <textarea
            v-model="form.focus"
            rows="5"
            class="mt-1 w-full resize-none rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
            placeholder="例如：改善久坐带来的腰背不适"
          />
        </label>

        <div class="mt-4 flex flex-wrap gap-2">
          <button
            v-for="tag in goalOptions"
            :key="tag"
            type="button"
            class="rounded-full border px-3 py-1.5 text-xs transition"
            :class="
              selectedGoals.has(tag)
                ? 'border-teal-300 bg-teal-50 text-teal-900'
                : 'border-[color:var(--surface-border)] bg-white text-slate-600 hover:text-slate-900'
            "
            @click="toggleGoal(tag)"
          >
            {{ tag }}
          </button>
        </div>

        <p class="mt-4 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm leading-6 text-slate-600">
          目标越明确，系统越能把监测、上传和康复建议串成完整闭环。
        </p>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalSurfaceCard eyebrow="提醒规则" title="提醒偏好" description="按你的使用节奏控制提醒频率。">
      <div class="grid gap-3 lg:grid-cols-3">
        <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
          <div>
            <p class="text-sm font-semibold text-slate-950">每日摘要</p>
            <p class="mt-1 text-xs text-slate-500">每天晚间推送完成度和趋势变化</p>
          </div>
          <input v-model="form.dailySummary" type="checkbox" class="h-4 w-4" />
        </label>

        <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
          <div>
            <p class="text-sm font-semibold text-slate-950">异常提醒</p>
            <p class="mt-1 text-xs text-slate-500">出现异常波动时及时提醒</p>
          </div>
          <input v-model="form.riskAlert" type="checkbox" class="h-4 w-4" />
        </label>

        <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
          <div>
            <p class="text-sm font-semibold text-slate-950">康复提醒</p>
            <p class="mt-1 text-xs text-slate-500">按训练节奏提醒动作执行</p>
          </div>
          <input v-model="form.rehabReminder" type="checkbox" class="h-4 w-4" />
        </label>
      </div>
    </ClinicalSurfaceCard>

    <div class="grid grid-cols-2 gap-2.5">
      <Button variant="secondary" :disabled="saving" @click="handleReset">恢复上次保存</Button>
      <Button :loading="saving" @click="handleSave">保存设置</Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getProfileSettings, saveProfileSettings } from '@/api/modules/profile'
import { useToast } from '@/composables/useToast'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import AppSelect from '@/shared/components/ui/AppSelect.vue'
import Button from '@/shared/components/ui/Button.vue'
import { useAuthStore } from '@/stores/auth'

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

const router = useRouter()
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
const genderOptions: { label: string; value: SettingsForm['gender'] }[] = [
  { label: '男', value: 'male' },
  { label: '女', value: 'female' },
  { label: '其他', value: 'other' },
]

const selectedGoals = ref(new Set<string>())
const snapshot = ref('')
const saving = ref(false)

const enabledReminderCount = computed(() => [form.dailySummary, form.riskAlert, form.rehabReminder].filter(Boolean).length)
const genderLabel = computed(() => {
  if (form.gender === 'female') return '女'
  if (form.gender === 'other') return '其他'
  return '男'
})

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}

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
  info('已恢复到上次保存', '你可以继续调整后再保存。')
}

const handleSave = async () => {
  saving.value = true
  await sleep(260)

  const payload = {
    ...form,
    goals: Array.from(selectedGoals.value),
  }

  try {
    const saved = await saveProfileSettings(payload)
    Object.assign(form, saved)
    selectedGoals.value = new Set(saved.goals)
    authStore.updateProfile({ name: saved.name, email: saved.email })
    snapshot.value = buildSnapshot()
    success('设置已保存', '新的偏好会在后续建议中生效。')
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
