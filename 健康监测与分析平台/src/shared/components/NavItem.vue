<template>
  <RouterLink
    :to="to"
    class="group flex flex-col items-center gap-1.5 rounded-[1.15rem] px-2 py-2 text-[11px] transition duration-200"
    :class="isActive ? 'bg-[color:var(--accent-soft)] text-slate-950' : 'text-slate-500 hover:bg-white hover:text-slate-900'"
  >
    <iconify-icon :icon="icon" width="20" height="20" />
    <span class="leading-none">{{ label }}</span>
    <span class="h-1 w-5 rounded-full bg-[color:var(--accent-strong)] transition-opacity" :class="isActive ? 'opacity-100' : 'opacity-0'" />
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const props = defineProps<{
  to: string
  label: string
  icon: string
}>()

const route = useRoute()
const isActive = computed(() => route.path === props.to || route.path.startsWith(`${props.to}/`))
</script>
