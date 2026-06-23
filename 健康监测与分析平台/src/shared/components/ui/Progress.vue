<template>
  <div class="h-2 w-full overflow-hidden rounded-full bg-black/10" role="progressbar" :aria-valuenow="value">
    <div class="h-full bg-brand-500 transition-[width]" :style="{ width: `${clamped}%` }" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value: number }>()
const clamped = computed(() => Math.min(100, Math.max(0, props.value ?? 0)))
</script>
