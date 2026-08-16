<template>
  <RouterLink
    :to="to"
    class="group flex flex-col items-center justify-center gap-0.5 transition-colors duration-150 motion-reduce:transition-none"
    :class="[
      variant === 'sidebar'
        ? 'flex-row gap-2.5 rounded-xl px-3 py-2.5 text-[15px]'
        : 'min-h-[44px] min-w-[44px] py-1 text-[10px]',
      isActive
        ? 'text-[color:var(--brand-500)] font-semibold'
        : 'text-[color:var(--muted-foreground)] font-medium hover:text-[color:var(--foreground)]',
    ]"
  >
    <iconify-icon :icon="icon" :width="variant === 'sidebar' ? 20 : 22" :height="variant === 'sidebar' ? 20 : 22" />
    <span class="whitespace-nowrap leading-none">{{ label }}</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const props = withDefaults(
  defineProps<{
    to: string
    label: string
    icon: string
    variant?: 'sidebar' | 'bar'
  }>(),
  { variant: 'bar' },
)

const route = useRoute()
const isActive = computed(() => route.path === props.to || route.path.startsWith(`${props.to}/`))
</script>
