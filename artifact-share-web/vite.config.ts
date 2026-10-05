import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 开发时代理到本地 Spring Boot;生产由 Nginx 统一反代 /api
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
