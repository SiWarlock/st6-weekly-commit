import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { Flowbite } from 'flowbite-react';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { flowbiteTheme } from '../app/flowbiteTheme';
import { store } from '../app/store';
import WeeklyCommitApp from '../remote/WeeklyCommitApp';
import { DemoIdentityProvider } from './DemoIdentityProvider';
import { FlowbiteThemeSync } from './FlowbiteThemeSync';
import { AppShell } from './shell/AppShell';

/**
 * Full STANDALONE shell — owns the router, the Redux store, the dark/light
 * ThemeProvider, the demo identity provider, and the demo app-shell chrome
 * (`AppShell`: top app-bar + persona-gated WC sub-nav + breadcrumb), and mounts
 * the exposed `WeeklyCommitApp` inside it. All of this (router ownership +
 * demo/persona + chrome) is standalone-only and tree-shaken out of the exposed
 * remote build (REQ-I-008); the remote consumes the host's router + accessor and
 * the production host owns the equivalent chrome (§7).
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
              <AppShell>
                <WeeklyCommitApp />
              </AppShell>
            </Flowbite>
          </DemoIdentityProvider>
        </ThemeProvider>
      </BrowserRouter>
    </Provider>
  );
}
