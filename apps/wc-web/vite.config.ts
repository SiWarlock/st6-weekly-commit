/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';
import { shouldEnableFederation } from './vite.buildTarget';

// Module Federation remote (9.3): exposes one mountable module and shares the
// React/Redux singletons. The federation plugin is gated OFF under Vitest (the
// jsdom unit suite exercises the boundary via a static import-graph test, not the
// rewritten module graph) AND for the standalone SPA build (9.16) — `vite build`
// with VITE_BUILD_TARGET=standalone uses `index.html` as the entry instead of
// emitting the `remoteEntry.js` library. The default build stays the remote.
// See vite.buildTarget.ts for the pure, unit-tested mode-selection.
const enableFederation = shouldEnableFederation(process.env);

export default defineConfig({
  plugins: [
    react(),
    ...(enableFederation
      ? [
          federation({
            name: 'wc_web',
            filename: 'remoteEntry.js',
            exposes: {
              // ONE exposed module. The remote is self-contained for state — it
              // wraps its own `<Provider store={store}>` inside WeeklyCommitApp
              // (store + baseApi live entirely in the remote). The host provides
              // the router + getAccessToken. We DON'T expose `./store`: a separate
              // `import('wc_web/store')` (imported BEFORE the WeeklyCommitApp
              // ensure) raced the shared-scope init and deadlocked the host on
              // "Connecting…". Folding the store into the single WeeklyCommitApp
              // ensure is what fixed the deadlock.
              './WeeklyCommitApp': './src/remote/WeeklyCommitApp.tsx',
            },
            // Share ALL the React-coupled singletons. They MUST be shared so the
            // remote uses the SAME React instance as the host renderer — a bundled
            // react-redux/@reduxjs/toolkit runs its hooks (useSyncExternalStore →
            // React.useRef) against a second React → "Cannot read properties of
            // null (reading 'useRef')" (dual-React crash, blank page). react-router-dom
            // is shared for the single Router context. The store is still
            // self-provided (no `./store` expose); sharing redux does NOT
            // reintroduce the deadlock because there's no separate pre-ensure
            // store import — its `importShared` now resolves inside the single
            // WeeklyCommitApp ensure (canonical, like react does).
            shared: {
              react: { requiredVersion: '^18.3.1' },
              'react-dom': { requiredVersion: '^18.3.1' },
              '@reduxjs/toolkit': { requiredVersion: '^2.3.0' },
              'react-redux': { requiredVersion: '^9.1.2' },
              'react-router-dom': { requiredVersion: '^6.28.0' },
            },
          }),
        ]
      : []),
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
