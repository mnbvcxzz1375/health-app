<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    class="inline-flex min-h-10 items-center justify-center gap-2 rounded-[1.15rem] border px-4 py-2 text-sm font-medium transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-45"
    :class="variantClass"
    :style="variantStyle"
    @click="$emit('click', $event)"
  >
    <span
      v-if="loading"
      class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
    />
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    loading?: boolean
  }>(),
  {
    variant: 'primary',
    type: 'button',
    disabled: false,
    loading: false,
  },
)

defineEmits<{
  (e: 'click', ev: MouseEvent): void
}>()

const variantClass = computed(() => {
  switch (props.variant) {
    case 'secondary':
      return 'border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] text-slate-900 hover:border-[color:var(--accent-soft)] hover:bg-[color:var(--surface-secondary)] focus-visible:ring-[color:var(--ring)]'
    case 'ghost':
      return 'border-[color:var(--surface-border)] bg-transparent text-slate-900 hover:bg-[color:var(--surface-secondary)] focus-visible:ring-[color:var(--ring)]'
    case 'danger':
      return 'border-transparent bg-rose-600 text-white hover:bg-rose-700 focus-visible:ring-rose-200'
    case 'primary':
    default:
      return 'border-transparent bg-[color:var(--accent-strong)] text-white hover:brightness-110 focus-visible:ring-[color:var(--ring)]'
  }
})

const variantStyle = computed(() => {
  if (props.variant === 'primary') {
    return {
      color: 'var(--primary-foreground)',
    }
  }

  if (props.variant === 'danger') {
    return {
      color: '#fff7f8',
    }
  }

  return {}
})
</script>

