/**
 * 9.15 — Finding #1: the MSW cold-install boot fix (standalone/demo only;
 * tree-shaken from the MF remote, REQ-I-008).
 *
 * On a *fresh* browser the mock Service Worker registers and activates, but does
 * not necessarily CONTROL the already-loaded page until a reload — so the app's
 * first RTK Query fetches can fire before the worker intercepts them and hang on
 * a loading skeleton (a fresh tab "fixes" it, which is the tell). This pure helper
 * lets the standalone boot AWAIT SW control (no user-visible reload) before the
 * app mounts: it resolves immediately when the page is already controlled, on the
 * `controllerchange` event when control arrives, and after a timeout if control
 * never comes (so a missing/disabled SW never deadlocks the boot).
 *
 * Pure — no `msw/browser` import — so it unit-tests deterministically in jsdom
 * (`setupWorker` can't run there). `browser.ts` adapts `navigator.serviceWorker`
 * onto the minimal seam below.
 */

/** The minimal slice of `ServiceWorkerContainer` this helper needs (testable seam). */
export interface ServiceWorkerControlLike {
  controller: unknown;
  addEventListener: (type: 'controllerchange', listener: () => void) => void;
  removeEventListener: (type: 'controllerchange', listener: () => void) => void;
}

/** Why the wait resolved — `already` controlled, `controlled` via event, or `timeout`. */
export type SwControlResult = 'already' | 'controlled' | 'timeout';

const DEFAULT_CONTROL_TIMEOUT_MS = 3000;

/**
 * Resolve once the Service Worker controls the page (or the timeout elapses).
 * The controller is sampled once at entry; if already present we resolve without
 * registering a listener. Otherwise we wait for the first `controllerchange`,
 * with a timeout backstop. Always cleans up its listener + timer.
 */
export function waitForServiceWorkerControl(
  container: ServiceWorkerControlLike,
  timeoutMs: number = DEFAULT_CONTROL_TIMEOUT_MS,
): Promise<SwControlResult> {
  if (container.controller) {
    return Promise.resolve('already');
  }
  return new Promise<SwControlResult>((resolve) => {
    let settled = false;

    function onChange(): void {
      finish('controlled');
    }
    function finish(result: SwControlResult): void {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timer);
      container.removeEventListener('controllerchange', onChange);
      resolve(result);
    }

    const timer = setTimeout(() => finish('timeout'), timeoutMs);
    container.addEventListener('controllerchange', onChange);
  });
}
