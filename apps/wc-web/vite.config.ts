/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

// Module Federation remote (9.3): exposes one mountable module and shares the
// React/Redux singletons. The federation plugin is gated OFF under Vitest — it
// rewrites the module graph for the build and isn't needed (or wanted) for the
// jsdom unit suite, which exercises the boundary via a static import-graph test.
const isTest = process.env.VITEST === 'true';

export default defineConfig({
  plugins: [
    react(),
    ...(isTest
      ? []
      : [
          federation({
            name: 'wc_web',
            filename: 'remoteEntry.js',
            exposes: {
              './WeeklyCommitApp': './src/remote/WeeklyCommitApp.tsx',
            },
            // @originjs dedups each shared dep into one version-matched shared
            // chunk (NOT webpack-style `singleton` enforcement — that key isn't
            // in its typed API). `requiredVersion` pins the shared React/Redux;
            // the real single-React-instance guarantee is a host+remote
            // shared-scope agreement owned by the 9.13 host contract.
            shared: {
              react: { requiredVersion: '^18.3.1' },
              'react-dom': { requiredVersion: '^18.3.1' },
              '@reduxjs/toolkit': { requiredVersion: '^2.3.0' },
              'react-redux': { requiredVersion: '^9.1.2' },
            },
          }),
        ]),
  ],
  // Federation emits top-level await; esnext keeps the build valid.
  build: { target: 'esnext' },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    // Absolute base URL so RTK Query's Request construction is valid under
    // Node/undici (a relative baseUrl throws before fetchFn runs). Read at
    // import time by baseApi; per-test fetch is mocked via vi.stubGlobal.
    env: { VITE_API_BASE_URL: 'http://localhost' },
  },
});
