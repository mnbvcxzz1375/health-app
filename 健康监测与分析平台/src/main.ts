/**
 * 应用入口
 *
 * 企业级项目建议：入口只做“装配（wiring）”，不要写业务逻辑：
 * - 安装路由/状态管理
 * - 注入全局样式
 * - 注册全局组件（必要时）
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { vuetify } from './plugins/vuetify'
import { useAuthStore } from '@/stores/auth'
import './styles/index.css'
import './styles/theme.css'

const app = createApp(App)
const pinia = createPinia()

// 全局状态（Pinia）
app.use(pinia)

// Vuetify 组件库
app.use(vuetify)

// 启动时恢复会话
const authStore = useAuthStore(pinia)
authStore.hydrate()

// 路由
app.use(router)

app.mount('#app')
