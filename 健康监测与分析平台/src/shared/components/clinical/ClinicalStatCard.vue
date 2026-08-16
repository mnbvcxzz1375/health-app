<template>
  <component
    :is="to ? RouterLink : 'article'"
    :to="to"
    class="rounded-[1.6rem] border px-4 py-3.5 shadow-[var(--elevation-soft)] backdrop-blur-xl transition"
    :class="[toneClass, to ? 'cursor-pointer hover:border-[color:var(--accent-strong)] hover:shadow-[var(--elevation-strong)]' : '']"
    :tabindex="to ? 0 : undefined"
    :role="to ? 'link' : undefined"
  >
    <div class="flex items-start justify-between gap-2">
      <div class="min-w-0">
        <p class="text-[11px] uppercase tracking-[0.1em] text-slate-500">{{ label }}</p>
        <p class="mt-2 truncate text-xl font-semibold text-slate-950">{{ value }}</p>
        <p v-if="hint" class="mt-2 text-xs leading-5 text-slate-600">{{ hint }}</p>
      </div>

      <iconify-icon
        v-if="icon"
        :icon="icon"
        width="18"
        height="18"
        class="mt-1 text-slate-500"
      />
    </div>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { RiskTone } from '@/shared/types/ui'

const props = withDefaults(
  defineProps<{
    label: string
    value: string
    hint?: string
    icon?: string
    tone?: RiskTone
    to?: string
  }>(),
  {
    hint: '',
    icon: '',
    tone: 'default',
    to: '',
  },
)

const toneClass = computed(() => {
  switch (props.tone) {
    case 'success':
      return 'border-emerald-200/60 bg-emerald-50/40'
    case 'warning':
      return 'border-amber-200/60 bg-amber-50/40'
    case 'danger':
      return 'border-rose-200/60 bg-rose-50/40'
    case 'info':
      return 'border-sky-200/60 bg-sky-50/40'
    default:
      return 'border-white/40 bg-white/30'
  }
})
</script>
