import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { StandaloneShell } from './StandaloneShell';
import '../styles/theme.css';

// Standalone entry — side-effectful mount only. The full provider tree
// (router + store + theme + demo identity + persona/theme chrome) lives in the
// testable StandaloneShell. The exposed Module-Federation remote
// (`./WeeklyCommitApp`) is configured in vite.config.ts and carries none of the
// standalone-only chrome/demo path (REQ-I-008).

/**
 * ST.7a — start the standalone MSW mock layer before mount so the backend-less
 * standalone app renders populated surfaces (QA + demo). On in dev by default;
 * `VITE_USE_MOCKS='false'` opts out (e.g. running against a real local API),
 * `'true'` opts a non-dev build in (e.g. the demo-video build). DYNAMIC import so
 * `msw` is bundled ONLY into the standalone graph — never the remote (REQ-I-008,
 * proven by the boundary test). The remote never imports this entry at all.
 */
async function startMocksIfEnabled(): Promise<void> {
  const flag = import.meta.env.VITE_USE_MOCKS;
  const enabled = flag === 'true' || (import.meta.env.DEV && flag !== 'false');
  if (!enabled) {
    return;
  }
  const { startMockWorker } = await import('./mocks/browser');
  await startMockWorker();
}

async function bootstrap(): Promise<void> {
  await startMocksIfEnabled();

  const rootEl = document.getElementById('root');
  if (!rootEl) {
    throw new Error('Root element #root not found');
  }

  createRoot(rootEl).render(
    <StrictMode>
      <StandaloneShell />
    </StrictMode>,
  );
}

void bootstrap();
