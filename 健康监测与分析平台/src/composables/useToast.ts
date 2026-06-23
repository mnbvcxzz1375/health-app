import { useToastStore } from '@/stores/toast'

export function useToast() {
  const store = useToastStore()

  return {
    toast: store.addToast,
    success: store.success,
    info: store.info,
    warning: store.warning,
    error: store.error,
    clearAll: store.clearAll,
  }
}
