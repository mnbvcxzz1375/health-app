import axios from 'axios'
import { env } from '@/config/env'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const SESSION_KEY = 'hm_auth_session'

function readToken() {
  if (typeof window === 'undefined') return ''
  const raw = window.localStorage.getItem(SESSION_KEY)
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw) as { token?: string }
    return parsed?.token ?? ''
  } catch {
    return ''
  }
}

export const http = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const token = readToken()
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    const status = err?.response?.status

    if (status === 401) {
      try {
        const authStore = useAuthStore()
        authStore.clearSession()
        void router.push({ name: 'auth-login', query: { redirect: router.currentRoute.value.fullPath } })
      } catch {
        // Pinia 尚未激活时忽略
      }
      return Promise.reject(err)
    }

    try {
      const toast = useToastStore()
      const message = err?.response?.data?.message ?? err.message ?? '网络请求失败'
      toast.error('请求失败', message)
    } catch {
      // Pinia 尚未激活时忽略提示
    }
    return Promise.reject(err)
  },
)
