<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalFeatureNavBar title="上传分析" back-to="/home" />
    <ClinicalPageHeader title="上传分析" />

    <ClinicalSurfaceCard title="资料类型">
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <button
          v-for="item in typeOptions"
          :key="item.key"
          type="button"
          class="rounded-[1.25rem] border px-4 py-4 text-left transition"
          :class="
            uploadType === item.key
              ? 'border-[color:var(--accent-strong)] bg-[color:var(--accent-soft)]'
              : 'border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] hover:bg-[color:var(--surface-secondary)]'
          "
          @click="selectType(item.key)"
        >
          <p class="text-sm font-semibold text-slate-950">{{ item.label }}</p>
          <p class="mt-1 text-xs text-slate-500">{{ item.hint }}</p>
        </button>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="上传内容">
      <div v-if="isFileType" class="space-y-3">
        <input
          id="upload-file-input"
          ref="fileInput"
          data-testid="upload-file-input"
          type="file"
          class="sr-only"
          :accept="fileAccept"
          multiple
          @change="onFileChange"
        />

        <label
          for="upload-file-input"
          class="flex w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-[1.4rem] border border-dashed border-[color:var(--accent-strong)] bg-[color:var(--accent-soft)] px-4 py-8 text-center transition hover:bg-white"
        >
          <iconify-icon icon="solar:upload-outline" width="24" height="24" class="text-[color:var(--accent-strong)]" />
          <span class="text-sm font-semibold text-slate-950">
            {{ selectedFiles.length ? '重新选择文件' : '选择文件' }}
          </span>
          <span class="text-xs text-slate-500">{{ fileHint }}</span>
        </label>

        <div
          v-if="selectedFiles.length"
          class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
        >
          <div class="flex items-center justify-between gap-3">
            <p class="text-sm font-semibold text-slate-950">已选择 {{ selectedFiles.length }} 个文件</p>
            <Button variant="ghost" @click="clearFiles">清空</Button>
          </div>

          <div class="mt-3 space-y-2">
            <div
              v-for="file in selectedFiles"
              :key="`${file.name}_${file.lastModified}`"
              class="rounded-[1rem] bg-white px-3 py-2 text-sm text-slate-700"
            >
              {{ file.name }}
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="isTextType" class="space-y-3">
        <textarea
          v-model="text"
          rows="7"
          class="w-full resize-none rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm leading-6 outline-none transition focus:border-[color:var(--accent-strong)]"
          placeholder="请输入需要分析的报告内容或症状描述。"
        />
        <p class="text-xs text-slate-500">建议至少输入 10 个字，便于生成更稳定的分析结果。</p>
      </div>

      <ClinicalStateNotice
        v-else
        class="mt-1"
        tone="empty"
        title="请先选择资料类型"
        description="先选择资料类型，再上传文件或输入文字内容。"
      />

      <div
        v-if="status !== 'idle'"
        class="mt-4 rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
      >
        <div class="flex items-center justify-between text-xs text-slate-500">
          <span>{{ statusText }}</span>
          <span>{{ progress }}%</span>
        </div>
        <div class="mt-3">
          <Progress :value="progress" />
        </div>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <Button :loading="isSubmitting" @click="handleSubmitClick">
          <iconify-icon icon="solar:upload-outline" width="16" height="16" />
          开始分析
        </Button>
        <Button variant="secondary" :disabled="isSubmitting" @click="reset">重置</Button>
      </div>
    </ClinicalSurfaceCard>

    <div
      v-if="status === 'complete' && report"
      ref="reportSectionRef"
      class="transition-all duration-500"
      :class="reportRevealActive ? 'translate-y-2 scale-[1.01]' : ''"
    >
      <ClinicalSurfaceCard
        title="分析报告"
        :class="reportRevealActive ? 'ring-2 ring-[color:var(--accent-strong)] ring-offset-2 ring-offset-[color:var(--page-bg)]' : ''"
      >
        <div class="space-y-4">
          <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p class="text-lg font-semibold text-slate-950">{{ report.title }}</p>
                <p class="mt-1 text-sm leading-6 text-slate-600">{{ report.summary }}</p>
              </div>
              <Badge :variant="riskBadgeVariant">{{ report.riskLevel }}</Badge>
            </div>
            <p class="mt-3 text-sm text-slate-700">康复重点：{{ report.rehabFocus }}</p>
          </div>

          <div class="grid gap-3 lg:grid-cols-2">
            <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
              <p class="text-sm font-semibold text-slate-950">关注点</p>
              <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
                <li v-for="point in report.points" :key="point">{{ point }}</li>
              </ul>
            </div>

            <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
              <p class="text-sm font-semibold text-slate-950">建议</p>
              <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
                <li v-for="advice in report.advice" :key="advice">{{ advice }}</li>
              </ul>
            </div>
          </div>

          <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
            <p class="text-sm font-semibold text-slate-950">后续观察</p>
            <ul class="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
              <li v-for="item in report.followUp" :key="item">{{ item }}</li>
            </ul>
            <p class="mt-4 text-xs leading-6 text-slate-500">{{ report.caution }}</p>
          </div>

          <div class="flex flex-wrap gap-2">
            <Button v-if="!saved" :loading="savingReport" @click="handleSaveReport">保留并生成康复计划</Button>
            <Button v-if="!saved" variant="secondary" :disabled="savingReport" @click="handleDiscardReport">
              不保留
            </Button>
            <Badge v-if="saved" variant="success">报告已保留</Badge>
            <Button variant="secondary" @click="goRehab">查看康复计划</Button>
            <Button variant="ghost" @click="goHome">返回总览</Button>
          </div>

          <ClinicalStateNotice
            v-if="savingReport"
            class="mt-4"
            tone="loading"
            title="正在保存并生成康复计划"
            description="请稍候，系统正在合并最近已保留报告并生成草案。"
          />

          <ClinicalStateNotice
            v-else-if="draftGenerationError"
            class="mt-4"
            tone="error"
            title="康复计划草案生成失败"
            :description="draftGenerationError"
          />
        </div>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalSurfaceCard
      v-if="saved && rehabPlanDraft"
      ref="draftSectionRef"
      title="康复计划草案"
      :class="draftRevealActive ? 'ring-2 ring-[color:var(--accent-strong)] ring-offset-2 ring-offset-[color:var(--page-bg)]' : ''"
    >
      <div class="space-y-4">
        <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
          <p class="text-sm font-semibold text-slate-950">计划摘要</p>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            <div class="rounded-[1rem] bg-white px-3 py-3 text-sm text-slate-700">
              <p class="text-xs text-slate-500">重点</p>
              <p class="mt-1 font-semibold text-slate-950">{{ rehabPlanDraft.summary.focus }}</p>
            </div>
            <div class="rounded-[1rem] bg-white px-3 py-3 text-sm text-slate-700">
              <p class="text-xs text-slate-500">频率</p>
              <p class="mt-1 font-semibold text-slate-950">{{ rehabPlanDraft.summary.frequency }}</p>
            </div>
            <div class="rounded-[1rem] bg-white px-3 py-3 text-sm text-slate-700">
              <p class="text-xs text-slate-500">时长</p>
              <p class="mt-1 font-semibold text-slate-950">{{ rehabPlanDraft.summary.duration }}</p>
            </div>
            <div class="rounded-[1rem] bg-white px-3 py-3 text-sm text-slate-700">
              <p class="text-xs text-slate-500">强度</p>
              <p class="mt-1 font-semibold text-slate-950">{{ rehabPlanDraft.summary.intensity }}</p>
            </div>
          </div>
        </div>

        <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
          <div class="flex items-center justify-between gap-3">
            <p class="text-sm font-semibold text-slate-950">动作清单</p>
            <p class="text-xs text-slate-500">共 {{ rehabPlanDraft.exercises.length }} 项</p>
          </div>
          <div class="mt-3 space-y-3">
            <article
              v-for="exercise in rehabPlanDraft.exercises"
              :key="`${exercise.mode}_${exercise.name}`"
              class="rounded-[1rem] bg-white px-4 py-4"
            >
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-sm font-semibold text-slate-950">{{ exercise.name }}</p>
                <Badge :variant="exercise.mode === 'generated' ? 'warning' : 'default'">
                  {{ exercise.mode === 'generated' ? '新增动作' : '复用动作库' }}
                </Badge>
                <Badge>{{ exercise.level }}</Badge>
              </div>
              <p class="mt-2 text-sm text-slate-600">{{ exercise.focus }}</p>
              <div class="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                <span class="rounded-full bg-[color:var(--surface-secondary)] px-3 py-1">{{ exercise.category }}</span>
                <span class="rounded-full bg-[color:var(--surface-secondary)] px-3 py-1">{{ exercise.duration }}</span>
                <span class="rounded-full bg-[color:var(--surface-secondary)] px-3 py-1">{{ exercise.minutes }} 分钟</span>
              </div>
            </article>
          </div>
        </div>

        <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
          <p class="text-sm font-semibold text-slate-950">统一提醒</p>
          <p class="mt-2 text-sm text-slate-700">
            {{ rehabPlanDraft.reminder.time }} / {{ rehabPlanDraft.reminder.days.join('、') }} /
            {{ rehabPlanDraft.reminder.pushEnabled ? '系统通知开启' : '系统通知关闭' }}
          </p>
        </div>

        <div class="flex flex-wrap gap-2">
          <Button :loading="applyingPlan" @click="handleApplyPlanDraft">应用到康复计划</Button>
          <Button variant="secondary" :disabled="applyingPlan" @click="goRehab">稍后去康复页确认</Button>
        </div>
      </div>
    </ClinicalSurfaceCard>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { applyRehabPlanDraft, type RehabPlanDraft } from '@/api/modules/rehab'
import {
  createAnalyzeTask,
  discardAnalyzeReport,
  getAnalyzeResult,
  saveAnalyzeReport,
  type AnalyzeReport,
} from '@/api/modules/upload'
import { useToast } from '@/composables/useToast'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'
import Progress from '@/shared/components/ui/Progress.vue'
import { needsModelImageTranscode, normalizeFilesForModel } from '@/shared/utils/modelImage'

type UploadType = 'image' | 'lab' | 'text' | 'symptom' | null
type UploadStatus = 'idle' | 'uploading' | 'analyzing' | 'complete'

const router = useRouter()
const { success, info, warning, error } = useToast()

const typeOptions = [
  { key: 'image', label: '影像资料', hint: '支持图片、扫描件和影像截图' },
  { key: 'lab', label: '化验报告', hint: '支持 PDF、图片和报告文件' },
  { key: 'text', label: '文字报告', hint: '适合直接粘贴检查结论或病历摘要' },
  { key: 'symptom', label: '症状描述', hint: '适合补充近期状态和主观感受' },
] as const

const uploadType = ref<UploadType>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFiles = ref<File[]>([])
const text = ref('')

const status = ref<UploadStatus>('idle')
const progress = ref(0)
const taskId = ref<string | null>(null)
const report = ref<AnalyzeReport | null>(null)
const saved = ref(false)
const savingReport = ref(false)
const applyingPlan = ref(false)
const rehabPlanDraft = ref<RehabPlanDraft | null>(null)
const draftGenerationError = ref('')
const convertedUnsupportedImages = ref(false)
const reportSectionRef = ref<HTMLElement | null>(null)
const draftSectionRef = ref<HTMLElement | null>(null)
const reportRevealActive = ref(false)
const draftRevealActive = ref(false)

const isFileType = computed(() => uploadType.value === 'image' || uploadType.value === 'lab')
const isTextType = computed(() => uploadType.value === 'text' || uploadType.value === 'symptom')
const isSubmitting = computed(() => status.value === 'uploading' || status.value === 'analyzing')

const fileAccept = computed(() => (uploadType.value === 'image' ? 'image/*,.dcm' : '.pdf,image/*,.txt'))
const fileHint = computed(() =>
  uploadType.value === 'image' ? '支持上传多张图片或影像文件' : '支持 PDF、图片或文本文件',
)
const statusText = computed(() => {
  if (status.value === 'uploading') return '正在上传资料'
  if (status.value === 'analyzing') return '正在调用大模型分析'
  if (status.value === 'complete') return saved.value ? '报告已保留' : '分析已完成'
  return '等待开始'
})

const riskBadgeVariant = computed(() => {
  if (!report.value) return 'default'
  if (report.value.riskLevel === '高风险') return 'danger'
  if (report.value.riskLevel === '中等风险') return 'warning'
  return 'success'
})

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const resolveScrollTarget = (target: unknown): HTMLElement | null => {
  if (target instanceof HTMLElement) return target
  if (target && typeof target === 'object' && '$el' in target) {
    const element = (target as { $el?: unknown }).$el
    return element instanceof HTMLElement ? element : null
  }
  return null
}

const revealReportSection = async () => {
  await sleep(120)
  resolveScrollTarget(reportSectionRef.value)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  reportRevealActive.value = true
  window.setTimeout(() => {
    reportRevealActive.value = false
  }, 1800)
}

const revealDraftSection = async () => {
  await nextTick()
  await sleep(120)
  resolveScrollTarget(draftSectionRef.value)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  draftRevealActive.value = true
  window.setTimeout(() => {
    draftRevealActive.value = false
  }, 1800)
}

const clearFiles = () => {
  selectedFiles.value = []
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const clearResult = () => {
  status.value = 'idle'
  progress.value = 0
  taskId.value = null
  report.value = null
  saved.value = false
  rehabPlanDraft.value = null
  draftGenerationError.value = ''
  convertedUnsupportedImages.value = false
}

const selectType = (value: UploadType) => {
  if (isSubmitting.value) return
  uploadType.value = value
  clearFiles()
  text.value = ''
  clearResult()
}

const onFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  selectedFiles.value = Array.from(input.files ?? [])
}

const validateSubmission = () => {
  if (!uploadType.value) {
    warning('请选择资料类型', '先选择资料类型，再继续分析。')
    return false
  }

  if (isFileType.value && !selectedFiles.value.length) {
    warning('请选择文件', '请先上传需要分析的文件或图片。')
    return false
  }

  if (isTextType.value && text.value.trim().length < 10) {
    warning('文字内容过短', '请输入至少 10 个字后再开始分析。')
    return false
  }

  return true
}

const pollResult = async (id: string) => {
  let current = progress.value

  for (let attempt = 0; attempt < 18; attempt += 1) {
    await sleep(650)
    current = Math.min(95, current + 6)
    progress.value = current

    const data = await getAnalyzeResult(id)
    if (data.status === 'DONE') {
      report.value = data.report ?? null
      status.value = 'complete'
      progress.value = 100
      saved.value = Boolean(data.saved)
      success('分析完成', '结构化报告已经生成。')
      await revealReportSection()
      return
    }

    if (data.status === 'FAILED') {
      throw new Error(data.message ?? '分析失败')
    }
  }

  warning('分析仍在进行', '请稍后再查看结果。')
}

const submit = async () => {
  status.value = 'uploading'
  progress.value = 10

  const normalizedFiles = await normalizeFilesForModel(selectedFiles.value)
  convertedUnsupportedImages.value = normalizedFiles.some((file, index) => file !== selectedFiles.value[index])

  const payload = new FormData()
  payload.append('type', uploadType.value ?? '')
  normalizedFiles.forEach((file) => payload.append('files', file))
  if (text.value.trim()) payload.append('text', text.value.trim())

  const response = await createAnalyzeTask(payload)
  taskId.value = response.taskId
  progress.value = 45
  status.value = 'analyzing'
  if (convertedUnsupportedImages.value || selectedFiles.value.some(needsModelImageTranscode)) {
    info('已自动转换图片格式', '不兼容的图片已转为 JPG 后再送去分析。')
  }
  await pollResult(response.taskId)
}

const handleSubmitClick = async () => {
  if (isSubmitting.value) return
  if (!validateSubmission()) return

  try {
    await submit()
  } catch (err) {
    draftGenerationError.value = err instanceof Error ? err.message : '请稍后重试。'
    console.error('[UploadAnalysis] saveAndGenerateDraft failed', err)
    error('分析失败', err instanceof Error ? err.message : '请稍后重试。')
    clearResult()
  }
}

const reset = () => {
  if (isSubmitting.value) return
  uploadType.value = null
  clearFiles()
  text.value = ''
  clearResult()
  info('已重置', '可以重新选择资料并分析。')
}

const handleSaveReport = async () => {
  if (savingReport.value) {
    info('正在生成草案', '请稍候，系统正在生成康复计划草案。')
    return
  }
  if (!taskId.value) {
    warning('当前没有可保存的结果', '请先完成一次上传分析，再生成康复计划。')
    return
  }
  draftGenerationError.value = ''
  savingReport.value = true
  try {
    info('开始生成康复计划', '系统正在合并最近已保留报告，请稍候。')
    console.info('[UploadAnalysis] saveAndGenerateDraft start', { taskId: taskId.value })
    const response = await saveAnalyzeReport(taskId.value)
    console.info('[UploadAnalysis] saveAndGenerateDraft response', response)
    if (!response.rehabPlanDraft || !response.rehabPlanDraft.exercises.length) {
      throw new Error('康复计划草案为空，请重新点击生成。')
    }
    saved.value = response.saved
    rehabPlanDraft.value = response.rehabPlanDraft
    success('报告已保留', '已生成康复计划草案，请确认后再应用。')
    await revealDraftSection()
  } catch (err) {
    draftGenerationError.value = err instanceof Error ? err.message : '请稍后重试。'
    console.error('[UploadAnalysis] saveAndGenerateDraft failed', err)
    error('保留失败', err instanceof Error ? err.message : '请稍后重试。')
  } finally {
    savingReport.value = false
  }
}

const handleApplyPlanDraft = async () => {
  if (!rehabPlanDraft.value || applyingPlan.value) return
  applyingPlan.value = true
  try {
    await applyRehabPlanDraft(rehabPlanDraft.value)
    success('康复计划已更新', '新的计划摘要、动作清单和统一提醒已经写入。')
    router.push('/rehab')
  } catch (err) {
    error('应用失败', err instanceof Error ? err.message : '请稍后重试。')
  } finally {
    applyingPlan.value = false
  }
}

const handleDiscardReport = async () => {
  if (!taskId.value || savingReport.value) return
  savingReport.value = true
  try {
    await discardAnalyzeReport(taskId.value)
    clearResult()
    success('已不保留', '本次分析报告已经移除。')
  } catch (err) {
    error('删除失败', err instanceof Error ? err.message : '请稍后重试。')
  } finally {
    savingReport.value = false
  }
}

const goRehab = () => router.push('/rehab')
const goHome = () => router.push('/home')
</script>
