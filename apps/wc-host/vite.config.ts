import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import federation from "@originjs/vite-plugin-federation";

/**
 * Module Federation HOST shell (the "Acme Portal" parent app). It loads the
 * `wc_web` remote — the Weekly Commit micro-frontend — from `VITE_WC_REMOTE_URL`
 * and renders it inside the portal chrome, proving wc-web plugs into a parent host
 * as a remote (§7 / REQ-I-007 / REQ-I-013).
 *
 * The `shared` scope MUST match the remote (apps/wc-web/vite.config.ts) EXACTLY so
 * there is a SINGLE instance of each across the boundary. Version drift here is the
 * classic federation footgun: two React copies → "invalid hook call"; two
 * react-router-dom copies → the remote's route hooks read a different Router
 * context than the host's <BrowserRouter> provides → "useRoutes() may be used only
 * in the context of a <Router>".
 *
 * The remote URL is read from `process.env` at config-eval (Node) and baked into
 * the build. Override per environment, e.g.:
 *   VITE_WC_REMOTE_URL=http://localhost:5174/assets/remoteEntry.js
 */
export default defineConfig(({ mode }) => {
  // Load .env[.local] (+ shell env) so VITE_WC_REMOTE_URL resolves from either —
  // the remote URL is baked at config/build time, not runtime.
  const env = loadEnv(mode, process.cwd(), "");
  const remoteUrl =
    env.VITE_WC_REMOTE_URL ?? "http://localhost:5174/assets/remoteEntry.js";

  return {
    plugins: [
      react(),
      federation({
        name: "wc_host",
        remotes: {
          // `wc_web` is the remote name declared in apps/wc-web/vite.config.ts.
          wc_web: remoteUrl,
        },
        // Mirror the remote's shared scope EXACTLY (+ react-router-dom, which the
        // host provides via <BrowserRouter> and the remote consumes).
        shared: {
          react: { requiredVersion: "^18.3.1" },
          "react-dom": { requiredVersion: "^18.3.1" },
          "@reduxjs/toolkit": { requiredVersion: "^2.3.0" },
          "react-redux": { requiredVersion: "^9.1.2" },
          "react-router-dom": { requiredVersion: "^6.28.0" },
        },
      }),
    ],
    // Federation emits top-level await; esnext keeps the build valid (mirrors the remote).
    build: { target: "esnext" },
    server: { port: 5173, strictPort: true },
    preview: { port: 5173, strictPort: true },
  };
});
