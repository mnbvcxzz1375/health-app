<template>
  <section
    class="rounded-[1.9rem] border px-5 py-5 lg:px-6 lg:py-6"
    :style="{
      backgroundColor: 'var(--surface-header)',
      borderColor: 'var(--surface-border)',
      boxShadow: 'var(--elevation-soft)',
    }"
  >
    <h1 class="text-[2rem] font-semibold leading-tight text-slate-950 lg:text-[2.2rem]">{{ title }}</h1>

    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title: string
  description?: string
  meta?: string
  metaLabel?: string
}>()
</script>
