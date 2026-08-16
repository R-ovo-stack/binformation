import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        timeout: 10000,
        proxyTimeout: 10000,
        configure: (proxy) => {
          proxy.on('error', (err, _req, res) => {
            console.error('[vite proxy /api]', err.message)
            const httpRes = res as { headersSent?: boolean; writeHead?: Function; end?: Function }
            if (httpRes && !httpRes.headersSent && typeof httpRes.writeHead === 'function') {
              httpRes.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
              httpRes.end?.(
                JSON.stringify({
                  message:
                    '后端未启动或无法连接 http://127.0.0.1:8080，请先在项目根目录执行 mvn spring-boot:run',
                }),
              )
            }
          })
        },
      },
    },
  },
})
