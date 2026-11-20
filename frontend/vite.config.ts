import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080', ws: true },
    },
  },
  preview: {
    proxy: {
      '/api': { target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
  },
});
