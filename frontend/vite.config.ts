import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
    extensions: ['.js', '.ts', '.tsx', '.json'],
  },
  server: {
    port: 3000,
    proxy: {
      '/services': {
        target: 'http://localhost:9080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static/admin',
    emptyOutDir: true,
  },
})
