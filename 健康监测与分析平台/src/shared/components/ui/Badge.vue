<template>
  <span class="inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium tracking-[0.08em]" :class="variantClass">
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Variant = 'default' | 'success' | 'warning' | 'danger' | 'info'

const props = withDefaults(
  defineProps<{
    variant?: Variant
  }>(),
  { variant: 'default' },
)

const variantClass = computed(() => {
  switch (props.variant) {
    case 'success':
      return 'border-emerald-200 bg-emerald-50 text-emerald-900'
    case 'warning':
      return 'border-amber-200 bg-amber-50 text-amber-900'
    case 'danger':
      return 'border-rose-200 bg-rose-50 text-rose-900'
    case 'info':
      return 'border-sky-200 bg-sky-50 text-sky-900'
    case 'default':
    default:
      return 'border-[color:var(--surface-border)] bg-white text-slate-700'
  }
})
</script>
