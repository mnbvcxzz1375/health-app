import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/home',
  },
  {
    path: '/auth/login',
    name: 'auth-login',
    component: () => import('@/modules/auth/views/LoginPage.vue'),
    meta: { title: '登录', hideNav: true, guestOnly: true },
  },
  {
    path: '/auth/register',
    name: 'auth-register',
    component: () => import('@/modules/auth/views/RegisterPage.vue'),
    meta: { title: '注册', hideNav: true, guestOnly: true },
  },
  {
    path: '/home',
    name: 'home',
    component: () => import('@/modules/home/views/HomePage.vue'),
    meta: { title: '总览', requiresAuth: true },
  },
  {
    path: '/monitor',
    name: 'monitor',
    redirect: '/home',
  },
  {
    path: '/upload',
    name: 'upload',
    component: () => import('@/modules/upload/views/UploadPage.vue'),
    meta: { title: '上传与分析', requiresAuth: true },
  },
  {
    path: '/medication',
    name: 'medication',
    component: () => import('@/modules/medication/views/MedicationPage.vue'),
    meta: { title: '用药提醒', requiresAuth: true },
  },
  {
    path: '/medication/alarm',
    name: 'medication-alarm',
    component: () => import('@/modules/medication/views/MedicationAlarmPage.vue'),
    meta: { title: '闹钟设置', requiresAuth: true },
  },
  {
    path: '/rehab',
    name: 'rehab',
    component: () => import('@/modules/rehab/views/RehabPage.vue'),
    meta: { title: '康复', requiresAuth: true },
  },
  {
    path: '/assistant',
    name: 'assistant',
    component: () => import('@/modules/assistant/views/AssistantPage.vue'),
    meta: { title: '智能助手', requiresAuth: true, hideNav: true },
  },
  {
    path: '/rehab/exercise',
    name: 'rehab-exercise',
    component: () => import('@/modules/rehab/views/RehabExercisePage.vue'),
    meta: { title: '动作示范', requiresAuth: true },
  },
  {
    path: '/rehab/reminder',
    name: 'rehab-reminder',
    component: () => import('@/modules/rehab/views/RehabReminderPage.vue'),
    meta: { title: '训练提醒', requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/modules/profile/views/ProfilePage.vue'),
    meta: { title: '我的', requiresAuth: true },
  },
  {
    path: '/profile/settings',
    name: 'profile-settings',
    component: () => import('@/modules/profile/views/SettingsPage.vue'),
    meta: { title: '个人设置', requiresAuth: true },
  },
  {
    path: '/profile/export',
    name: 'profile-export',
    component: () => import('@/modules/profile/views/DataExportPage.vue'),
    meta: { title: '导出数据', requiresAuth: true },
  },
  {
    path: '/profile/security',
    name: 'profile-security',
    component: () => import('@/modules/profile/views/SecurityPage.vue'),
    meta: { title: '数据安全', requiresAuth: true },
  },
  {
    path: '/profile/permissions',
    name: 'profile-permissions',
    component: () => import('@/modules/profile/views/PermissionsPage.vue'),
    meta: { title: '权限与授权', requiresAuth: true },
  },
  {
    path: '/profile/privacy',
    name: 'profile-privacy',
    component: () => import('@/modules/profile/views/PrivacyCenterPage.vue'),
    meta: { title: '隐私中心', requiresAuth: true },
  },
  {
    path: '/profile/help',
    name: 'profile-help',
    component: () => import('@/modules/profile/views/HelpPage.vue'),
    meta: { title: '帮助与支持', requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/modules/system/views/NotFoundPage.vue'),
    meta: { title: '页面不存在', requiresAuth: true },
  },
]
