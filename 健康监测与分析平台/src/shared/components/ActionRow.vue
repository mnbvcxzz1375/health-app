<template>
  <button
    class="flex w-full items-center justify-between rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] px-3 py-3 text-left shadow-[var(--elevation-soft)] transition duration-200 hover:border-[color:var(--accent-soft)] hover:bg-[color:var(--surface-secondary)]"
    type="button"
    @click="$emit('click')"
  >
    <div class="flex items-center gap-2.5">
      <iconify-icon :icon="iconName" width="18" height="18" class="text-slate-700" />
      <div class="min-w-0">
        <p class="text-sm font-medium text-slate-950">{{ title }}</p>
        <p v-if="desc" class="mt-1 text-xs text-slate-600">{{ desc }}</p>
      </div>
    </div>

    <iconify-icon icon="solar:alt-arrow-right-outline" width="16" height="16" class="text-slate-400" />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    desc?: string
    icon: 'Database' | 'Shield' | 'FileText' | 'HelpCircle'
  }>(),
  {
    desc: '',
  },
)

defineEmits<{ (e: 'click'): void }>()

const iconName = computed(() => {
  switch (props.icon) {
    case 'Database':
      return 'solar:database-outline'
    case 'Shield':
      return 'solar:shield-outline'
    case 'FileText':
      return 'solar:file-text-outline'
    case 'HelpCircle':
    default:
      return 'solar:question-circle-outline'
  }
})
</script>
