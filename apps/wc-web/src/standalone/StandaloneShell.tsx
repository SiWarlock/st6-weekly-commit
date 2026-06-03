import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { Flowbite } from 'flowbite-react';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { flowbiteTheme } from '../app/flowbiteTheme';
import { store } from '../app/store';
import WeeklyCommitApp from '../remote/WeeklyCommitApp';
import { DemoIdentityProvider } from './DemoIdentityProvider';
import { FlowbiteThemeSync } from './FlowbiteThemeSync';
import { PersonaSwitcher } from './PersonaSwitcher';
import { ThemeToggle } from './ThemeToggle';

/**
 * Full STANDALONE shell — owns the router, the Redux store, the dark/light
 * ThemeProvider, the demo identity provider, and the persona/theme chrome, and
 * mounts the exposed `WeeklyCommitApp`. All of this (router ownership +
 * demo/persona + chrome) is standalone-only and tree-shaken out of the exposed
 * remote build (REQ-I-008); the remote consumes the host's router + accessor.
 */
export function StandaloneShell() {
  return (
    <Provider store={store}>
      <BrowserRouter
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <ThemeProvider>
          <DemoIdentityProvider>
            <Flowbite theme={{ theme: flowbiteTheme }}>
              {/* Keep Flowbite's own theme-mode in sync with our [data-theme]
                  single source (#6, ST.7d) — must be inside <Flowbite>. */}
              <FlowbiteThemeSync />
              <header className="flex items-center justify-end gap-4 border-b border-border px-6 py-3">
                <PersonaSwitcher />
                <ThemeToggle />
              </header>
              <WeeklyCommitApp />
            </Flowbite>
          </DemoIdentityProvider>
        </ThemeProvider>
      </BrowserRouter>
    </Provider>
  );
}
