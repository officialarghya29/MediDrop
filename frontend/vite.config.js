var _a, _b, _c, _d;
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
    preview: {
        proxy: {
            '/api': { target: (_c = process.env.VITE_PROXY_TARGET) !== null && _c !== void 0 ? _c : 'http://localhost:8080', changeOrigin: true },
            '/ws': { target: (_d = process.env.VITE_PROXY_TARGET) !== null && _d !== void 0 ? _d : 'http://localhost:8080', ws: true },
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
