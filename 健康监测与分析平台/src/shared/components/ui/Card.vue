<template>
  <div class="overflow-hidden rounded-3xl border border-black/10 bg-white shadow-[0_16px_38px_-30px_rgba(0,0,0,.45)] transition duration-200" :class="className">
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{ className?: string }>()
</script>
