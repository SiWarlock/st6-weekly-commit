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
              // (the store + baseApi live entirely in the remote). The host only
              // provides the router + getAccessToken. We DON'T expose `./store`:
              // a separate `import('wc_web/store')` whose chunk did a top-level
              // `await importShared('@reduxjs/toolkit')` deadlocked the cross-build
              // shared-scope init → the host hung forever on "Connecting…".
              './WeeklyCommitApp': './src/remote/WeeklyCommitApp.tsx',
            },
            // Share ONLY the singletons that cross the host↔remote boundary:
            //  - react / react-dom: the single React instance (else invalid hooks).
            //  - react-router-dom: the remote renders <AppRoutes/>'s <Routes>/
            //    useNavigate inside the host's <BrowserRouter>; React Router's
            //    context is module-identity-based, so host + remote must resolve to
            //    ONE instance (else "useRoutes() may be used only in the context of
            //    a <Router>"). Host declares the same set + versions.
            // Redux (@reduxjs/toolkit) + react-redux are NOT shared: they're
            // remote-INTERNAL now (the remote self-provides its store), so the host
            // never touches them. Sharing @reduxjs/toolkit forced a top-level
            // `await importShared(...)` in the store chunk that deadlocked the
            // cross-build init; bundling it in the remote removes that await.
            shared: {
              react: { requiredVersion: '^18.3.1' },
              'react-dom': { requiredVersion: '^18.3.1' },
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
