<template>
  <div
    class="sticky top-0 z-20 -mx-4 -mt-4 border-b lg:-mx-6 lg:-mt-6"
    :style="{
      backgroundColor: 'var(--surface-header)',
      borderColor: 'var(--surface-border)',
      boxShadow: '0 10px 28px rgba(15, 23, 42, 0.08)',
    }"
  >
    <nav
      class="mx-auto px-3 py-3 lg:px-4"
      :style="{ width: contentWidth }"
      aria-label="功能页导航"
    >
      <div class="grid grid-cols-[max-content_minmax(0,1fr)_max-content] items-center gap-2">
        <button
          type="button"
          class="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-slate-700 transition hover:text-slate-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--ring)] focus-visible:ring-offset-2"
          @click="goBack"
        >
        <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
        {{ backLabel }}
        </button>

        <p class="truncate px-2 text-center text-xl font-semibold text-slate-950 lg:text-base">{{ title }}</p>

        <div data-testid="feature-nav-spacer" aria-hidden="true" class="h-10 w-[72px] shrink-0" />
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    title: string
    backTo: string
    backLabel?: string
    contentWidth?: string
  }>(),
  {
    backLabel: '返回',
    contentWidth: '100%',
  },
)

const router = useRouter()

const goBack = () => {
  void router.push(props.backTo)
}
</script>
