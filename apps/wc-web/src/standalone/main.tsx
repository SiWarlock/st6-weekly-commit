import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Flowbite } from 'flowbite-react';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { flowbiteTheme } from '../app/flowbiteTheme';
import { ThemeToggle } from './ThemeToggle';
import App from '../App';
import '../styles/theme.css';

// Standalone entry. Owns the dark/light ThemeProvider + the Flowbite skin + the
// standalone-only ThemeToggle chrome. The Module Federation `expose`, full
// standalone/remote split, PersonaSwitcher, and baseApi XOR land in Phase 9.1/9.3.
const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Root element #root not found');
}

createRoot(rootEl).render(
  <StrictMode>
    <ThemeProvider>
      <Flowbite theme={{ theme: flowbiteTheme }}>
        <header className="flex items-center justify-end border-b border-border px-6 py-3">
          <ThemeToggle />
        </header>
        <App />
      </Flowbite>
    </ThemeProvider>
  </StrictMode>,
);
