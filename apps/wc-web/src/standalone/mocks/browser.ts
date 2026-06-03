/**
 * ST.7a — the standalone MSW browser worker (dev/demo only; tree-shaken from the
 * MF remote, REQ-I-008 — only `standalone/main.tsx` dynamic-imports this). Wraps
 * the contract-typed handlers in a Service Worker that intercepts the RTK Query
 * fetches so the backend-less standalone app renders populated surfaces.
 */
import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);

/**
 * Start the worker before the app mounts. `onUnhandledRequest: 'bypass'` lets
 * non-API requests (assets, the SW script itself) pass through untouched; only
 * the declared API handlers are intercepted.
 */
export async function startMockWorker(): Promise<void> {
  await worker.start({
    onUnhandledRequest: 'bypass',
    serviceWorker: { url: '/mockServiceWorker.js' },
  });
}
