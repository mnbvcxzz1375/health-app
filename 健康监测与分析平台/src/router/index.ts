import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { useAuthStore } from '@/stores/auth'

// 保存离开页面时的滚动位置，用于返回时恢复
const scrollPositions = new Map<string, number>()

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 浏览器前进/后退（包括 router.back()）优先使用 savedPosition
    if (savedPosition) return savedPosition
    // 如果目标路径有存储的滚动位置，恢复它
    const stored = scrollPositions.get(to.fullPath)
    if (stored !== undefined) {
      return { top: stored }
    }
    return { top: 0 }
  },
})

router.beforeEach((to, from, next) => {
  // 离开前保存当前滚动位置
  if (from.fullPath && from.fullPath !== '/') {
    scrollPositions.set(from.fullPath, window.scrollY)
  }

  const authStore = useAuthStore()
  if (!authStore.ready) authStore.hydrate()

  if (to.meta?.guestOnly && authStore.isAuthenticated) {
    next('/home')
    return
  }

  if (to.meta?.requiresAuth && !authStore.isAuthenticated) {
    next({ path: '/auth/login', query: { redirect: to.fullPath } })
    return
  }

  const baseTitle = '健康监测与分析平台'
  document.title = to.meta?.title ? `${to.meta.title} - ${baseTitle}` : baseTitle
  next()
})

export default router
