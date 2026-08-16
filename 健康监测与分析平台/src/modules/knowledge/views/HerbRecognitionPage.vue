<template>
  <div class="apple-herb pb-6">
    <div class="mx-auto max-w-[420px] px-4 pt-6">
      <!-- Page Header -->
      <header>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground); line-height: 1.15;">多药材识别</h1>
        <p class="mt-1.5 text-[15px]" style="color: var(--muted-foreground);">拍摄一张含多种药材的照片，AI 自动识别并匹配知识库</p>
      </header>

      <!-- Scan Trigger Card -->
      <section
        class="mt-5 rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">拍照或选择图片</h2>
        <p class="mt-1 text-[14px]" style="color: var(--muted-foreground);">支持多种中药材同框识别，自动去重</p>

        <!-- 选图 input：不带 capture -->
        <input
          ref="galleryInput"
          type="file"
          accept="image/*"
          class="hidden"
          @change="handleUpload"
        />

        <!-- Primary: 拍照识别 -->
        <button
          type="button"
          class="mt-4 flex h-[52px] w-full items-center justify-center gap-2 rounded-full transition active:scale-[0.98] disabled:opacity-60"
          style="background: var(--brand-500); color: var(--primary-foreground);"
          :disabled="loading"
          @click="triggerCameraScan"
        >
          <iconify-icon icon="solar:camera-outline" width="22" height="22" />
          <span class="text-[16px] font-semibold">{{ loading ? '识别中…' : '拍照识别' }}</span>
        </button>

        <!-- Secondary: 从相册选择 -->
        <button
          type="button"
          class="mt-2 flex h-[44px] w-full items-center justify-center gap-2 rounded-full text-[14px] font-medium transition active:scale-[0.98] disabled:opacity-60"
          style="background: var(--secondary); color: var(--foreground);"
          :disabled="loading"
          @click="triggerGallery"
        >
          <iconify-icon icon="solar:gallery-add-outline" width="18" height="18" />
          从相册选择
        </button>
      </section>

      <!-- Camera modal -->
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
              <h3 class="text-[17px] font-semibold" style="color: var(--foreground);">拍摄药材</h3>
              <p class="text-[13px]" style="color: var(--muted-foreground);">将药材放入框内并拍照</p>
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
              style="background: var(--brand-500); color: var(--primary-foreground); box-shadow: 0 0 0 4px var(--brand-100, rgba(var(--brand-500-rgb, 59 130 246), 0.2));"
              aria-label="拍照"
              :disabled="!cameraReady"
              @click="captureCameraPhoto"
            >
              <iconify-icon icon="solar:camera-bold" width="26" height="26" />
            </button>
          </div>
        </div>
      </div>

      <!-- Sample Images -->
      <section
        class="mt-4 rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">使用示例图片</h2>
            <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">点击任一中药材图片快速测试</p>
          </div>
          <iconify-icon icon="solar:gallery-outline" width="20" height="20" style="color: var(--muted-foreground);" />
        </div>

        <div class="mt-3 grid grid-cols-3 gap-2">
          <button
            v-for="img in sampleImages"
            :key="img.url"
            type="button"
            class="flex flex-col items-center gap-1 rounded-[12px] p-1.5 transition active:scale-[0.98] disabled:opacity-60"
            style="background: var(--secondary); border: 1px solid var(--border);"
            :disabled="loading"
            :aria-label="`使用示例图片：${img.name}`"
            @click="useSampleImage(img.url, img.name)"
          >
            <img :src="img.url" :alt="img.name" class="h-16 w-full rounded-[8px] object-cover" />
            <span class="w-full truncate text-center text-[11px]" style="color: var(--foreground);">{{ img.name }}</span>
          </button>
        </div>
      </section>

      <!-- Error State -->
      <section
        v-if="viewState === 'error'"
        class="mt-4 flex items-start gap-2 rounded-[12px] px-4 py-3"
        style="background: var(--state-error-surface);"
      >
        <iconify-icon icon="solar:danger-triangle-outline" width="18" height="18" class="mt-0.5 shrink-0" style="color: var(--state-error);" />
        <div class="min-w-0 flex-1">
          <p class="text-[14px] font-medium" style="color: var(--state-error);">识别失败</p>
          <p class="mt-0.5 text-[13px] leading-5" style="color: var(--foreground);">{{ errorMsg || '请稍后重试' }}</p>
        </div>
      </section>

      <!-- Empty State -->
      <section
        v-else-if="viewState === 'empty'"
        class="mt-4 flex flex-col items-center rounded-[19.2px] border p-6 text-center"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <iconify-icon icon="solar:leaf-outline" width="32" height="32" class="mx-auto" style="color: var(--muted-foreground);" />
        <p class="mt-2 text-[14px]" style="color: var(--muted-foreground);">未识别到任何药材，请尝试更清晰的图片</p>
      </section>

      <!-- Results -->
      <section
        v-if="result"
        class="mt-4 rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">识别结果</h2>
          <span class="text-[13px]" style="color: var(--muted-foreground);">{{ result.items.length }} 种药材</span>
        </div>

        <!-- Summary badges -->
        <div class="mt-2 flex flex-wrap items-center gap-2 text-[12px]">
          <span
            v-if="result.duplicatesRemoved.length"
            class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 font-medium"
            style="background: #fff4e6; color: #b25c00;"
          >
            <iconify-icon icon="solar:filter-outline" width="12" height="12" />
            去重 {{ result.duplicatesRemoved.length }} 项
          </span>
          <span
            v-if="result.confidence !== null"
            class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 font-medium"
            style="background: var(--state-success-surface); color: var(--state-success);"
          >
            <iconify-icon icon="solar:check-circle-outline" width="12" height="12" />
            置信度 {{ Math.round((result.confidence ?? 0) * 100) }}%
          </span>
        </div>

        <div class="mt-3 space-y-3">
          <div
            v-for="(herb, idx) in result.items"
            :key="idx"
            class="rounded-[12px] p-3"
            style="background: var(--secondary); border: 1px solid var(--border);"
          >
            <!-- Herb header -->
            <div class="flex items-center gap-2">
              <div
                class="flex h-[40px] w-[40px] shrink-0 items-center justify-center rounded-full"
                style="background: var(--brand-50); color: var(--state-success);"
              >
                <iconify-icon icon="solar:leaf-outline" width="20" height="20" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-[15px] font-medium" style="color: var(--foreground);">{{ herb.herbName }}</p>
                <p class="truncate text-[12px]" style="color: var(--muted-foreground);">来源：{{ herb.source }}</p>
              </div>
              <span
                v-if="herb.confidence !== null"
                class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
                style="background: var(--brand-50); color: var(--brand-500);"
              >{{ Math.round((herb.confidence ?? 0) * 100) }}%</span>
            </div>

            <!-- Properties grid -->
            <div class="mt-2.5 grid grid-cols-2 gap-2 text-[13px] sm:grid-cols-3">
              <div>
                <span style="color: var(--muted-foreground);">性</span>
                <span class="ml-1 font-medium" style="color: var(--foreground);">{{ herb.nature || '—' }}</span>
              </div>
              <div>
                <span style="color: var(--muted-foreground);">味</span>
                <span class="ml-1 font-medium" style="color: var(--foreground);">{{ herb.flavor || '—' }}</span>
              </div>
              <div>
                <span style="color: var(--muted-foreground);">归经</span>
                <span class="ml-1 font-medium" style="color: var(--foreground);">{{ herb.meridian || '—' }}</span>
              </div>
            </div>

            <!-- Efficacy -->
            <p class="mt-2 text-[13px] leading-5" style="color: var(--foreground);">
              <span style="color: var(--muted-foreground);">功效：</span>{{ herb.efficacy || '—' }}
            </p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToast } from '@/composables/useToast'
import { recognizeHerbs, type HerbRecognitionResult } from '@/api/modules/knowledge'

const { warning } = useToast()

// 示例图片：1 张已有甘草 + 4 张新建中药材
const sampleImages = [
  { name: '甘草饮片', url: '/pictures/medication/gancao_herb.svg', category: '中药材' },
  { name: '枸杞子', url: '/pictures/herbs/gouqi_herb.svg', category: '中药材' },
  { name: '黄芪', url: '/pictures/herbs/huangqi_herb.svg', category: '中药材' },
  { name: '当归', url: '/pictures/herbs/danggui_herb.svg', category: '中药材' },
  { name: '金银花', url: '/pictures/herbs/jinyinhua_herb.svg', category: '中药材' },
]

const galleryInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const result = ref<HerbRecognitionResult | null>(null)
const errorMsg = ref('')
const viewState = ref<'idle' | 'empty' | 'error' | 'success'>('idle')

// 相机弹窗状态
const showCameraModal = ref(false)
const cameraVideo = ref<HTMLVideoElement | null>(null)
const cameraReady = ref(false)
const cameraStream = ref<MediaStream | null>(null)

/** 选图：点击不带 capture 的 input */
function triggerGallery() {
  galleryInput.value?.click()
}

const stopCameraStream = () => {
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
  }
  cameraReady.value = false
}

const closeCameraModal = () => {
  showCameraModal.value = false
  stopCameraStream()
}

const startCameraStream = async () => {
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

const triggerCameraScan = () => {
  showCameraModal.value = true
  void startCameraStream()
}

const captureCameraPhoto = async () => {
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

  canvas.toBlob((blob) => {
    if (!blob) return
    const file = new File([blob], `herb-scan-${Date.now()}.jpg`, { type: 'image/jpeg' })
    void runRecognition(file)
  }, 'image/jpeg', 0.92)
}

async function runRecognition(file: File) {
  loading.value = true
  errorMsg.value = ''
  result.value = null
  try {
    const data = await recognizeHerbs(file)
    result.value = data
    viewState.value = data.items.length ? 'success' : 'empty'
  } catch (err: any) {
    errorMsg.value = err?.message ?? '识别失败'
    viewState.value = 'error'
  } finally {
    loading.value = false
  }
}

async function handleUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  await runRecognition(file)
  target.value = ''
}

async function useSampleImage(url: string, name: string) {
  try {
    const res = await fetch(url)
    const blob = await res.blob()
    const file = new File([blob], name, { type: blob.type || 'image/svg+xml' })
    await runRecognition(file)
  } catch (err) {
    warning('示例图片加载失败', err instanceof Error ? err.message : '请稍后重试。')
  }
}
</script>
