import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          // 三方依赖统一走 vendor 分包，降低首屏主包体积并提升缓存命中
          if (!id.includes('node_modules')) return

          // Vue 运行时与路由/状态管理拆分
          if (
            id.includes('/node_modules/vue/') ||
            id.includes('/node_modules/vue-router/') ||
            id.includes('/node_modules/pinia/')
          ) {
            return 'vendor-vue'
          }

          // Element Plus 图标单独拆包，避免与组件运行时代码耦合
          if (id.includes('/node_modules/@element-plus/icons-vue/')) {
            return 'vendor-icons'
          }

          // Element Plus 组件库独立分包
          if (
            id.includes('/node_modules/element-plus/') ||
            id.includes('/node_modules/@element-plus/')
          ) {
            return 'vendor-element'
          }

          // 图表依赖单独拆分，避免管理端图表代码进入基础包
          if (id.includes('/node_modules/echarts/')) {
            return 'vendor-echarts'
          }

          // 其余依赖归入通用 vendor
          return 'vendor-misc'
        }
      }
    }
  }
})
