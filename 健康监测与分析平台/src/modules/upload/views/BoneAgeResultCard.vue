<template>
  <section
    ref="rootRef"
    class="rounded-[19.2px] border p-5 transition-all duration-500"
    :class="revealActive ? 'translate-y-2 scale-[1.01]' : ''"
    style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
  >
    <!-- Header -->
    <div class="flex items-center justify-between gap-2">
      <h2 class="text-[19px] font-semibold" style="color: var(--foreground);">骨龄评估结果</h2>
      <span
        v-if="source === 'llm_fallback'"
        class="whitespace-nowrap rounded-full px-3 py-1 text-[11px] font-semibold"
        style="background: #fff4e6; color: #b25c00;"
      >LLM 估算</span>
      <span
        v-else
        class="whitespace-nowrap rounded-full px-3 py-1 text-[11px] font-semibold"
        style="background: var(--state-success-surface); color: var(--state-success);"
      >模型推理</span>
    </div>

    <!-- Big number: estimated bone age -->
    <div class="mt-4 flex items-end gap-3">
      <span class="text-[56px] font-bold leading-none tabular-nums" style="color: var(--brand-500);">
        {{ result.estimatedAgeYears !== null ? result.estimatedAgeYears.toFixed(1) : '--' }}
      </span>
      <span class="mb-2 text-[18px] font-medium" style="color: var(--muted-foreground);">岁</span>
    </div>

    <!-- Confidence bar -->
    <div class="mt-3">
      <div class="flex items-center justify-between text-[12px]" style="color: var(--muted-foreground);">
        <span>置信度</span>
        <span class="tabular-nums">{{ confidencePercent }}%</span>
      </div>
      <div class="mt-1 w-full overflow-hidden rounded-full" style="height: 4px; background: var(--background-200);">
        <div
          style="height: 100%; background: var(--brand-500); border-radius: 9999px; transition: width 0.6s ease;"
          :style="{ width: `${confidencePercent}%` }"
        ></div>
      </div>
    </div>

    <!-- Growth plate stage -->
    <div class="mt-4 rounded-[12.8px] p-4" style="background: var(--background-100);">
      <div class="mb-1 flex items-center gap-2">
        <iconify-icon icon="solar:bone-outline" width="16" height="16" style="color: var(--muted-foreground);" />
        <span class="text-[14px] font-semibold" style="color: var(--foreground);">骨骺分期</span>
      </div>
      <p class="text-[15px] font-medium" style="color: var(--foreground);">
        {{ result.growthPlateStage || '暂无分期信息' }}
      </p>
    </div>

    <!-- Malformed indicators -->
    <div
      v-if="result.malformedIndicators && result.malformedIndicators.length"
      class="mt-3 rounded-[12.8px] p-4"
      style="background: var(--background-100);"
    >
      <div class="mb-2 flex items-center gap-2">
        <iconify-icon icon="solar:eye-outline" width="16" height="16" style="color: var(--muted-foreground);" />
        <span class="text-[14px] font-semibold" style="color: var(--foreground);">异常指标</span>
      </div>
      <ul class="space-y-2">
        <li
          v-for="(item, idx) in result.malformedIndicators"
          :key="`indicator-${idx}`"
          class="flex items-start gap-2 text-[14px]"
          style="color: var(--foreground);"
        >
          <span class="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full" style="background: var(--state-error);"></span>
          <span>{{ item }}</span>
        </li>
      </ul>
    </div>

    <!-- Disclaimer -->
    <div
      class="mt-3 flex items-start gap-2 rounded-[9.6px] p-3"
      style="background: var(--state-error-surface);"
    >
      <iconify-icon icon="solar:danger-triangle-outline" width="16" height="16" class="mt-0.5 shrink-0" style="color: var(--state-error);" />
      <span class="text-[12px] leading-relaxed" style="color: var(--state-error);">{{ result.disclaimer }}</span>
    </div>

    <!-- Task ID + timestamp -->
    <div class="mt-3 flex items-center justify-between text-[11px]" style="color: var(--muted-foreground);">
      <span>任务编号：{{ taskId }}</span>
      <span v-if="estimatedAt">{{ formatTime(estimatedAt) }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { BoneAgeResult } from '@/api/modules/upload'

const props = defineProps<{
  taskId: string
  result: BoneAgeResult
  source: string
  estimatedAt?: string
}>()

const emit = defineEmits<{ revealed: [] }>()

const rootRef = ref<HTMLElement | null>(null)
const revealActive = ref(false)

const confidencePercent = computed(() => {
  if (props.result.confidence === null || props.result.confidence === undefined) return 0
  return Math.round(props.result.confidence * 100)
})

const formatTime = (iso: string) => {
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return iso
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return iso
  }
}

onMounted(() => {
  // 触发一次 reveal 动画
  requestAnimationFrame(() => {
    rootRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    revealActive.value = true
    window.setTimeout(() => {
      revealActive.value = false
      emit('revealed')
    }, 1800)
  })
})
</script>
