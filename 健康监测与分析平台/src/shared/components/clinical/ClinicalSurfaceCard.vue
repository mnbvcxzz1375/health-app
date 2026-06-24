<template>
  <section class="rounded-[1.75rem] border border-white/30 bg-white/40 backdrop-blur-xl shadow-[var(--elevation-soft)]">
    <div
      v-if="title || (description && !$slots.default) || $slots.headerRight"
      class="flex items-start justify-between gap-3 border-b border-[color:var(--surface-border)] px-4 py-4 lg:px-5"
    >
      <div class="min-w-0">
        <h2 v-if="title" class="text-lg font-semibold text-slate-950">{{ title }}</h2>
        <p v-if="description && !$slots.default" class="mt-2 text-sm leading-6 text-slate-600">{{ description }}</p>
      </div>

      <div v-if="$slots.headerRight" class="shrink-0">
        <slot name="headerRight" />
      </div>
    </div>

    <div class="px-4 py-4 lg:px-5">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    eyebrow?: string
    title?: string
    description?: string
  }>(),
  {
    eyebrow: '',
    title: '',
    description: '',
  },
)
</script>
