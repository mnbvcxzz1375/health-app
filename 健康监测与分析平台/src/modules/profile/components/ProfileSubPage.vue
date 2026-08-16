<template>
  <div class="apple-profile-sub pb-6">
    <div class="mx-auto max-w-[420px] px-4 pt-6">
      <!-- Header -->
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition active:scale-95"
          style="background: var(--secondary); color: var(--foreground);"
          aria-label="返回"
          @click="goBack"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground); line-height: 1.15;">{{ title }}</h1>
      </div>
      <p v-if="subtitle" class="mt-1.5 text-[15px]" style="color: var(--muted-foreground);">{{ subtitle }}</p>

      <!-- Content -->
      <div class="mt-5">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

defineProps<{
  title: string
  subtitle?: string
}>()

const router = useRouter()

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}
</script>
