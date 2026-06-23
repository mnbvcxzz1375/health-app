<template>
  <div class="space-y-5 pb-6 text-slate-950">
    <ClinicalFeatureNavBar :title="pageTitle" back-to="/medication" />

    <ClinicalSurfaceCard v-if="loadingAlarm" title="正在加载闹钟">
      <p class="text-sm leading-6 text-slate-600">请稍候，正在读取当前闹钟设置。</p>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard v-else :title="pageTitle">
      <div class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-[minmax(0,320px)_minmax(0,180px)]">
          <div class="block">
            <span class="text-xs text-slate-500">提醒时间</span>
            <div class="mt-1 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2">
              <AppSelect
                v-model="hourValue"
                ariaLabel="提醒小时"
                placeholder="小时"
                :options="hourOptions"
              />
              <span class="text-sm font-semibold text-slate-400">:</span>
              <AppSelect
                v-model="minuteValue"
                ariaLabel="提醒分钟"
                placeholder="分钟"
                :options="minuteOptions"
              />
            </div>
          </div>

          <div class="block">
            <span class="text-xs text-slate-500">提醒状态</span>
            <AppSelect
              v-model="form.enabled"
              class="mt-1"
              ariaLabel="提醒状态"
              placeholder="请选择"
              :options="statusOptions"
            />
          </div>
        </div>

        <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">药盒识别</p>
              <p class="mt-1 text-xs leading-5 text-slate-600">
                自动填入的内容只允许来自图片中的可见文字。识别不确定的字段会保留为空，交由你再次确认。
              </p>
            </div>

            <div class="flex flex-wrap gap-2">
              <input
                id="medication-upload-input"
                ref="recognitionUploadInput"
                data-testid="medication-upload-input"
                type="file"
                accept="image/*"
                multiple
                class="hidden"
                @change="onUploadRecognitionFiles"
              />
              <Button variant="secondary" @click="openUploadPicker">
                <iconify-icon icon="solar:gallery-outline" width="16" height="16" />
                上传图片
              </Button>
              <Button variant="secondary" :disabled="cameraStarting" @click="openCameraPanel">
                <iconify-icon icon="solar:camera-outline" width="16" height="16" />
                拍照采集
              </Button>
              <Button :loading="recognizing" :disabled="!recognitionAssets.length" @click="runRecognition">
                <iconify-icon icon="solar:magic-stick-3-outline" width="16" height="16" />
                开始识别
              </Button>
            </div>
          </div>

          <div
            v-if="cameraPanelOpen"
            class="mt-4 rounded-[1rem] border border-[color:var(--surface-border)] bg-white p-3"
          >
            <div class="overflow-hidden rounded-[0.9rem] bg-slate-950">
              <video ref="cameraVideo" autoplay playsinline muted class="h-[220px] w-full object-cover" />
            </div>
            <div class="mt-3 flex flex-wrap gap-2">
              <Button variant="secondary" :disabled="!cameraReady" @click="captureCameraShot">拍一张</Button>
              <Button variant="secondary" @click="closeCameraPanel">关闭摄像头</Button>
            </div>
          </div>

          <div v-if="recognitionAssets.length" class="mt-4 space-y-3">
            <div class="text-xs text-slate-500">已选择 {{ recognitionAssets.length }} 张图片</div>

            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3" data-testid="recognition-preview-grid">
              <article
                v-for="asset in recognitionAssets"
                :key="asset.id"
                class="overflow-hidden rounded-[1rem] border border-[color:var(--surface-border)] bg-white"
              >
                <img :src="asset.previewUrl" :alt="asset.name" class="h-32 w-full object-cover" />
                <div class="flex items-center justify-between gap-2 px-3 py-2">
                  <p class="min-w-0 truncate text-xs text-slate-600">{{ asset.name }}</p>
                  <button type="button" class="text-xs text-rose-600" @click="removeRecognitionAsset(asset.id)">
                    移除
                  </button>
                </div>
              </article>
            </div>
          </div>
        </div>

        <div
          v-if="recognitionReviewRequired"
          ref="reviewNoticeRef"
          class="rounded-[1.2rem] border border-amber-200 bg-amber-50 px-4 py-3"
        >
          <p class="text-sm font-semibold text-amber-950">识别完成后请逐项核对，确认无误后再保存闹钟。</p>
          <p class="mt-1 text-xs leading-5 text-amber-800">
            药品名称、剂量、单位和服用方式都必须以图片中的文字为准，未识别项请手动补充。
          </p>
          <label class="mt-3 inline-flex items-center gap-2 text-sm text-amber-950">
            <input
              ref="reviewCheckboxRef"
              v-model="recognitionReviewConfirmed"
              type="checkbox"
              aria-label="我已核对识别结果"
              @change="clearReviewError"
            />
            我已核对识别结果
          </label>
          <p v-if="reviewErrorMessage" class="mt-2 text-xs font-medium text-rose-600">
            {{ reviewErrorMessage }}
          </p>
        </div>

        <div class="space-y-3">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">药品清单</p>
              <p class="mt-1 text-xs text-slate-500">新增药品会插入到最上面，方便继续识别并追加。</p>
            </div>
            <Button variant="secondary" @click="addMedicationRow">
              <iconify-icon icon="solar:add-circle-outline" width="16" height="16" />
              新增药品
            </Button>
          </div>

          <div class="space-y-3">
            <article
              v-for="(drug, index) in form.medications"
              :key="drug.localId"
              class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
            >
              <div class="flex items-center justify-between gap-3">
                <p class="text-sm font-semibold text-slate-950">药品 {{ index + 1 }}</p>
                <button
                  type="button"
                  class="text-xs text-rose-600 disabled:cursor-not-allowed disabled:opacity-40"
                  :disabled="form.medications.length === 1"
                  @click="removeMedicationRow(drug.localId)"
                >
                  删除
                </button>
              </div>

              <div class="mt-3 grid gap-3 lg:grid-cols-2">
                <label class="block">
                  <span class="text-xs text-slate-500">药品名称</span>
                  <input
                    v-model="drug.name"
                    aria-label="药品名称"
                    class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
                    placeholder="例如：降压药"
                  />
                </label>

                <label class="block">
                  <span class="text-xs text-slate-500">口语别名</span>
                  <input
                    v-model="drug.alias"
                    aria-label="口语别名"
                    class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
                    placeholder="例如：小白片"
                  />
                </label>
              </div>

              <div class="mt-3 grid gap-3 md:grid-cols-3">
                <div class="block">
                  <span class="text-xs text-slate-500">剂量</span>
                  <AppSelect
                    v-model="drug.dosageValue"
                    class="mt-1"
                    ariaLabel="剂量"
                    placeholder="待确认"
                    :options="dosageValueOptions"
                  />
                </div>

                <div class="block">
                  <span class="text-xs text-slate-500">单位</span>
                  <AppSelect
                    v-model="drug.dosageUnit"
                    class="mt-1"
                    ariaLabel="单位"
                    placeholder="待确认"
                    :options="dosageUnitOptions"
                  />
                </div>

                <div class="block">
                  <span class="text-xs text-slate-500">服用方式</span>
                  <AppSelect
                    v-model="drug.usage"
                    class="mt-1"
                    ariaLabel="服用方式"
                    placeholder="待确认"
                    :options="usageSelectOptions"
                  />
                </div>
              </div>

              <label class="mt-3 block">
                <span class="text-xs text-slate-500">注意事项</span>
                <textarea
                  v-model="drug.notes"
                  rows="2"
                  class="mt-1 w-full resize-none rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]"
                  placeholder="例如：避免与牛奶同服"
                />
              </label>
            </article>
          </div>
        </div>

        <div class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
          <p class="text-sm font-semibold text-slate-950">确认提示</p>
          <p class="mt-1 text-sm leading-6 text-slate-600">
            保存前请确认每种药品的名称、剂量、单位和服用方式均已核对完成。
          </p>
        </div>

        <div class="grid grid-cols-2 gap-2.5">
          <Button class="w-full" :loading="saving" :disabled="loadingAlarm" @click="saveAlarm">保存闹钟</Button>
          <Button class="w-full" variant="secondary" :disabled="saving || loadingAlarm" @click="resetForm">
            清空
          </Button>
        </div>
      </div>
    </ClinicalSurfaceCard>

    <p class="px-1 text-center text-xs leading-6 text-slate-500">
      用药提醒仅用于执行辅助，不替代医生处方与药学指导。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { type LocationQueryValue, useRoute, useRouter } from 'vue-router'
import {
  createMedicationAlarm,
  getMedicationAlarms,
  recognizeMedicationBatch,
  updateMedicationAlarm,
  type MedicationAlarm,
  type MedicationAlarmDrugInput,
  type MedicationAlarmPayload,
  type MedicationRecognitionResult,
} from '@/api/modules/medication'
import { useToast } from '@/composables/useToast'
import { emitMedicationAlarmChangedEvent } from '@/modules/medication/utils/medicationAlarm'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import AppSelect from '@/shared/components/ui/AppSelect.vue'
import Button from '@/shared/components/ui/Button.vue'
import { normalizeFilesForModel } from '@/shared/utils/modelImage'

type RecognitionAsset = {
  id: string
  file: File
  name: string
  previewUrl: string
  source: 'upload' | 'camera'
}

type EditableMedicationDrug = MedicationAlarmDrugInput & {
  localId: string
  id?: number
}

const dosageUnits = ['片', '粒', '毫升', '滴', '袋']
const usageOptions = ['饭前', '饭后', '随餐', '睡前', '按需']
const statusOptions = [
  { label: '启用', value: true },
  { label: '暂停', value: false },
]
const dosageValueOptions = [
  { label: '待确认', value: null },
  ...Array.from({ length: 12 }, (_, index) => ({
    label: String(index + 1),
    value: index + 1,
  })),
]
const dosageUnitOptions = [
  { label: '待确认', value: '' },
  ...dosageUnits.map((unit) => ({ label: unit, value: unit })),
]
const usageSelectOptions = [
  { label: '待确认', value: '' },
  ...usageOptions.map((item) => ({ label: item, value: item })),
]
const hourOptions = Array.from({ length: 24 }, (_, index) => ({
  label: String(index).padStart(2, '0'),
  value: String(index).padStart(2, '0'),
}))
const minuteOptions = Array.from({ length: 60 }, (_, index) => ({
  label: String(index).padStart(2, '0'),
  value: String(index).padStart(2, '0'),
}))

const route = useRoute()
const router = useRouter()
const { success, warning, error } = useToast()

const requestedAlarmId = ref<number | null>(null)
const originalAlarm = ref<MedicationAlarm | null>(null)
const editingId = ref<number | null>(null)
const loadingAlarm = ref(false)
const saving = ref(false)
const recognizing = ref(false)
const cameraStarting = ref(false)
const cameraPanelOpen = ref(false)
const cameraReady = ref(false)
const recognitionUploadInput = ref<HTMLInputElement | null>(null)
const cameraVideo = ref<HTMLVideoElement | null>(null)
const reviewCheckboxRef = ref<HTMLInputElement | null>(null)
const reviewNoticeRef = ref<HTMLElement | null>(null)
const recognitionAssets = ref<RecognitionAsset[]>([])
const recognitionReviewRequired = ref(false)
const recognitionReviewConfirmed = ref(false)
const reviewErrorMessage = ref('')
const cameraStream = ref<MediaStream | null>(null)

const createLocalId = () => `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`

const createEmptyDrug = (): EditableMedicationDrug => ({
  localId: createLocalId(),
  name: '',
  alias: '',
  dosageValue: null,
  dosageUnit: '',
  usage: '',
  notes: '',
  photoUrl: '',
  enableOcr: false,
  enableYolo: false,
  ocrEndpoint: '',
  yoloEndpoint: '',
  enabled: true,
})

const form = reactive<{
  time: string
  enabled: boolean
  medications: EditableMedicationDrug[]
}>({
  time: '08:00',
  enabled: true,
  medications: [createEmptyDrug()],
})

const pageTitle = computed(() => (requestedAlarmId.value !== null ? '修改闹钟' : '新增闹钟'))

const hourValue = computed({
  get: () => form.time.split(':')[0] || '08',
  set: (value: string | number | boolean | null) => {
    const hour = String(value ?? '08').padStart(2, '0')
    const minute = form.time.split(':')[1] || '00'
    form.time = `${hour}:${minute}`
  },
})

const minuteValue = computed({
  get: () => form.time.split(':')[1] || '00',
  set: (value: string | number | boolean | null) => {
    const hour = form.time.split(':')[0] || '08'
    const minute = String(value ?? '00').padStart(2, '0')
    form.time = `${hour}:${minute}`
  },
})

const isPendingDrug = (drug: EditableMedicationDrug) =>
  !drug.name.trim() &&
  !drug.alias.trim() &&
  !drug.notes.trim() &&
  drug.dosageValue === null &&
  !drug.dosageUnit &&
  !drug.usage

const applyEmptyForm = () => {
  editingId.value = null
  form.time = '08:00'
  form.enabled = true
  form.medications = [createEmptyDrug()]
}

const clearReviewError = () => {
  reviewErrorMessage.value = ''
}

const resetReviewState = () => {
  recognitionReviewRequired.value = false
  recognitionReviewConfirmed.value = false
  reviewErrorMessage.value = ''
}

const fillFormFromAlarm = (alarm: MedicationAlarm) => {
  editingId.value = alarm.id
  form.time = alarm.time
  form.enabled = alarm.enabled
  form.medications = alarm.medications.length
    ? alarm.medications.map((drug) => ({
        localId: createLocalId(),
        id: drug.id,
        name: drug.name,
        alias: drug.alias,
        dosageValue: drug.dosageValue ?? null,
        dosageUnit: drug.dosageUnit ?? '',
        usage: drug.usage ?? '',
        notes: drug.notes,
        photoUrl: drug.photoUrl,
        enableOcr: drug.enableOcr,
        enableYolo: drug.enableYolo,
        ocrEndpoint: drug.ocrEndpoint,
        yoloEndpoint: drug.yoloEndpoint,
        enabled: drug.enabled,
      }))
    : [createEmptyDrug()]
  resetReviewState()
}

const revokeRecognitionAsset = (asset: RecognitionAsset) => {
  if (asset.previewUrl.startsWith('blob:')) {
    URL.revokeObjectURL(asset.previewUrl)
  }
}

const clearRecognitionAssets = () => {
  recognitionAssets.value.forEach(revokeRecognitionAsset)
  recognitionAssets.value = []
  if (recognitionUploadInput.value) {
    recognitionUploadInput.value.value = ''
  }
}

const stopCameraStream = () => {
  cameraStream.value?.getTracks().forEach((track) => track.stop())
  cameraStream.value = null
  cameraReady.value = false
  cameraPanelOpen.value = false
  if (cameraVideo.value) {
    cameraVideo.value.srcObject = null
  }
}

const closeCameraPanel = () => {
  stopCameraStream()
}

const resetForm = () => {
  clearRecognitionAssets()
  closeCameraPanel()

  if (originalAlarm.value) {
    fillFormFromAlarm(originalAlarm.value)
    return
  }

  applyEmptyForm()
  resetReviewState()
}

const goToMedicationPage = async () => {
  await router.replace('/medication')
}

const resolveAlarmId = (value: LocationQueryValue | LocationQueryValue[] | undefined): number | null => {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (rawValue == null || rawValue === '') return null

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const initializePage = async () => {
  const rawAlarmId = route.query.id
  const hasAlarmId = Array.isArray(rawAlarmId) ? rawAlarmId.length > 0 : rawAlarmId != null
  const alarmId = resolveAlarmId(rawAlarmId)

  requestedAlarmId.value = alarmId
  clearRecognitionAssets()
  closeCameraPanel()

  if (hasAlarmId && alarmId === null) {
    warning('闹钟参数无效', '请从用药提醒列表重新进入。')
    await goToMedicationPage()
    return
  }

  if (alarmId === null) {
    originalAlarm.value = null
    applyEmptyForm()
    resetReviewState()
    loadingAlarm.value = false
    return
  }

  loadingAlarm.value = true

  try {
    const alarms = await getMedicationAlarms()
    const target = alarms.find((item) => item.id === alarmId)

    if (!target) {
      warning('未找到对应闹钟', '请从用药提醒列表重新进入。')
      await goToMedicationPage()
      return
    }

    originalAlarm.value = target
    fillFormFromAlarm(target)
  } catch (loadError) {
    warning('加载失败', loadError instanceof Error ? loadError.message : '请稍后重试。')
    await goToMedicationPage()
  } finally {
    loadingAlarm.value = false
  }
}

const revealReviewNotice = async () => {
  await nextTick()
  reviewNoticeRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  reviewCheckboxRef.value?.focus()
}

const revealRecognitionResult = async () => {
  await nextTick()
  reviewNoticeRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  reviewCheckboxRef.value?.focus()
}

const addMedicationRow = () => {
  form.medications.unshift(createEmptyDrug())
  clearRecognitionAssets()
  closeCameraPanel()
  resetReviewState()
}

const removeMedicationRow = (localId: string) => {
  if (form.medications.length === 1) return
  form.medications = form.medications.filter((item) => item.localId !== localId)
}

const appendRecognitionFiles = (files: File[], source: RecognitionAsset['source']) => {
  const nextAssets = files.map((file, index) => ({
    id: `${Date.now()}_${index}_${Math.random().toString(36).slice(2, 8)}`,
    file,
    name: file.name,
    previewUrl: typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : '',
    source,
  }))
  recognitionAssets.value = [...recognitionAssets.value, ...nextAssets]
}

const removeRecognitionAsset = (id: string) => {
  const target = recognitionAssets.value.find((item) => item.id === id)
  if (target) revokeRecognitionAsset(target)
  recognitionAssets.value = recognitionAssets.value.filter((item) => item.id !== id)
}

const openCameraPanel = async () => {
  if (!navigator.mediaDevices?.getUserMedia) {
    warning('当前浏览器不支持摄像头调用。')
    return
  }

  cameraStarting.value = true
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: 'environment' },
    })
    cameraStream.value = stream
    cameraPanelOpen.value = true

    if (cameraVideo.value) {
      cameraVideo.value.srcObject = stream
      await cameraVideo.value.play()
    }
    cameraReady.value = true
  } catch (captureError) {
    warning(captureError instanceof Error ? captureError.message : '摄像头启动失败，请检查系统权限。')
  } finally {
    cameraStarting.value = false
  }
}

const captureCameraShot = async () => {
  if (!cameraVideo.value) return

  const video = cameraVideo.value
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth || 1280
  canvas.height = video.videoHeight || 720
  const context = canvas.getContext('2d')

  if (!context) {
    warning('拍照失败，请稍后再试。')
    return
  }

  context.drawImage(video, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
  if (!blob) {
    warning('拍照失败，请稍后再试。')
    return
  }

  appendRecognitionFiles([new File([blob], `camera-shot-${Date.now()}.jpg`, { type: 'image/jpeg' })], 'camera')
}

const openUploadPicker = () => {
  recognitionUploadInput.value?.click()
}

const onUploadRecognitionFiles = (event: Event) => {
  const target = event.target as HTMLInputElement | null
  const files = Array.from(target?.files ?? [])
  if (!files.length) return
  appendRecognitionFiles(files, 'upload')
}

const mapRecognitionToDrug = (item: MedicationRecognitionResult): EditableMedicationDrug => ({
  localId: createLocalId(),
  name: item.name,
  alias: item.alias,
  dosageValue: item.dosageValue ?? null,
  dosageUnit: item.dosageUnit || '',
  usage: item.usage || '',
  notes: item.notes || '',
  photoUrl: item.photoUrl || '',
  enableOcr: false,
  enableYolo: false,
  ocrEndpoint: '',
  yoloEndpoint: '',
  enabled: true,
})

const mergeRecognizedItems = (items: MedicationRecognitionResult[]) => {
  const next = [...form.medications]
  items.forEach((item) => {
    const recognized = mapRecognitionToDrug(item)
    const pendingIndex = next.findIndex(isPendingDrug)
    if (pendingIndex >= 0) {
      next[pendingIndex] = {
        ...next[pendingIndex],
        ...recognized,
        localId: next[pendingIndex].localId,
        id: next[pendingIndex].id,
      }
      return
    }
    next.unshift(recognized)
  })
  form.medications = next
}

const runRecognition = async () => {
  if (!recognitionAssets.value.length) {
    warning('请先上传或拍摄药盒图片。')
    return
  }

  recognizing.value = true
  clearReviewError()

  try {
    const normalizedFiles = await normalizeFilesForModel(recognitionAssets.value.map((item) => item.file))
    const result = await recognizeMedicationBatch(normalizedFiles)

    if (!result.items.length) {
      warning('未识别到可用药品信息，请更换更清晰的图片。')
      return
    }

    mergeRecognizedItems(result.items)
    recognitionReviewRequired.value = true
    recognitionReviewConfirmed.value = false
    success(`已识别 ${result.items.length} 种药品，请逐项核对后再保存。`)
    await revealRecognitionResult()
  } catch (recognizeError) {
    error('识别失败', recognizeError instanceof Error ? recognizeError.message : '药品识别失败，请稍后再试。')
  } finally {
    recognizing.value = false
  }
}

const validateMedications = (drugs: EditableMedicationDrug[]) => {
  const medications = drugs
    .map((item) => ({
      id: item.id,
      name: item.name.trim(),
      alias: item.alias.trim(),
      dosageValue: item.dosageValue,
      dosageUnit: item.dosageUnit,
      usage: item.usage,
      notes: item.notes.trim(),
      photoUrl: item.photoUrl ?? '',
      enableOcr: false,
      enableYolo: false,
      ocrEndpoint: '',
      yoloEndpoint: '',
      enabled: true,
    }))
    .filter((item) => item.name)

  if (!medications.length) {
    warning('至少填写一种药品后再保存。')
    return null
  }

  const invalidDrug = medications.find((item) => !item.dosageValue || !item.dosageUnit.trim() || !item.usage.trim())
  if (invalidDrug) {
    warning('请先把待确认字段补充完整后再保存。')
    return null
  }

  return medications
}

const toPayload = (): MedicationAlarmPayload | null => {
  if (!/^\d{2}:\d{2}$/.test(form.time.trim())) {
    warning('请先设置有效的提醒时间。')
    return null
  }

  if (recognitionReviewRequired.value && !recognitionReviewConfirmed.value) {
    reviewErrorMessage.value = '请先勾选“我已核对识别结果”，再保存闹钟。'
    warning('请先确认你已核对识别结果。')
    void revealReviewNotice()
    return null
  }

  const medications = validateMedications(form.medications)
  if (!medications) return null

  return {
    time: form.time.trim(),
    enabled: form.enabled,
    medications,
  }
}

const saveAlarm = async () => {
  const payload = toPayload()
  if (!payload) return

  const wasEditing = editingId.value !== null
  saving.value = true

  try {
    await (wasEditing
      ? updateMedicationAlarm(editingId.value as number, payload)
      : createMedicationAlarm(payload))

    emitMedicationAlarmChangedEvent()
    success(wasEditing ? '闹钟修改成功' : '闹钟创建成功')
    if (wasEditing && typeof window !== 'undefined' && typeof window.alert === 'function') {
      window.alert('闹钟修改成功')
    }
    await router.push('/medication')
  } catch (saveError) {
    error('保存失败', saveError instanceof Error ? saveError.message : '闹钟保存失败，请稍后再试。')
  } finally {
    saving.value = false
  }
}

watch(
  () => route.query.id,
  () => {
    void initializePage()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopCameraStream()
  clearRecognitionAssets()
})
</script>
