import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:3302'
  const postureProxyTarget = env.VITE_DEV_POSTURE_PROXY_TARGET || 'http://127.0.0.1:8080'
  const devPort = Number(env.VITE_DEV_PORT || 4173)

  return {
    plugins: [
      tailwindcss(),
      vue({
        template: {
          compilerOptions: {
            isCustomElement: (tag) => tag === 'iconify-icon',
          },
        },
      }),
      vuetify({ autoImport: true }),
    ],
    server: {
      host: '127.0.0.1',
      port: devPort,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
        '/posture-api': {
          target: postureProxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/posture-api/, ''),
        },
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
  }
})
