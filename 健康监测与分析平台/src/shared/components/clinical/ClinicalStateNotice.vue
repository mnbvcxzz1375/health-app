<template>
  <div class="rounded-[1.6rem] border px-4 py-4 shadow-[var(--elevation-soft)]" :class="toneClass">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 inline-flex h-9 w-9 items-center justify-center rounded-full bg-white/85 text-slate-800">
        <iconify-icon :icon="icon" width="18" height="18" />
      </div>

      <div class="min-w-0 flex-1">
        <p class="text-sm font-semibold text-slate-950">{{ title }}</p>
        <p class="mt-1 text-sm leading-6 text-slate-700">{{ description }}</p>

        <button
          v-if="actionLabel"
          type="button"
          class="mt-3 inline-flex items-center rounded-full border border-slate-300/80 bg-white px-3 py-1.5 text-xs font-medium text-slate-900 transition hover:bg-slate-100"
          @click="$emit('action')"
        >
          {{ actionLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StatusNoticeTone } from '@/shared/types/ui'

const props = withDefaults(
  defineProps<{
    tone?: StatusNoticeTone
    title: string
    description: string
    actionLabel?: string
  }>(),
  {
    tone: 'info',
    actionLabel: '',
  },
)

defineEmits<{
  (e: 'action'): void
}>()

const toneClass = computed(() => {
  switch (props.tone) {
    case 'loading':
      return 'border-sky-200 bg-sky-50/90'
    case 'empty':
      return 'border-slate-200 bg-slate-50/95'
    case 'error':
      return 'border-rose-200 bg-rose-50/95'
    case 'success':
      return 'border-emerald-200 bg-emerald-50/95'
    case 'info':
    default:
      return 'border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)]'
  }
})

const icon = computed(() => {
  switch (props.tone) {
    case 'loading':
      return 'solar:refresh-circle-outline'
    case 'empty':
      return 'solar:documents-minimalistic-outline'
    case 'error':
      return 'solar:danger-triangle-outline'
    case 'success':
      return 'solar:check-circle-outline'
    case 'info':
    default:
      return 'solar:info-circle-outline'
  }
})
</script>
