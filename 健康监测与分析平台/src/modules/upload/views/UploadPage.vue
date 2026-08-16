<template>
  <div class="apple-upload pb-6">
    <div class="mx-auto max-w-[420px] px-4 pt-4">
      <!-- Page Header -->
      <header>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground); line-height: 1.15;">上传分析</h1>
        <p class="mt-1.5 text-[15px]" style="color: var(--muted-foreground);">上传健康资料，AI 自动生成分析报告</p>
      </header>

      <!-- Material Type Selection Card -->
      <section
        class="mt-5 rounded-[19.2px] border p-5"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <span class="text-[13px] font-semibold uppercase tracking-[0.06em]" style="color: var(--muted-foreground);">资料类型</span>
        <div class="mt-3 grid grid-cols-2 gap-3">
          <button
            v-for="item in typeOptions"
            :key="item.key"
            type="button"
            class="rounded-[12.8px] border p-4 text-left transition active:scale-[0.98]"
            :style="uploadType === item.key
              ? { borderColor: 'var(--brand-500)', background: 'color-mix(in srgb, var(--brand-500) 8%, var(--card))' }
              : { borderColor: 'var(--border)', background: 'var(--card)' }"
            :disabled="isSubmitting"
            @click="selectType(item.key)"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-[10px]" style="background: var(--brand-50);">
              <iconify-icon :icon="item.icon" width="24" height="24" style="color: var(--brand-500);" />
            </div>
            <div class="mt-3 text-[15px] font-semibold" style="color: var(--foreground);">{{ item.label }}</div>
            <div class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">{{ item.hint }}</div>
          </button>
        </div>
      </section>

      <!-- Upload Area (file type: image / lab) -->
      <section v-if="isFileType" class="mt-4">
        <input
          id="upload-file-input"
          ref="fileInput"
          data-testid="upload-file-input"
          type="file"
          class="hidden"
          :accept="fileAccept"
          multiple
          @change="onFileChange"
        />
        <label
          for="upload-file-input"
          class="block cursor-pointer rounded-[19.2px] p-8 text-center transition active:scale-[0.99]"
          style="border: 2px dashed var(--brand-300); background: color-mix(in srgb, var(--brand-500) 8%, var(--card));"
        >
          <iconify-icon icon="solar:cloud-upload-outline" width="32" height="32" class="mx-auto" style="color: var(--brand-500);" />
          <div class="mt-3 text-[15px] font-semibold" style="color: var(--foreground);">
            {{ selectedFiles.length ? '重新选择文件' : '选择文件' }}
          </div>
          <div class="mt-1 text-[13px]" style="color: var(--muted-foreground);">{{ fileHint }}</div>
        </label>

        <!-- File list -->
        <div
          v-if="selectedFiles.length"
          class="mt-3 rounded-[19.2px] border p-3"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center justify-between gap-3 px-1 pb-2">
            <p class="text-[13px] font-semibold" style="color: var(--foreground);">已选择 {{ selectedFiles.length }} 个文件</p>
            <button
              type="button"
              class="text-[13px] font-medium transition active:opacity-70"
              style="color: var(--brand-500);"
              @click="clearFiles"
            >清空</button>
          </div>
          <div
            v-for="file in selectedFiles"
            :key="`${file.name}_${file.lastModified}`"
            class="flex items-center gap-3 py-2"
          >
            <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]" style="background: var(--brand-50);">
              <iconify-icon icon="solar:file-outline" width="16" height="16" style="color: var(--brand-500);" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="truncate text-[14px] font-medium" style="color: var(--foreground);">{{ file.name }}</div>
              <div class="text-[12px]" style="color: var(--muted-foreground);">{{ formatFileSize(file.size) }}</div>
            </div>
            <iconify-icon icon="solar:check-circle-bold" width="20" height="20" class="shrink-0" style="color: var(--state-success);" />
          </div>
        </div>

        <!-- Sample images -->
        <div v-if="visibleSampleImages.length" class="mt-4">
          <span class="text-[13px] font-semibold uppercase tracking-[0.06em]" style="color: var(--muted-foreground);">示例图片</span>
          <div class="mt-2 grid grid-cols-2 gap-3">
            <button
              v-for="img in visibleSampleImages"
              :key="img.url"
              type="button"
              class="overflow-hidden rounded-[12.8px] border text-left transition active:scale-[0.98]"
              style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
              :aria-label="`使用示例图片：${img.name}`"
              @click="useSampleImage(img.url, img.name)"
            >
              <img :src="img.url" :alt="img.name" class="h-[90px] w-full object-cover" />
              <div class="p-2 text-[12px]" style="color: var(--muted-foreground);">{{ img.name }}</div>
            </button>
          </div>
        </div>
      </section>

      <!-- Text Input Area (text / symptom) -->
      <section v-else-if="isTextType" class="mt-4">
        <textarea
          v-model="text"
          rows="7"
          class="w-full resize-none rounded-[19.2px] border p-4 text-[15px] outline-none transition focus:border-[color:var(--ring)] focus:outline-none focus:ring-2 focus:ring-[color:var(--ring)]"
          style="background: var(--card); border-color: var(--border); color: var(--foreground);"
          placeholder="请输入需要分析的报告内容或症状描述"
        />
        <div class="mt-1.5 text-[12px]" style="color: var(--muted-foreground);">建议至少输入 10 个字</div>
      </section>

      <!-- Empty state -->
      <section
        v-else
        class="mt-4 rounded-[19.2px] border p-6 text-center"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <iconify-icon icon="solar:upload-outline" width="32" height="32" class="mx-auto" style="color: var(--muted-foreground);" />
        <p class="mt-2 text-[14px]" style="color: var(--muted-foreground);">请先选择资料类型</p>
      </section>

      <!-- CTA: 开始分析 -->
      <button
        type="button"
        class="mt-4 flex h-12 w-full items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
        style="background: var(--primary); color: var(--primary-foreground);"
        :disabled="isSubmitting"
        @click="handleSubmitClick"
      >
        <iconify-icon icon="solar:magic-stick-3-bold-duotone" width="20" height="20" />
        {{ isSubmitting ? '分析中…' : '开始分析' }}
      </button>

      <!-- Reset button -->
      <button
        v-if="uploadType"
        type="button"
        class="mt-2 w-full py-2 text-center text-[13px] transition active:opacity-70"
        style="color: var(--muted-foreground);"
        :disabled="isSubmitting"
        @click="reset"
      >重置</button>

      <!-- Analysis Progress -->
      <section
        v-if="status !== 'idle'"
        ref="progressSectionRef"
        class="mt-4 rounded-[19.2px] border p-5"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="mb-3 flex items-center justify-between">
          <span class="flex items-center gap-2 text-[15px] font-semibold" style="color: var(--foreground);">
            <iconify-icon
              :icon="status === 'complete' ? 'solar:check-circle-bold' : 'solar:spinner-round-line-bold-duotone'"
              width="16"
              height="16"
              :style="status === 'complete' ? { color: 'var(--state-success)' } : { color: 'var(--brand-500)' }"
            />
            {{ statusText }}
          </span>
          <span class="text-[15px] font-semibold tabular-nums" style="color: var(--brand-500);">{{ progress }}%</span>
        </div>
        <div class="w-full overflow-hidden rounded-full" style="height: 6px; background: var(--background-200);">
          <div
            style="height: 100%; background: var(--brand-500); border-radius: 9999px; transition: width 0.3s ease;"
            :style="{ width: `${progress}%` }"
          ></div>
        </div>
        <div class="mt-2 text-[13px]" style="color: var(--muted-foreground);">
          {{ status === 'uploading' ? '正在上传资料…' : status === 'analyzing' ? '正在识别报告关键指标…' : '分析已完成' }}
        </div>
      </section>

      <!-- Analysis Report -->
      <section
        v-if="status === 'complete' && report"
        ref="reportSectionRef"
        class="mt-4 rounded-[19.2px] border p-5 transition-all duration-500"
        :class="reportRevealActive ? 'translate-y-2 scale-[1.01]' : ''"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <!-- 骨龄评估单独走 BoneAgeResultCard -->
        <div v-if="uploadType === 'bone' && boneAgeResult" class="mb-4">
          <BoneAgeResultCard
            :task-id="taskId || ''"
            :result="boneAgeResult"
            :source="boneAgeSource"
            :estimated-at="boneAgeEstimatedAt"
          />
          <div class="mt-4 flex gap-2">
            <button
              type="button"
              class="flex h-12 flex-1 items-center justify-center rounded-full border text-[14px] font-medium transition active:scale-[0.98]"
              style="background: var(--card); border-color: var(--border); color: var(--foreground);"
              @click="viewBoneAgeHistory"
            >查看历史记录</button>
            <button
              type="button"
              class="flex h-12 flex-1 items-center justify-center rounded-full text-[14px] font-medium transition active:scale-[0.98]"
              style="background: var(--secondary); color: var(--foreground);"
              @click="goHome"
            >返回总览</button>
          </div>
        </div>
        <template v-else>
        <!-- Title + risk badge -->
        <div class="flex items-center justify-between gap-2">
          <div class="min-w-0">
            <h2 class="text-[19px] font-semibold" style="color: var(--foreground);">分析报告</h2>
            <p v-if="report.title" class="mt-1 truncate text-[13px]" style="color: var(--muted-foreground);">
              {{ report.title }}
            </p>
          </div>
          <span
            class="whitespace-nowrap rounded-full px-3 py-1 text-[12px] font-semibold"
            :style="riskBadgeStyle"
          >{{ report.riskLevel }}</span>
        </div>

        <!-- Summary -->
        <p class="mt-3 text-[15px] leading-relaxed" style="color: var(--foreground);">{{ report.summary }}</p>

        <!-- 康复重点 -->
        <div class="mt-3 flex items-center gap-2">
          <iconify-icon icon="solar:target-outline" width="16" height="16" class="shrink-0" style="color: var(--brand-500);" />
          <span class="text-[14px] font-semibold" style="color: var(--foreground);">康复重点：{{ report.rehabFocus }}</span>
        </div>

        <!-- 关注点 -->
        <div class="mt-4 rounded-[12.8px] p-4" style="background: var(--background-100);">
          <div class="mb-2 flex items-center gap-2">
            <iconify-icon icon="solar:eye-outline" width="16" height="16" style="color: var(--muted-foreground);" />
            <span class="text-[14px] font-semibold" style="color: var(--foreground);">关注点</span>
          </div>
          <ul class="space-y-2">
            <li
              v-for="(point, idx) in report.points"
              :key="`point-${idx}`"
              class="flex items-start gap-2 text-[14px]"
              style="color: var(--foreground);"
            >
              <span class="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full" :style="pointDotStyle(idx, 'point')"></span>
              <span>{{ point }}</span>
            </li>
          </ul>
        </div>

        <!-- 建议 -->
        <div class="mt-3 rounded-[12.8px] p-4" style="background: var(--background-100);">
          <div class="mb-2 flex items-center gap-2">
            <iconify-icon icon="solar:lightbulb-outline" width="16" height="16" style="color: var(--muted-foreground);" />
            <span class="text-[14px] font-semibold" style="color: var(--foreground);">建议</span>
          </div>
          <ul class="space-y-2">
            <li
              v-for="(advice, idx) in report.advice"
              :key="`advice-${idx}`"
              class="flex items-start gap-2 text-[14px]"
              style="color: var(--foreground);"
            >
              <span class="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full" style="background: var(--brand-500);"></span>
              <span>{{ advice }}</span>
            </li>
          </ul>
        </div>

        <!-- 后续观察 -->
        <div class="mt-3 rounded-[12.8px] p-4" style="background: var(--background-100);">
          <div class="mb-2 flex items-center gap-2">
            <iconify-icon icon="solar:telescope-outline" width="16" height="16" style="color: var(--muted-foreground);" />
            <span class="text-[14px] font-semibold" style="color: var(--foreground);">后续观察</span>
          </div>
          <ul class="space-y-2">
            <li
              v-for="(item, idx) in report.followUp"
              :key="`follow-${idx}`"
              class="flex items-start gap-2 text-[14px]"
              style="color: var(--foreground);"
            >
              <span class="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full" style="background: var(--chart-4);"></span>
              <span>{{ item }}</span>
            </li>
          </ul>
          <div
            v-if="report.caution"
            class="mt-3 flex items-start gap-2 rounded-[9.6px] p-3"
            style="background: var(--state-error-surface);"
          >
            <iconify-icon icon="solar:danger-triangle-outline" width="16" height="16" class="mt-0.5 shrink-0" style="color: var(--state-error);" />
            <span class="text-[12px]" style="color: var(--state-error);">{{ report.caution }}</span>
          </div>
        </div>

        <!-- Action buttons -->
        <div class="mt-4 space-y-2">
          <button
            v-if="!saved"
            type="button"
            class="flex h-12 w-full items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
            style="background: var(--primary); color: var(--primary-foreground);"
            :disabled="savingReport"
            @click="handleSaveReport"
          >
            <iconify-icon icon="solar:dumbbell-outline" width="20" height="20" />
            {{ savingReport ? '生成中…' : '保留并生成康复计划' }}
          </button>
          <button
            v-if="!saved"
            type="button"
            class="flex h-12 w-full items-center justify-center rounded-full border text-[15px] font-medium transition active:scale-[0.98] disabled:opacity-60"
            style="background: var(--card); border-color: var(--border); color: var(--foreground);"
            :disabled="savingReport"
            @click="handleDiscardReport"
          >不保留</button>
          <div
            v-if="saved"
            class="flex items-center justify-center gap-2 rounded-full py-3 text-[14px] font-medium"
            style="background: var(--state-success-surface); color: var(--state-success);"
          >
            <iconify-icon icon="solar:check-circle-bold" width="18" height="18" />
            报告已保留
          </div>
          <div class="flex gap-2">
            <button
              v-if="saved"
              type="button"
              class="flex h-12 flex-1 items-center justify-center rounded-full border text-[14px] font-medium transition active:scale-[0.98]"
              style="background: var(--card); border-color: var(--border); color: var(--foreground);"
              @click="goRehab"
            >查看康复计划</button>
            <button
              type="button"
              class="flex h-12 flex-1 items-center justify-center rounded-full text-[14px] font-medium transition active:scale-[0.98]"
              style="background: var(--secondary); color: var(--foreground);"
              @click="goHome"
            >返回总览</button>
          </div>
        </div>

        <!-- Loading state -->
        <div
          v-if="savingReport"
          class="mt-3 rounded-[12.8px] p-3 text-center text-[13px]"
          style="background: var(--background-100); color: var(--muted-foreground);"
        >
          正在保存并生成康复计划，请稍候…
        </div>

        <!-- Error state -->
        <div
          v-if="draftGenerationError"
          class="mt-3 rounded-[12.8px] p-3 text-[13px]"
          style="background: var(--state-error-surface); color: var(--state-error);"
        >
          康复计划草案生成失败：{{ draftGenerationError }}
        </div>
        </template>
      </section>

      <!-- Rehab Plan Draft (preserved feature, not in design mockup) -->
      <section
        v-if="saved && rehabPlanDraft"
        ref="draftSectionRef"
        class="mt-4 rounded-[19.2px] border p-5 transition-all duration-500"
        :class="draftRevealActive ? 'translate-y-2 scale-[1.01]' : ''"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[19px] font-semibold" style="color: var(--foreground);">康复计划草案</h2>

        <!-- Plan summary -->
        <div class="mt-3 grid grid-cols-2 gap-2">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">重点</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ rehabPlanDraft.summary.focus }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">频率</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ rehabPlanDraft.summary.frequency }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">时长</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ rehabPlanDraft.summary.duration }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">强度</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ rehabPlanDraft.summary.intensity }}</p>
          </div>
        </div>

        <!-- Exercise list -->
        <div class="mt-4">
          <div class="flex items-center justify-between">
            <p class="text-[15px] font-semibold" style="color: var(--foreground);">动作清单</p>
            <p class="text-[13px]" style="color: var(--muted-foreground);">共 {{ rehabPlanDraft.exercises.length }} 项</p>
          </div>
          <div class="mt-2 space-y-2">
            <article
              v-for="exercise in rehabPlanDraft.exercises"
              :key="`${exercise.mode}_${exercise.name}`"
              class="rounded-[12.8px] border p-3"
              style="background: var(--card); border-color: var(--border);"
            >
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-[14px] font-semibold" style="color: var(--foreground);">{{ exercise.name }}</p>
                <span
                  class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                  :style="exercise.mode === 'generated'
                    ? { background: '#fff4e6', color: '#b25c00' }
                    : { background: 'var(--secondary)', color: 'var(--muted-foreground)' }"
                >{{ exercise.mode === 'generated' ? '新增动作' : '复用动作库' }}</span>
                <span class="rounded-full px-2 py-0.5 text-[11px] font-medium" style="background: var(--secondary); color: var(--muted-foreground);">{{ exercise.level }}</span>
              </div>
              <p class="mt-1.5 text-[13px]" style="color: var(--muted-foreground);">{{ exercise.focus }}</p>
              <div class="mt-2 flex flex-wrap gap-1.5 text-[11px]">
                <span class="rounded-full px-2.5 py-1" style="background: var(--secondary); color: var(--muted-foreground);">{{ exercise.category }}</span>
                <span class="rounded-full px-2.5 py-1" style="background: var(--secondary); color: var(--muted-foreground);">{{ exercise.duration }}</span>
                <span class="rounded-full px-2.5 py-1" style="background: var(--secondary); color: var(--muted-foreground);">{{ exercise.minutes }} 分钟</span>
              </div>
            </article>
          </div>
        </div>

        <!-- Reminder -->
        <div class="mt-3 rounded-[12.8px] p-3" style="background: var(--background-100);">
          <p class="text-[13px] font-semibold" style="color: var(--foreground);">统一提醒</p>
          <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">
            {{ rehabPlanDraft.reminder.time }} · {{ rehabPlanDraft.reminder.days.join('、') }} ·
            {{ rehabPlanDraft.reminder.pushEnabled ? '系统通知开启' : '系统通知关闭' }}
          </p>
        </div>

        <!-- Apply buttons -->
        <div class="mt-4 flex gap-2">
          <button
            type="button"
            class="flex h-12 flex-1 items-center justify-center rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
            style="background: var(--primary); color: var(--primary-foreground);"
            :disabled="applyingPlan"
            @click="handleApplyPlanDraft"
          >{{ applyingPlan ? '应用中…' : '应用到康复计划' }}</button>
          <button
            type="button"
            class="flex h-12 flex-1 items-center justify-center rounded-full border text-[14px] font-medium transition active:scale-[0.98]"
            style="background: var(--card); border-color: var(--border); color: var(--foreground);"
            :disabled="applyingPlan"
            @click="goRehab"
          >稍后确认</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { applyRehabPlanDraft, type RehabPlanDraft } from '@/api/modules/rehab'
import {
  createAnalyzeTask,
  discardAnalyzeReport,
  estimateBoneAge,
  getAnalyzeResult,
  saveAnalyzeReport,
  type AnalyzeReport,
  type BoneAgeResult,
} from '@/api/modules/upload'
import { useToast } from '@/composables/useToast'
import { needsModelImageTranscode, normalizeFilesForModel } from '@/shared/utils/modelImage'
import BoneAgeResultCard from './BoneAgeResultCard.vue'

type UploadType = 'image' | 'lab' | 'text' | 'symptom' | 'bone' | null
type UploadStatus = 'idle' | 'uploading' | 'analyzing' | 'complete'

const router = useRouter()
const { success, info, warning, error } = useToast()

const typeOptions = [
  { key: 'image' as const, label: '影像资料', hint: '图片、扫描件、影像截图', icon: 'solar:gallery-outline' },
  { key: 'lab' as const, label: '化验报告', hint: 'PDF、图片、报告文件', icon: 'solar:document-text-outline' },
  { key: 'text' as const, label: '文字报告', hint: '粘贴检查结论或病历', icon: 'solar:document-add-outline' },
  { key: 'symptom' as const, label: '症状描述', hint: '补充近期状态和感受', icon: 'solar:clipboard-list-outline' },
  { key: 'bone' as const, label: '骨龄评估', hint: '左手腕 X 光片', icon: 'solar:bone-outline' },
]

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
const progressSectionRef = ref<HTMLElement | null>(null)
const reportRevealActive = ref(false)
const draftRevealActive = ref(false)

// 骨龄评估专用状态（同步调用，不走 task 轮询）
const boneAgeResult = ref<BoneAgeResult | null>(null)
const boneAgeSource = ref<string>('')
const boneAgeEstimatedAt = ref<string>('')

const isFileType = computed(() => uploadType.value === 'image' || uploadType.value === 'lab' || uploadType.value === 'bone')
const isTextType = computed(() => uploadType.value === 'text' || uploadType.value === 'symptom')
const isSubmitting = computed(() => status.value === 'uploading' || status.value === 'analyzing')

const fileAccept = computed(() => {
  if (uploadType.value === 'image') return 'image/*,.dcm'
  if (uploadType.value === 'bone') return 'image/*,.dcm'
  return '.pdf,image/*,.txt'
})
const fileHint = computed(() => {
  if (uploadType.value === 'image') return '支持 JPG、PNG、PDF，单文件最大 20MB'
  if (uploadType.value === 'bone') return '支持 JPG、PNG、DICOM，建议左手腕正位 X 光片'
  return '支持 PDF、JPG、PNG，单文件最大 20MB'
})

// 示例图片：按资料类型分组
const sampleImages = [
  { name: '血常规化验单', url: '/pictures/upload/blood_test_report.svg', category: '化验报告', type: 'lab' as const },
  { name: '胸部 CT 报告', url: '/pictures/upload/ct_report.svg', category: '影像报告', type: 'image' as const },
  { name: '健康体检摘要', url: '/pictures/upload/health_checkup.svg', category: '体检报告', type: 'lab' as const },
  { name: '心电图报告', url: '/pictures/upload/ecg_report.svg', category: '检查报告', type: 'image' as const },
]

const visibleSampleImages = computed(() => {
  if (uploadType.value === 'image') return sampleImages.filter((i) => i.type === 'image')
  if (uploadType.value === 'lab') return sampleImages.filter((i) => i.type === 'lab' || i.type === 'image')
  return []
})
const statusText = computed(() => {
  if (status.value === 'uploading') return '正在上传资料'
  if (status.value === 'analyzing') return '正在调用大模型分析'
  if (status.value === 'complete') return saved.value ? '报告已保留' : '分析已完成'
  return '等待开始'
})

const riskBadgeStyle = computed(() => {
  if (!report.value) return { background: 'var(--secondary)', color: 'var(--muted-foreground)' }
  if (report.value.riskLevel === '高风险') return { background: 'var(--state-error-surface)', color: 'var(--state-error)' }
  if (report.value.riskLevel === '中等风险') return { background: '#fff4e6', color: '#b25c00' }
  return { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
})

const pointDotStyle = (_idx: number, _type: string) => {
  // Alternate colors for visual variety
  const colors = ['var(--state-error)', 'var(--chart-3)', 'var(--state-success)']
  return { background: colors[_idx % colors.length] }
}

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

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
  boneAgeResult.value = null
  boneAgeSource.value = ''
  boneAgeEstimatedAt.value = ''
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

async function useSampleImage(url: string, name: string) {
  try {
    const res = await fetch(url)
    const blob = await res.blob()
    const file = new File([blob], name, { type: blob.type || 'image/svg+xml' })
    selectedFiles.value = [file]
    success('示例文件已加载', '点击"开始分析"继续。')
  } catch (err) {
    error('示例文件加载失败', err instanceof Error ? err.message : '请稍后重试。')
  }
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

  // 骨龄评估走同步调用，不走 createAnalyzeTask + 轮询流程
  if (uploadType.value === 'bone') {
    if (!selectedFiles.value.length) {
      throw new Error('请先上传左手腕 X 光图片。')
    }
    const file = selectedFiles.value[0]
    progress.value = 45
    status.value = 'analyzing'
    const resp = await estimateBoneAge(file)
    taskId.value = resp.taskId
    boneAgeResult.value = resp.result
    boneAgeSource.value = resp.source
    boneAgeEstimatedAt.value = resp.estimatedAt ?? ''
    status.value = 'complete'
    progress.value = 100
    success('骨龄评估完成', `估算骨龄 ${resp.result.estimatedAgeYears ?? '--'} 岁。`)
    await revealReportSection()
    return
  }

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

const viewBoneAgeHistory = () => {
  // 暂未单独的历史页，先提示用户
  info('历史记录', '骨龄评估历史记录可通过"我的-健康档案"查看（开发中）。')
}
</script>
