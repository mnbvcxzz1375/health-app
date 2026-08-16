<template>
  <section
    class="rounded-[1.9rem] border px-5 py-5 lg:px-6 lg:py-6"
    :style="{
      backgroundColor: 'var(--surface-header)',
      borderColor: 'var(--surface-border)',
      boxShadow: 'var(--elevation-soft)',
    }"
  >
    <p v-if="eyebrow" class="text-[11px] uppercase tracking-[0.12em] text-slate-500">{{ eyebrow }}</p>
    <h1 class="text-[2rem] font-semibold leading-tight text-slate-950 lg:text-[2.2rem]" :class="eyebrow ? 'mt-2' : ''">{{ title }}</h1>
    <p v-if="description" class="mt-2 text-sm leading-6 text-slate-600">{{ description }}</p>

    <div v-if="meta || metaLabel" class="mt-3 flex items-center gap-2">
      <span v-if="metaLabel" class="text-xs text-slate-500">{{ metaLabel }}</span>
      <span v-if="meta" class="text-sm font-semibold text-slate-950">{{ meta }}</span>
    </div>

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
