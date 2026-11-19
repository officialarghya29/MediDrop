var _a, _b;
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            '/api': { target: (_a = process.env.VITE_PROXY_TARGET) !== null && _a !== void 0 ? _a : 'http://localhost:8080', changeOrigin: true },
            '/ws': { target: (_b = process.env.VITE_PROXY_TARGET) !== null && _b !== void 0 ? _b : 'http://localhost:8080', ws: true },
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
        setupFiles: ['./src/test/setup.ts'],
    },
});
