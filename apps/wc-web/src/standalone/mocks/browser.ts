/**
 * ST.7a — the standalone MSW browser worker (dev/demo only; tree-shaken from the
 * MF remote, REQ-I-008 — only `standalone/main.tsx` dynamic-imports this). Wraps
 * the contract-typed handlers in a Service Worker that intercepts the RTK Query
 * fetches so the backend-less standalone app renders populated surfaces.
 */
import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { waitForServiceWorkerControl } from './swControl';

export const worker = setupWorker(...handlers);

/**
 * Start the worker before the app mounts. `onUnhandledRequest: 'bypass'` lets
 * non-API requests (assets, the SW script itself) pass through untouched; only
 * the declared API handlers are intercepted.
 *
 * Finding #1 (9.15): after `start()` resolves, on a COLD install the SW has
 * activated but may not yet CONTROL this already-loaded page — the app's first
 * RTK Query fetches would then bypass the worker and hang on a loading skeleton.
 * Await control (no user-visible reload) before returning; the timeout backstop in
 * the helper means a missing/disabled SW never deadlocks the boot.
 */
export async function startMockWorker(): Promise<void> {
  await worker.start({
    onUnhandledRequest: 'bypass',
    serviceWorker: { url: '/mockServiceWorker.js' },
  });
  const sw =
    typeof navigator !== 'undefined' ? navigator.serviceWorker : undefined;
  if (sw) {
    await waitForServiceWorkerControl({
      controller: sw.controller,
      addEventListener: (type, listener) => sw.addEventListener(type, listener),
      removeEventListener: (type, listener) =>
        sw.removeEventListener(type, listener),
    });
  }
}
