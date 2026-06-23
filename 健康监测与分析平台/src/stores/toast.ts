import { defineStore } from 'pinia'

export type ToastVariant = 'success' | 'info' | 'warning' | 'error'

export type ToastItem = {
  id: number
  title: string
  description?: string
  variant: ToastVariant
  duration: number
}

type ToastPayload = {
  title: string
  description?: string
  variant?: ToastVariant
  duration?: number
}

let toastSeed = 0

export const useToastStore = defineStore('toast', {
  state: () => ({
    toasts: [] as ToastItem[],
  }),
  actions: {
    addToast(payload: ToastPayload) {
      const toast: ToastItem = {
        id: ++toastSeed,
        title: payload.title,
        description: payload.description,
        variant: payload.variant ?? 'info',
        duration: payload.duration ?? 2600,
      }

      this.toasts.unshift(toast)

      window.setTimeout(() => {
        this.removeToast(toast.id)
      }, toast.duration)

      return toast.id
    },
    removeToast(id: number) {
      this.toasts = this.toasts.filter(t => t.id !== id)
    },
    success(title: string, description?: string, duration?: number) {
      this.addToast({ title, description, duration, variant: 'success' })
    },
    info(title: string, description?: string, duration?: number) {
      this.addToast({ title, description, duration, variant: 'info' })
    },
    warning(title: string, description?: string, duration?: number) {
      this.addToast({ title, description, duration, variant: 'warning' })
    },
    error(title: string, description?: string, duration?: number) {
      this.addToast({ title, description, duration, variant: 'error' })
    },
    clearAll() {
      this.toasts = []
    },
  },
})
