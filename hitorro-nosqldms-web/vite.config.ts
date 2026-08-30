import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Output goes into the spring-boot module's static/ resource dir so the
// backend can serve the built UI without any additional wiring.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../hitorro-nosqldms-spring-boot/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8090',
    },
  },
});
