import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { StandaloneShell } from './StandaloneShell';
import '../styles/theme.css';

// Standalone entry — side-effectful mount only. The full provider tree
// (router + store + theme + demo identity + persona/theme chrome) lives in the
// testable StandaloneShell. The exposed Module-Federation remote
// (`./WeeklyCommitApp`) is configured in vite.config.ts and carries none of the
// standalone-only chrome/demo path (REQ-I-008).
const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Root element #root not found');
}

createRoot(rootEl).render(
  <StrictMode>
    <StandaloneShell />
  </StrictMode>,
);
