<template>
  <RouterLink
    v-if="visible"
    :to="to"
    class="fixed bottom-24 right-4 z-40 inline-flex items-center gap-2 rounded-full border border-[color:var(--surface-border)] bg-[color:var(--accent-strong)] px-4 py-3 text-sm font-semibold text-white shadow-[var(--elevation-strong)] transition hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--ring)] lg:bottom-6 lg:right-6"
    aria-label="打开智能助手"
  >
    <iconify-icon icon="solar:chat-round-dots-outline" width="20" height="20" />
    <span>智能助手</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(
  defineProps<{
    visible?: boolean
    to?: string
  }>(),
  {
    visible: true,
    to: '/assistant',
  },
)
</script>
