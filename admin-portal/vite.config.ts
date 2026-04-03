import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/services': {
        target: 'http://localhost:9080',
        changeOrigin: true,
      },
    },
  },
})
