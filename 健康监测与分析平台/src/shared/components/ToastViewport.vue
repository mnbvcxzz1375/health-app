<template>
  <div class="pointer-events-none absolute inset-x-3 top-3 z-50" role="status" aria-live="polite">
    <TransitionGroup name="toast" tag="div" class="flex flex-col gap-2">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto rounded-2xl border border-black/15 bg-white px-3 py-2.5 text-black shadow-[0_14px_26px_-20px_rgba(0,0,0,.5)]"
      >
        <div class="flex items-start gap-2.5">
          <iconify-icon :icon="iconMap[toast.variant]" width="18" height="18" class="mt-0.5 text-black" />
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-black">{{ toast.title }}</p>
            <p v-if="toast.description" class="mt-0.5 text-xs text-black/70">{{ toast.description }}</p>
          </div>
          <button
            class="inline-flex h-6 w-6 items-center justify-center rounded-lg text-black/50 transition hover:bg-black/5 hover:text-black"
            type="button"
            aria-label="关闭提示"
            @click="removeToast(toast.id)"
          >
            <iconify-icon icon="solar:close-circle-outline" width="16" height="16" />
          </button>
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useToastStore } from '@/stores/toast'
import type { ToastVariant } from '@/stores/toast'

const toastStore = useToastStore()
const { toasts } = storeToRefs(toastStore)

const removeToast = (id: number) => toastStore.removeToast(id)

const iconMap: Record<ToastVariant, string> = {
  success: 'solar:check-circle-outline',
  info: 'solar:info-circle-outline',
  warning: 'solar:danger-circle-outline',
  error: 'solar:close-circle-outline',
}
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}

.toast-move {
  transition: transform 0.2s ease;
}
</style>
