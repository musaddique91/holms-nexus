import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Where `npm run dev` sends /api: a local backend by default, or the deployed one with
// NEXUS_API_TARGET=https://nexus.example.com.
const apiTarget = process.env.NEXUS_API_TARGET ?? 'http://localhost:8095'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        // The backend's CORS policy only allows its own origin; present that origin in dev.
        configure: (proxy) => {
          proxy.on('proxyReq', (req) => {
            if (req.getHeader('origin')) req.setHeader('origin', new URL(apiTarget).origin)
          })
        },
      },
    },
  },
})
