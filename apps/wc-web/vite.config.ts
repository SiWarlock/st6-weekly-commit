/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Standalone dev/build config. The Module Federation `expose` + remote entry
// land in Phase 9.3; this config stands up the buildable shell + the Vitest
// (jsdom) harness for the styling-foundation slice.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
