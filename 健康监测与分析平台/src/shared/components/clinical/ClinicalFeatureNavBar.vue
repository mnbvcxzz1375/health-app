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
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition active:scale-95"
          style="background: var(--secondary); color: var(--foreground);"
          aria-label="返回"
          @click="goBack"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>

        <p class="truncate px-2 text-center text-[17px] font-semibold" style="color: var(--foreground);">{{ title }}</p>

        <div data-testid="feature-nav-spacer" aria-hidden="true" class="h-9 w-9 shrink-0" />
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
    backLabel: '',
    contentWidth: '100%',
  },
)

const router = useRouter()

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push(props.backTo)
  }
}
</script>
