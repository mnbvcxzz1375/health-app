import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

router.beforeEach((to, _from, next) => {
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
