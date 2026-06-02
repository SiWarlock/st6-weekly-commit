import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { Flowbite } from 'flowbite-react';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { flowbiteTheme } from '../app/flowbiteTheme';
import { store } from '../app/store';
import { ThemeToggle } from './ThemeToggle';
import App from '../App';
import '../styles/theme.css';

// Standalone entry. Owns the Redux store (9.1), the dark/light ThemeProvider +
// Flowbite skin, and the standalone-only ThemeToggle chrome. The Module
// Federation `expose`, full standalone/remote split, PersonaSwitcher, and the
// DemoIdentityProvider that injects the auth-accessor seam land in Phase 9.3.
const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Root element #root not found');
}

createRoot(rootEl).render(
  <StrictMode>
    <Provider store={store}>
      <ThemeProvider>
        <Flowbite theme={{ theme: flowbiteTheme }}>
          <header className="flex items-center justify-end border-b border-border px-6 py-3">
            <ThemeToggle />
          </header>
          <App />
        </Flowbite>
      </ThemeProvider>
    </Provider>
  </StrictMode>,
);
