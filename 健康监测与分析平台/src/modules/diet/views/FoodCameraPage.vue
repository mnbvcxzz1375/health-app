<template>
  <div class="mx-auto max-w-[420px] px-4 pb-8 pt-6">
    <!-- 头部 -->
    <div class="flex items-start gap-3">
      <button
        type="button"
        class="flex h-[36px] w-[36px] shrink-0 items-center justify-center rounded-full transition active:scale-[0.98]"
        style="background: var(--secondary); color: var(--foreground);"
        aria-label="返回"
        @click="router.back()"
      >
        <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
      </button>
      <div class="min-w-0 flex-1">
        <h1 class="text-[24px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">AI 拍照识热量</h1>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">拍照识别食物，自动估算热量与营养</p>
      </div>
    </div>

    <!-- 拍照区域卡片 -->
    <section
      class="mt-5 rounded-[19.2px] border p-[18px]"
      style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
    >
      <div class="flex items-center gap-2">
        <iconify-icon icon="solar:camera-outline" width="20" height="20" style="color: var(--brand-500);" />
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">拍摄食物</h2>
      </div>
      <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">对准餐盘拍摄，AI 将自动识别食物种类与分量。</p>

      <!-- 隐藏的相册选择 input -->
      <input
        ref="galleryInput"
        type="file"
        accept="image/*"
        class="hidden"
        @change="handleGalleryUpload"
      />

      <button
        type="button"
        class="mt-4 flex h-[52px] w-full items-center justify-center gap-2 rounded-full transition active:scale-[0.98]"
        style="background: var(--brand-500); color: var(--primary-foreground);"
        @click="triggerCamera"
      >
        <iconify-icon icon="solar:camera-bold" width="22" height="22" />
        <span class="text-[16px] font-semibold">拍照识别</span>
      </button>

      <button
        type="button"
        class="mt-2 flex h-[44px] w-full items-center justify-center gap-2 rounded-full text-[14px] font-medium transition active:scale-[0.98]"
        style="background: var(--secondary); color: var(--foreground);"
        @click="triggerGallery"
      >
        <iconify-icon icon="solar:gallery-add-outline" width="18" height="18" />
        从相册选择
      </button>
    </section>

    <!-- 已拍摄图片预览（识别中/识别完成均显示） -->
    <section
      v-if="capturedImage && !result"
      class="mt-3 rounded-[19.2px] border p-[18px]"
      style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
    >
      <div class="overflow-hidden rounded-[14px]" style="background: var(--background-100);">
        <img :src="capturedImage" alt="拍摄的食物" class="h-[180px] w-full object-cover" />
      </div>
    </section>

    <!-- 识别流程展示 -->
    <section
      v-if="recognizing"
      class="mt-3 rounded-[19.2px] border p-[18px]"
      style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
    >
      <div class="mb-3 flex items-center gap-2">
        <iconify-icon icon="solar:ai-search-outline" width="18" height="18" style="color: var(--brand-500);" class="animate-pulse" />
        <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">AI 识别中</h2>
      </div>

      <div class="space-y-3">
        <!-- Step 1: VLM -->
        <div class="flex items-center gap-3">
          <div
            class="flex h-[32px] w-[32px] shrink-0 items-center justify-center rounded-full"
            :style="stepStyle(1)"
          >
            <iconify-icon
              :icon="recognitionStep > 1 ? 'solar:check-circle-outline' : 'solar:eye-outline'"
              width="18" height="18"
            />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[14px] font-medium" style="color: var(--foreground);">VLM 视觉理解中</p>
            <p class="text-[12px]" style="color: var(--muted-foreground);">
              {{ recognitionStep > 1 ? '已完成食物图像理解' : '正在解析图像内容…' }}
            </p>
          </div>
          <iconify-icon
            v-if="recognitionStep === 1"
            icon="solar:refresh-outline"
            width="14" height="14"
            class="animate-spin"
            style="color: var(--muted-foreground);"
          />
        </div>

        <!-- Step 2: 食物库匹配 -->
        <div class="flex items-center gap-3">
          <div
            class="flex h-[32px] w-[32px] shrink-0 items-center justify-center rounded-full"
            :style="stepStyle(2)"
          >
            <iconify-icon
              :icon="recognitionStep > 2 ? 'solar:check-circle-outline' : 'solar:database-outline'"
              width="18" height="18"
            />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[14px] font-medium" style="color: var(--foreground);">食物库匹配中</p>
            <p class="text-[12px]" style="color: var(--muted-foreground);">
              {{ recognitionStep > 2 ? '已匹配到候选食物' : '正在比对食物数据库…' }}
            </p>
          </div>
          <iconify-icon
            v-if="recognitionStep === 2"
            icon="solar:refresh-outline"
            width="14" height="14"
            class="animate-spin"
            style="color: var(--muted-foreground);"
          />
        </div>

        <!-- Step 3: 分量估算 -->
        <div class="flex items-center gap-3">
          <div
            class="flex h-[32px] w-[32px] shrink-0 items-center justify-center rounded-full"
            :style="stepStyle(3)"
          >
            <iconify-icon
              :icon="recognitionStep > 3 ? 'solar:check-circle-outline' : 'solar:scale-outline'"
              width="18" height="18"
            />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-[14px] font-medium" style="color: var(--foreground);">分量估算中</p>
            <p class="text-[12px]" style="color: var(--muted-foreground);">
              {{ recognitionStep > 3 ? '已完成分量估算' : '正在估算食物重量…' }}
            </p>
          </div>
          <iconify-icon
            v-if="recognitionStep === 3"
            icon="solar:refresh-outline"
            width="14" height="14"
            class="animate-spin"
            style="color: var(--muted-foreground);"
          />
        </div>
      </div>
    </section>

    <!-- 识别结果 -->
    <section
      v-if="result"
      class="mt-3 rounded-[19.2px] border p-[18px]"
      style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
    >
      <div
        v-if="result.warnings.length"
        class="mb-3 rounded-[12px] border px-3 py-2.5 text-[12px]"
        style="border-color: var(--state-warning); background: var(--state-warning-surface); color: var(--state-warning);"
      >
        <p v-for="warningText in result.warnings" :key="warningText">{{ warningText }}</p>
      </div>
      <!-- 食物名称 + 缩略图 + 置信度 -->
      <div class="flex items-center gap-3">
        <div
          v-if="capturedImage"
          class="h-[64px] w-[64px] shrink-0 overflow-hidden rounded-[12px]"
          style="background: var(--background-100);"
        >
          <img :src="capturedImage" :alt="result.foodName" class="h-full w-full object-cover" />
        </div>
        <div
          v-else
          class="flex h-[64px] w-[64px] shrink-0 items-center justify-center rounded-[12px]"
          style="background: var(--brand-50); color: var(--brand-500);"
        >
          <iconify-icon icon="solar:salad-outline" width="28" height="28" />
        </div>
        <div class="min-w-0 flex-1">
          <h2 class="truncate text-[20px] font-semibold" style="color: var(--foreground);">{{ result.foodName }}</h2>
          <div class="mt-1 flex items-center gap-2">
            <span
              class="rounded-full px-2 py-0.5 text-[11px] font-medium tabular-nums"
              style="background: var(--brand-50); color: var(--brand-500);"
            >置信度 {{ result.confidence }}%</span>
            <span class="text-[12px]" style="color: var(--muted-foreground);">{{ result.category }}</span>
          </div>
        </div>
      </div>

      <!-- 营养信息卡片网格 2x2 -->
      <div class="mt-4 grid grid-cols-2 gap-2">
        <div
          v-for="nutrient in nutritionCards"
          :key="nutrient.label"
          class="rounded-[12px] p-3"
          style="background: var(--secondary); border: 1px solid var(--border);"
        >
          <div class="flex items-center gap-1.5">
            <iconify-icon :icon="nutrient.icon" width="16" height="16" :style="{ color: nutrient.color }" />
            <span class="text-[12px]" style="color: var(--muted-foreground);">{{ nutrient.label }}</span>
          </div>
          <p class="mt-1 text-[22px] font-semibold tabular-nums" :style="{ color: nutrient.color }">
            {{ nutrient.value }}<span class="ml-0.5 text-[12px] font-normal" style="color: var(--muted-foreground);">{{ nutrient.unit }}</span>
          </p>
        </div>
      </div>

      <!-- 估算分量信息 -->
      <div
        class="mt-3 flex items-center gap-2 rounded-[12px] px-3 py-2.5"
        style="background: var(--brand-50);"
      >
        <iconify-icon icon="solar:scale-outline" width="16" height="16" style="color: var(--brand-500);" />
        <span class="text-[13px]" style="color: var(--brand-500);">估算分量：约 {{ result.weightGrams }}g · {{ result.portion }}</span>
      </div>

      <!-- 食物详情 -->
      <div class="mt-3 rounded-[12px] p-3" style="background: var(--background-100);">
        <p class="text-[12px] font-medium" style="color: var(--foreground);">每 100g 营养参考</p>
        <div class="mt-2 grid grid-cols-2 gap-y-1 text-[12px] tabular-nums" style="color: var(--muted-foreground);">
          <p>热量：<span style="color: var(--foreground);">{{ result.per100g.calories }} kcal</span></p>
          <p>蛋白质：<span style="color: var(--foreground);">{{ result.per100g.protein }} g</span></p>
          <p>碳水：<span style="color: var(--foreground);">{{ result.per100g.carbs }} g</span></p>
          <p>脂肪：<span style="color: var(--foreground);">{{ result.per100g.fat }} g</span></p>
        </div>
      </div>

      <!-- 操作按钮 -->
      <button
        type="button"
        class="mt-4 flex h-[48px] w-full items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98]"
        style="background: var(--brand-500); color: var(--primary-foreground);"
        @click="recordToLog"
      >
        <iconify-icon icon="solar:add-circle-outline" width="20" height="20" />
        记录到饮食日志
      </button>
      <button
        type="button"
        class="mt-2 flex h-[44px] w-full items-center justify-center gap-2 rounded-full text-[14px] font-medium transition active:scale-[0.98]"
        style="background: var(--secondary); color: var(--foreground);"
        @click="resetResult"
      >
        <iconify-icon icon="solar:camera-rotate-outline" width="18" height="18" />
        重新拍摄
      </button>
    </section>

    <!-- 今日识别记录 -->
    <section
      class="mt-3 rounded-[19.2px] border p-[18px]"
      style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
    >
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <iconify-icon icon="solar:clock-circle-outline" width="18" height="18" style="color: var(--muted-foreground);" />
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">今日识别记录</h2>
        </div>
        <span class="text-[13px] tabular-nums" style="color: var(--muted-foreground);">{{ todayRecords.length }} 条</span>
      </div>

      <div v-if="todayRecords.length" class="mt-2">
        <div
          v-for="(record, idx) in todayRecords"
          :key="record.id"
          class="flex items-center gap-3 py-[12px]"
          :class="{ 'border-b': idx < todayRecords.length - 1 }"
          style="border-color: var(--border);"
        >
          <template v-if="editingRecordId === record.id && editDraft">
            <div class="min-w-0 flex-1 space-y-2">
              <input
                v-model="editDraft.foodName"
                class="h-9 w-full rounded-lg border px-2 text-[13px]"
                style="background: var(--background-100); border-color: var(--border); color: var(--foreground);"
                aria-label="更正食物名称"
              />
              <div class="grid grid-cols-2 gap-2">
                <label class="text-[11px]" style="color: var(--muted-foreground);">
                  重量(g)
                  <input v-model.number="editDraft.weightGrams" type="number" min="0" class="mt-1 h-8 w-full rounded-lg border px-2 text-[12px]" style="background: var(--background-100); border-color: var(--border); color: var(--foreground);" />
                </label>
                <label class="text-[11px]" style="color: var(--muted-foreground);">
                  热量(kcal)
                  <input v-model.number="editDraft.calories" type="number" min="0" class="mt-1 h-8 w-full rounded-lg border px-2 text-[12px]" style="background: var(--background-100); border-color: var(--border); color: var(--foreground);" />
                </label>
              </div>
              <div class="flex justify-end gap-2">
                <button type="button" class="rounded-full px-3 py-1 text-[12px]" style="background: var(--secondary); color: var(--foreground);" @click="cancelEdit">取消</button>
                <button type="button" class="rounded-full px-3 py-1 text-[12px]" style="background: var(--brand-500); color: var(--primary-foreground);" @click="saveCorrection">保存更正</button>
              </div>
            </div>
          </template>
          <template v-else>
            <div
              class="flex h-[36px] w-[36px] shrink-0 items-center justify-center rounded-full"
              style="background: var(--brand-50); color: var(--brand-500);"
            >
              <iconify-icon icon="solar:salad-outline" width="18" height="18" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-[14px] font-medium" style="color: var(--foreground);">{{ record.foodName }}</p>
              <p class="text-[12px] tabular-nums" style="color: var(--muted-foreground);">{{ formatRecordTime(record.recordedAt) }}</p>
            </div>
            <span class="shrink-0 text-[14px] font-semibold tabular-nums" style="color: var(--brand-500);">
              {{ record.calories }} kcal
            </span>
            <button type="button" class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full" style="color: var(--muted-foreground);" :aria-label="`更正${record.foodName}`" @click="beginEdit(record)">
              <iconify-icon icon="solar:pen-outline" width="16" height="16" />
            </button>
            <button type="button" class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full" style="color: var(--state-danger, #ef4444);" :aria-label="`删除${record.foodName}`" @click="removeDietLog(record)">
              <iconify-icon icon="solar:trash-bin-trash-outline" width="16" height="16" />
            </button>
          </template>
        </div>
      </div>

      <div v-else class="mt-2 py-4 text-center">
        <iconify-icon icon="solar:tray-outline" width="32" height="32" style="color: var(--muted-foreground);" />
        <p class="mt-2 text-[13px]" style="color: var(--muted-foreground);">今天还没有识别记录</p>
      </div>
    </section>

    <!-- 相机弹窗 -->
    <div
      v-if="showCameraModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      style="background: rgba(0, 0, 0, 0.48);"
      @click.self="closeCameraModal"
    >
      <div
        class="w-full max-w-[360px] overflow-hidden rounded-[19.2px] border p-4"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-lg, 0 25px 50px -12px rgba(0, 0, 0, 0.25));"
      >
        <div class="flex items-center justify-between">
          <div>
            <h3 class="text-[17px] font-semibold" style="color: var(--foreground);">拍摄食物</h3>
            <p class="text-[13px]" style="color: var(--muted-foreground);">将食物放入框内并拍照</p>
          </div>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
            style="color: var(--muted-foreground);"
            aria-label="关闭相机"
            @click="closeCameraModal"
          >
            <iconify-icon icon="solar:close-circle-outline" width="22" height="22" />
          </button>
        </div>

        <div
          class="relative mt-3 aspect-[4/3] w-full overflow-hidden rounded-[14px]"
          style="background: var(--background-100);"
        >
          <video
            ref="cameraVideo"
            autoplay
            playsinline
            muted
            class="h-full w-full object-cover"
          />
          <div
            v-if="!cameraReady"
            class="absolute inset-0 flex flex-col items-center justify-center gap-2"
            style="color: var(--muted-foreground);"
          >
            <iconify-icon icon="solar:camera-rotate-outline" width="32" height="32" />
            <span class="text-[13px]">正在启动摄像头…</span>
          </div>
        </div>

        <div class="mt-4 flex items-center justify-center gap-4">
          <button
            type="button"
            class="flex h-12 items-center justify-center gap-1.5 rounded-full px-5 text-[14px] font-medium transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            @click="closeCameraModal"
          >
            <iconify-icon icon="solar:close-circle-outline" width="18" height="18" />
            取消
          </button>
          <button
            type="button"
            class="flex h-[56px] w-[56px] shrink-0 items-center justify-center rounded-full transition active:scale-[0.96]"
            style="background: var(--brand-500); color: var(--primary-foreground); box-shadow: 0 0 0 4px var(--brand-100);"
            aria-label="拍照"
            :disabled="!cameraReady"
            @click="captureCameraPhoto"
          >
            <iconify-icon icon="solar:camera-bold" width="26" height="26" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import {
  getTodayDietLogs,
  deleteDietLog,
  recognizeFood as recognizeFoodApi,
  saveDietLog,
  updateDietLog,
  type DietLogEntry,
  type FoodRecognitionResponse,
} from '@/api/modules/diet'

const router = useRouter()
const { success, warning } = useToast()

type RecognitionResult = FoodRecognitionResponse

const showCameraModal = ref(false)
const cameraVideo = ref<HTMLVideoElement | null>(null)
const cameraReady = ref(false)
const cameraStream = ref<MediaStream | null>(null)
const galleryInput = ref<HTMLInputElement | null>(null)
const capturedImage = ref<string | null>(null)
const capturedFile = ref<File | null>(null)
const recognizing = ref(false)
const recognitionStep = ref(0) // 0=未开始, 1=VLM, 2=食物库, 3=分量, 4=完成
const result = ref<RecognitionResult | null>(null)
const todayRecords = ref<DietLogEntry[]>([])
const editingRecordId = ref<number | null>(null)
const editDraft = ref<{
  foodName: string
  category: string
  weightGrams: number
  calories: number
  protein: number
  carbs: number
  fat: number
  source: string
} | null>(null)

onMounted(() => {
  void loadTodayRecords()
})

const nutritionCards = computed(() => {
  if (!result.value) return []
  return [
    { label: '热量', value: result.value.calories, unit: 'kcal', icon: 'solar:fire-outline', color: '#ff9500' },
    { label: '蛋白质', value: result.value.protein, unit: 'g', icon: 'solar:egg-outline', color: 'var(--state-success)' },
    { label: '碳水', value: result.value.carbs, unit: 'g', icon: 'solar:wheat-outline', color: 'var(--brand-500)' },
    { label: '脂肪', value: result.value.fat, unit: 'g', icon: 'solar:oil-outline', color: '#ff3b30' },
  ]
})

// 步骤样式：未开始/进行中/已完成
function stepStyle(step: number): Record<string, string> {
  if (recognitionStep.value > step) {
    return { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
  }
  if (recognitionStep.value === step) {
    return { background: 'var(--brand-50)', color: 'var(--brand-500)' }
  }
  return { background: 'var(--secondary)', color: 'var(--muted-foreground)' }
}

// === 相机相关 ===
function triggerCamera() {
  showCameraModal.value = true
  void startCameraStream()
}

function triggerGallery() {
  galleryInput.value?.click()
}

function stopCameraStream() {
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
  }
  cameraReady.value = false
}

function closeCameraModal() {
  showCameraModal.value = false
  stopCameraStream()
}

async function startCameraStream() {
  try {
    cameraReady.value = false
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    cameraStream.value = stream
    if (cameraVideo.value) {
      cameraVideo.value.srcObject = stream
      cameraVideo.value.onloadedmetadata = () => {
        cameraReady.value = true
      }
    } else {
      cameraReady.value = true
    }
  } catch (err) {
    warning('无法启动摄像头', err instanceof Error ? err.message : '请检查摄像头权限或设备。')
    closeCameraModal()
  }
}

function captureCameraPhoto() {
  const video = cameraVideo.value
  const stream = cameraStream.value
  if (!video || !stream || !cameraReady.value) return

  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth || 1280
  canvas.height = video.videoHeight || 720
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
  closeCameraModal()

  const dataUrl = canvas.toDataURL('image/jpeg', 0.92)
  capturedImage.value = dataUrl
  capturedFile.value = dataUrlToFile(dataUrl, 'food-camera.jpg')
  void recognizeFood()
}

function handleGalleryUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  capturedFile.value = file
  const reader = new FileReader()
  reader.onload = () => {
    capturedImage.value = reader.result as string
    void recognizeFood()
  }
  reader.onerror = () => {
    warning('图片读取失败', '请重试或更换图片。')
  }
  reader.readAsDataURL(file)
  input.value = ''
}

// === 真实识别流程：多模态候选 → 食物库匹配 → 分量换算 ===
async function recognizeFood() {
  const file = capturedFile.value
  if (!file) {
    warning('缺少图片', '请重新拍摄或从相册选择图片。')
    return
  }
  recognizing.value = true
  result.value = null

  recognitionStep.value = 1
  try {
    const response = await recognizeFoodApi(file)
    recognitionStep.value = 2
    await delay(150)
    recognitionStep.value = 3
    await delay(150)
    result.value = response
    recognitionStep.value = 4
  } catch (err: unknown) {
    recognitionStep.value = 0
    warning('识别失败', err instanceof Error ? err.message : '请稍后重试。')
  } finally {
    recognizing.value = false
  }
}

function delay(ms: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, ms))
}

// === 记录与重置 ===
async function loadTodayRecords() {
  try {
    todayRecords.value = await getTodayDietLogs()
  } catch {
    warning('饮食日志加载失败', '当前无法读取历史记录，请稍后重试。')
  }
}

function formatRecordTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function beginEdit(record: DietLogEntry) {
  editingRecordId.value = record.id
  editDraft.value = {
    foodName: record.foodName,
    category: record.category,
    weightGrams: record.weightGrams,
    calories: record.calories,
    protein: record.protein,
    carbs: record.carbs,
    fat: record.fat,
    source: record.source,
  }
}

function cancelEdit() {
  editingRecordId.value = null
  editDraft.value = null
}

async function saveCorrection() {
  const id = editingRecordId.value
  const draft = editDraft.value
  if (id === null || !draft) return
  if (!draft.foodName.trim()) {
    warning('更正失败', '食物名称不能为空。')
    return
  }
  try {
    const saved = await updateDietLog(id, { ...draft, foodName: draft.foodName.trim() })
    todayRecords.value = todayRecords.value.map((item) => (item.id === saved.id ? saved : item))
    cancelEdit()
    success('已更正', '饮食记录已更新并写入审计记录')
  } catch (err: unknown) {
    warning('更正失败', err instanceof Error ? err.message : '饮食记录暂时无法更新。')
  }
}

async function removeDietLog(record: DietLogEntry) {
  if (!window.confirm(`确定删除“${record.foodName}”这条饮食记录吗？`)) return
  try {
    await deleteDietLog(record.id)
    todayRecords.value = todayRecords.value.filter((item) => item.id !== record.id)
    success('已删除', '饮食记录已删除，审计记录仍会保留')
  } catch (err: unknown) {
    warning('删除失败', err instanceof Error ? err.message : '饮食记录暂时无法删除。')
  }
}

async function recordToLog() {
  if (!result.value) return
  const current = result.value
  try {
    const saved = await saveDietLog({
      foodName: current.foodName,
      category: current.category,
      weightGrams: current.weightGrams,
      calories: current.calories,
      protein: current.protein,
      carbs: current.carbs,
      fat: current.fat,
      source: current.source,
    })
    todayRecords.value = [saved, ...todayRecords.value.filter((item) => item.id !== saved.id)]
    success('已记录', `${current.foodName} 已添加到饮食日志`)
    resetResult()
  } catch (err: unknown) {
    warning('记录失败', err instanceof Error ? err.message : '饮食日志暂时无法保存。')
  }
}

function resetResult() {
  result.value = null
  capturedImage.value = null
  capturedFile.value = null
  recognitionStep.value = 0
}

function dataUrlToFile(dataUrl: string, fileName: string): File {
  const [header, encoded] = dataUrl.split(',')
  const mime = header.match(/data:(.*?);/)?.[1] ?? 'image/jpeg'
  const binary = atob(encoded)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index)
  return new File([bytes], fileName, { type: mime })
}
</script>
