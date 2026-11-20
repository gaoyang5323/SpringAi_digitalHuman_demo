import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/upload': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/digital-human': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/rag': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
})