<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalFeatureNavBar title="康复训练" back-to="/home" />
    <ClinicalPageHeader
      eyebrow="Rehab Program"
      title="康复训练"
      description="围绕今日动作执行、视频纠错、频次设置和周趋势，形成可落地的居家康复闭环。"
      :meta="`${doneCount}/${todayExercises.length || 0} 项完成`"
      meta-label="今日进度"
    >
      <div class="flex flex-wrap items-center gap-2">
        <Button variant="secondary" @click="openPlanSettings">
          <iconify-icon icon="solar:settings-outline" width="16" height="16" />
          调整计划
        </Button>
        <Button variant="secondary" @click="goReminderSettings">
          <iconify-icon icon="solar:bell-outline" width="16" height="16" />
          设置提醒
        </Button>
      </div>
    </ClinicalPageHeader>

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <ClinicalStatCard
        label="今日完成度"
        :value="`${doneCount}/${todayExercises.length || 0}`"
        :hint="`建议总时长 ${totalMinutes} 分钟`"
        icon="solar:check-circle-outline"
        tone="success"
      />
      <ClinicalStatCard
        label="执行比例"
        :value="`${progress}%`"
        :hint="planSummary.frequency"
        icon="solar:pulse-2-outline"
        tone="info"
      />
      <ClinicalStatCard
        label="提醒状态"
        :value="reminderSummary.status"
        :hint="`${reminderSummary.time} · ${reminderSummary.days}`"
        icon="solar:alarm-outline"
        tone="default"
      />
    </section>

    <ClinicalSurfaceCard
      eyebrow="Video Review"
      title="动作拍摄纠错"
      description="录制居家训练动作，系统将返回动作评分、问题段落与分段修正建议。"
    >
      <div class="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
        <div class="space-y-4">
          <div class="overflow-hidden rounded-[1.4rem] border border-[color:var(--surface-border)] bg-slate-950">
            <video
              v-if="recordState === 'recorded' && recordedUrl"
              :src="recordedUrl"
              class="h-60 w-full object-cover"
              controls
              playsinline
            />
            <video
              v-else
              ref="cameraVideoRef"
              class="h-60 w-full object-cover"
              autoplay
              muted
              playsinline
            />
          </div>

          <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-sm font-semibold text-slate-950">录制状态</p>
            <p class="mt-1 text-sm leading-6 text-slate-600">
              {{ cameraStatusText }}
              <span v-if="cameraError" class="text-rose-700"> · {{ cameraError }}</span>
            </p>
          </div>
        </div>

        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-2.5">
            <Button variant="secondary" :disabled="recordState === 'recording'" @click="requestCamera">授权相机</Button>
            <Button :disabled="recordState === 'recording' || !isCameraReady" @click="startRecord">开始录制</Button>
            <Button variant="ghost" :disabled="recordState !== 'recording'" @click="stopRecord">停止录制</Button>
            <Button variant="secondary" :disabled="recordState !== 'recorded'" @click="resetRecording">重新录制</Button>
          </div>

          <Button
            class="w-full"
            :loading="videoTaskState === 'uploading' || videoTaskState === 'analyzing'"
            :disabled="recordState !== 'recorded'"
            @click="uploadRecordedVideo"
          >
            上传并分析
          </Button>

          <div class="rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <div class="flex items-center justify-between gap-2">
              <p class="text-sm font-semibold text-slate-950">分析进度</p>
              <Badge :variant="videoTaskState === 'done' ? 'success' : videoTaskState === 'failed' ? 'warning' : 'info'">
                {{ videoTaskStateLabel }}
              </Badge>
            </div>
            <p class="mt-2 text-sm leading-6 text-slate-600">
              任务状态：{{ videoTaskStateLabel }}
              <span v-if="videoTaskId" class="text-slate-500">({{ videoTaskId }})</span>
            </p>
          </div>

          <div
            v-if="videoResult && videoResult.status === 'DONE'"
            class="space-y-3 rounded-[1.35rem] border border-emerald-200 bg-emerald-50/90 px-4 py-4"
          >
            <div class="flex items-center justify-between gap-2">
              <p class="text-sm font-semibold text-emerald-950">动作评分</p>
              <Badge variant="success">{{ videoResult.score ?? '--' }}</Badge>
            </div>

            <div v-if="videoResult.issues?.length" class="space-y-1">
              <p class="text-xs uppercase tracking-[0.12em] text-emerald-800/80">发现问题</p>
              <ul class="list-disc space-y-1 pl-5 text-sm leading-6 text-emerald-950">
                <li v-for="item in videoResult.issues" :key="item">{{ item }}</li>
              </ul>
            </div>

            <div v-if="videoResult.tips?.length" class="space-y-1">
              <p class="text-xs uppercase tracking-[0.12em] text-emerald-800/80">改进建议</p>
              <ul class="list-disc space-y-1 pl-5 text-sm leading-6 text-emerald-950">
                <li v-for="item in videoResult.tips" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      <div
        v-if="videoResult && videoResult.status === 'DONE' && videoResult.segments?.length"
        class="mt-4 grid gap-3 lg:grid-cols-3"
      >
        <article
          v-for="segment in videoResult.segments"
          :key="`${segment.start}-${segment.end}`"
          class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-white px-4 py-4"
        >
          <p class="text-xs uppercase tracking-[0.12em] text-slate-500">{{ segment.start }} - {{ segment.end }}</p>
          <p class="mt-2 text-sm font-semibold text-slate-950">{{ segment.issue }}</p>
          <p class="mt-2 text-sm leading-6 text-slate-600">{{ segment.suggestion }}</p>
        </article>
      </div>
    </ClinicalSurfaceCard>

    <div class="grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
      <ClinicalSurfaceCard
        eyebrow="Plan Summary"
        title="训练计划"
        description="根据上传资料、体征趋势和动作反馈，动态调整本周训练负荷。"
      >
        <div class="grid grid-cols-2 gap-3 text-sm text-slate-700">
          <div class="rounded-[1.15rem] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-xs uppercase tracking-[0.12em] text-slate-500">重点</p>
            <p class="mt-2 font-semibold text-slate-950">{{ planSummary.focus }}</p>
          </div>
          <div class="rounded-[1.15rem] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-xs uppercase tracking-[0.12em] text-slate-500">频次</p>
            <p class="mt-2 font-semibold text-slate-950">{{ planSummary.frequency }}</p>
          </div>
          <div class="rounded-[1.15rem] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-xs uppercase tracking-[0.12em] text-slate-500">时长</p>
            <p class="mt-2 font-semibold text-slate-950">{{ planSummary.duration }}</p>
          </div>
          <div class="rounded-[1.15rem] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-xs uppercase tracking-[0.12em] text-slate-500">强度</p>
            <p class="mt-2 font-semibold text-slate-950">{{ planSummary.intensity }}</p>
          </div>
        </div>

        <div class="mt-4 flex flex-wrap gap-2">
          <Button variant="secondary" @click="openPlanSettings">设置计划</Button>
          <Button @click="goReminderSettings">设置提醒</Button>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard
        eyebrow="Trend Review"
        title="本周趋势"
        description="用训练分钟、周变化和系统洞察判断当前负荷是否平衡。"
      >
        <div class="grid grid-cols-2 gap-3">
          <div class="rounded-[1.15rem] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-xs uppercase tracking-[0.12em] text-slate-500">周变化</p>
            <p class="mt-2 text-xl font-semibold text-slate-950">+{{ weekTrend?.deltaPercent ?? 0 }}%</p>
          </div>
          <div class="rounded-[1.15rem] bg-[color:var(--surface-secondary)] px-4 py-3">
            <p class="text-xs uppercase tracking-[0.12em] text-slate-500">提醒</p>
            <p class="mt-2 text-sm font-semibold text-slate-950">{{ reminderSummary.channel }}</p>
          </div>
        </div>

        <div class="mt-4 h-56 overflow-hidden rounded-[1.35rem] border border-[color:var(--surface-border)] bg-white p-2">
          <EChartCanvas :option="weekChartOption" />
        </div>

        <p class="mt-4 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm leading-6 text-slate-700">
          {{ weekTrend?.insight ?? '暂无训练趋势数据。' }}
        </p>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalSurfaceCard
      eyebrow="Action List"
      title="今日动作清单"
      description="逐项执行训练动作，记录完成情况，并在需要时查看示范或调整提醒。"
    >
      <div class="space-y-3">
        <article
          v-for="ex in todayExercises"
          :key="ex.id"
          class="rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-base font-semibold text-slate-950">{{ ex.name }}</p>
                <Badge>{{ ex.category }}</Badge>
                <Badge :variant="ex.level === '基础' ? 'success' : 'warning'">{{ ex.level }}</Badge>
              </div>
              <p class="mt-2 text-sm text-slate-600">{{ ex.duration }} · 聚焦 {{ ex.focus }}</p>
            </div>

            <button
              type="button"
              class="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-[color:var(--surface-border)] bg-white text-slate-900 transition hover:bg-slate-50"
              :aria-label="ex.done ? '取消完成' : '标记完成'"
              @click="toggleDone(ex.id)"
            >
              <iconify-icon :icon="ex.done ? 'solar:check-circle-outline' : 'solar:play-outline'" width="18" height="18" />
            </button>
          </div>

          <div class="mt-4 grid gap-3 lg:grid-cols-[1fr_0.9fr_0.8fr]">
            <div class="rounded-[1.15rem] bg-white px-4 py-3">
              <p class="text-xs uppercase tracking-[0.12em] text-slate-500">动作要点</p>
              <ul class="mt-2 list-disc space-y-1 pl-5 text-sm leading-6 text-slate-700">
                <li v-for="s in ex.steps" :key="s">{{ s }}</li>
              </ul>
            </div>

            <div class="rounded-[1.15rem] bg-white px-4 py-3">
              <p class="text-xs uppercase tracking-[0.12em] text-slate-500">注意事项</p>
              <p class="mt-2 text-sm leading-6 text-slate-700">{{ ex.caution }}</p>
            </div>

            <div class="rounded-[1.15rem] bg-white px-4 py-3">
              <p class="text-xs uppercase tracking-[0.12em] text-slate-500">训练收益</p>
              <ul class="mt-2 space-y-1 text-sm leading-6 text-slate-700">
                <li v-for="benefit in ex.benefits" :key="benefit">{{ benefit }}</li>
              </ul>
            </div>
          </div>

          <div class="mt-4 flex flex-wrap gap-2">
            <Button variant="secondary" @click="openVideo(ex.name, ex.id)">动作示范</Button>
            <Button @click="setReminder(ex.name)">设置提醒</Button>
            <Button variant="ghost" @click="removeExercise(ex.id)">删除</Button>
          </div>
        </article>
      </div>
    </ClinicalSurfaceCard>

    <div v-if="showPlanSettings" class="fixed inset-0 z-40 flex items-end justify-center bg-slate-950/45 p-4">
      <div class="w-full max-w-[480px] rounded-[1.8rem] border border-[color:var(--surface-border)] bg-white p-5 shadow-[var(--elevation-strong)]">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-[11px] uppercase tracking-[0.18em] text-slate-500">Plan Settings</p>
            <h2 class="mt-2 text-xl font-semibold text-slate-950">训练计划设置</h2>
            <p class="mt-1 text-sm leading-6 text-slate-600">更新重点、频次、时长和强度，新的节奏会立即用于首页与提醒系统。</p>
          </div>

          <button
            type="button"
            class="inline-flex h-9 w-9 items-center justify-center rounded-2xl border border-[color:var(--surface-border)] text-slate-700 transition hover:bg-slate-50"
            aria-label="关闭"
            @click="showPlanSettings = false"
          >
            <iconify-icon icon="solar:close-circle-outline" width="16" height="16" />
          </button>
        </div>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs text-slate-500">训练重点</span>
            <input v-model="planSettingsForm.focus" class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]" />
          </label>
          <label class="block">
            <span class="text-xs text-slate-500">训练频次</span>
            <input v-model="planSettingsForm.frequency" class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]" />
          </label>
          <label class="block">
            <span class="text-xs text-slate-500">单次时长</span>
            <input v-model="planSettingsForm.duration" class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]" />
          </label>
          <label class="block">
            <span class="text-xs text-slate-500">训练强度</span>
            <input v-model="planSettingsForm.intensity" class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]" />
          </label>
        </div>

        <div class="mt-5 grid grid-cols-2 gap-2.5">
          <Button variant="secondary" :disabled="planSettingsSaving" @click="showPlanSettings = false">取消</Button>
          <Button :loading="planSettingsSaving" @click="submitPlanSettings">保存</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { EChartsCoreOption } from 'echarts'
import { useRouter } from 'vue-router'
import {
  createRehabVideoTask,
  getRehabPlan,
  getRehabPlanSettings,
  getRehabVideoTask,
  removeRehabExercise,
  saveRehabPlanSettings,
  toggleRehabExercise,
  type RehabExercise,
  type RehabPlanSettings,
  type RehabPlanSummary,
  type RehabReminderSummary,
  type RehabVideoResult,
  type RehabWeekTrend,
} from '@/api/modules/rehab'
import { useToast } from '@/composables/useToast'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import EChartCanvas from '@/shared/components/EChartCanvas.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'

const router = useRouter()
const { success, warning, error } = useToast()

const todayExercises = ref<RehabExercise[]>([])
const weekTrend = ref<RehabWeekTrend | null>(null)

const planSummary = ref<RehabPlanSummary>({
  focus: '核心稳定',
  frequency: '每周 3 次',
  duration: '单次 20-30 分钟',
  intensity: '低-中',
})

const reminderSummary = ref<RehabReminderSummary>({
  time: '--:--',
  days: '未设置',
  channel: '未开启',
  status: '未设置',
})

const doneCount = computed(() => todayExercises.value.filter((x) => x.done).length)
const progress = computed(() => {
  if (!todayExercises.value.length) return 0
  return Math.round((doneCount.value / todayExercises.value.length) * 100)
})
const totalMinutes = computed(() => todayExercises.value.reduce((sum, x) => sum + x.minutes, 0))

const cameraVideoRef = ref<HTMLVideoElement | null>(null)
const cameraStream = ref<MediaStream | null>(null)
const mediaRecorder = ref<MediaRecorder | null>(null)
const recordState = ref<'idle' | 'ready' | 'recording' | 'recorded'>('idle')
const cameraError = ref('')

const recordedBlob = ref<Blob | null>(null)
const recordedUrl = ref('')
const recordChunks = ref<Blob[]>([])

const videoTaskId = ref('')
const videoTaskState = ref<'idle' | 'uploading' | 'analyzing' | 'done' | 'failed'>('idle')
const videoResult = ref<RehabVideoResult | null>(null)

const showPlanSettings = ref(false)
const planSettingsSaving = ref(false)
const planSettingsForm = reactive<RehabPlanSettings>({
  focus: '',
  frequency: '',
  duration: '',
  intensity: '',
})

const isCameraReady = computed(() => recordState.value === 'ready' || recordState.value === 'recorded')
const cameraStatusText = computed(() => {
  if (recordState.value === 'recording') return '录制中，请保持动作稳定。'
  if (recordState.value === 'recorded') return '录制完成，可上传进行动作纠错。'
  if (recordState.value === 'ready') return '相机已就绪，可开始录制。'
  return '请先授权相机，再进行动作录制。'
})

const videoTaskStateLabel = computed(() => {
  if (videoTaskState.value === 'uploading') return '上传中'
  if (videoTaskState.value === 'analyzing') return '分析中'
  if (videoTaskState.value === 'done') return '分析完成'
  if (videoTaskState.value === 'failed') return '任务失败'
  return '未开始'
})

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const loadPlan = async () => {
  try {
    const data = await getRehabPlan()
    todayExercises.value = data.exercises
    weekTrend.value = data.weekTrend
    planSummary.value = data.planSummary
    reminderSummary.value = data.reminderSummary
  } catch (err) {
    warning('加载失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const loadPlanSettings = async () => {
  try {
    const data = await getRehabPlanSettings()
    Object.assign(planSettingsForm, data)
  } catch {
    Object.assign(planSettingsForm, planSummary.value)
  }
}

const toggleDone = async (id: number) => {
  try {
    const data = await toggleRehabExercise(id)
    todayExercises.value = data.exercises
    weekTrend.value = data.weekTrend
    planSummary.value = data.planSummary
    reminderSummary.value = data.reminderSummary

    if (todayExercises.value.length && doneCount.value === todayExercises.value.length) {
      success('今日训练已完成', '你已达成全部训练目标。')
    }
  } catch (err) {
    warning('更新失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const removeExercise = async (id: number) => {
  try {
    const data = await removeRehabExercise(id)
    todayExercises.value = data.exercises
    weekTrend.value = data.weekTrend
    planSummary.value = data.planSummary
    reminderSummary.value = data.reminderSummary
    success('动作已移除', '活动清单已更新。')
  } catch (err) {
    error('删除失败', err instanceof Error ? err.message : '请稍后重试。')
  }
}

const openVideo = (name: string, planId: number) => {
  router.push({ path: '/rehab/exercise', query: { name, planId: String(planId) } })
}

const setReminder = (_name?: string) => {
  router.push('/rehab/reminder')
}

const goReminderSettings = () => {
  const targetName = todayExercises.value[0]?.name ?? '鸟狗式'
  setReminder()
}

const attachCameraStream = () => {
  if (!cameraVideoRef.value || !cameraStream.value) return
  cameraVideoRef.value.srcObject = cameraStream.value
}

const requestCamera = async () => {
  cameraError.value = ''
  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = '当前浏览器不支持相机能力。'
    warning('相机不可用', cameraError.value)
    return
  }

  try {
    if (!cameraStream.value) {
      cameraStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    }
    attachCameraStream()
    recordState.value = 'ready'
  } catch (err) {
    cameraError.value = err instanceof Error ? err.message : '相机授权失败'
    warning('相机授权失败', cameraError.value)
  }
}

const startRecord = async () => {
  if (!cameraStream.value) {
    await requestCamera()
  }
  if (!cameraStream.value) return

  try {
    recordChunks.value = []
    const recorder = new MediaRecorder(cameraStream.value)
    mediaRecorder.value = recorder

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        recordChunks.value.push(event.data)
      }
    }

    recorder.onstop = () => {
      const blob = new Blob(recordChunks.value, { type: 'video/webm' })
      recordedBlob.value = blob
      if (recordedUrl.value) {
        URL.revokeObjectURL(recordedUrl.value)
      }
      recordedUrl.value = URL.createObjectURL(blob)
      recordState.value = 'recorded'
    }

    recorder.start()
    recordState.value = 'recording'
    videoResult.value = null
    videoTaskId.value = ''
    videoTaskState.value = 'idle'
  } catch (err) {
    cameraError.value = err instanceof Error ? err.message : '录制失败'
    warning('录制失败', cameraError.value)
  }
}

const stopRecord = () => {
  if (mediaRecorder.value && mediaRecorder.value.state === 'recording') {
    mediaRecorder.value.stop()
  }
}

const resetRecording = () => {
  if (recordedUrl.value) {
    URL.revokeObjectURL(recordedUrl.value)
  }
  recordedUrl.value = ''
  recordedBlob.value = null
  videoResult.value = null
  videoTaskId.value = ''
  videoTaskState.value = 'idle'
  recordState.value = cameraStream.value ? 'ready' : 'idle'
}

const releaseCamera = () => {
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
  }
  if (cameraVideoRef.value) {
    cameraVideoRef.value.srcObject = null
  }
}

const uploadRecordedVideo = async () => {
  if (!recordedBlob.value) {
    warning('请先录制动作视频')
    return
  }

  try {
    videoTaskState.value = 'uploading'
    videoResult.value = null

    const formData = new FormData()
    formData.append('exerciseName', todayExercises.value[0]?.name ?? '康复动作')
    formData.append('file', new File([recordedBlob.value], 'rehab-record.webm', { type: recordedBlob.value.type || 'video/webm' }))

    const task = await createRehabVideoTask(formData)
    videoTaskId.value = task.taskId
    videoTaskState.value = 'analyzing'

    for (let i = 0; i < 12; i += 1) {
      await sleep(800)
      const latest = await getRehabVideoTask(task.taskId)

      if (latest.status === 'DONE') {
        videoResult.value = latest
        videoTaskState.value = 'done'
        success('动作纠错完成', '已生成纠错建议与分段分析。')
        return
      }

      if (latest.status === 'FAILED') {
        throw new Error(latest.message ?? '动作分析失败')
      }
    }

    videoTaskState.value = 'failed'
    warning('分析超时', '任务仍在处理中，请稍后再试。')
  } catch (err) {
    videoTaskState.value = 'failed'
    warning('上传或分析失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const openPlanSettings = () => {
  Object.assign(planSettingsForm, planSummary.value)
  showPlanSettings.value = true
}

const submitPlanSettings = async () => {
  planSettingsSaving.value = true
  try {
    const payload: RehabPlanSettings = {
      focus: planSettingsForm.focus.trim(),
      frequency: planSettingsForm.frequency.trim(),
      duration: planSettingsForm.duration.trim(),
      intensity: planSettingsForm.intensity.trim(),
    }
    const saved = await saveRehabPlanSettings(payload)
    planSummary.value = saved
    showPlanSettings.value = false
    success('计划设置已保存', '新的训练设置已生效。')
  } catch (err) {
    warning('保存失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    planSettingsSaving.value = false
  }
}

const weekChartOption = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 16, right: 16, top: 24, bottom: 20, containLabel: true },
  xAxis: {
    type: 'category',
    data: weekTrend.value?.labels ?? [],
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#cbd5e1' } },
    axisLabel: { color: '#475569' },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { color: '#64748b' },
  },
  series: [
    {
      name: '训练分钟',
      type: 'bar',
      data: weekTrend.value?.values ?? [],
      barWidth: 18,
      itemStyle: { borderRadius: [8, 8, 0, 0], color: '#0f766e' },
    },
  ],
}))

onMounted(() => {
  void loadPlan()
  void loadPlanSettings()
})

onBeforeUnmount(() => {
  releaseCamera()
  if (recordedUrl.value) {
    URL.revokeObjectURL(recordedUrl.value)
  }
})
</script>
