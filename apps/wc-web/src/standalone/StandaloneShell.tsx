import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { Flowbite } from 'flowbite-react';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { flowbiteTheme } from '../app/flowbiteTheme';
import { store } from '../app/store';
import WeeklyCommitApp from '../remote/WeeklyCommitApp';
import { DemoIdentityProvider } from './DemoIdentityProvider';
import { Auth0IdentityProvider } from './Auth0IdentityProvider';
import { FlowbiteThemeSync } from './FlowbiteThemeSync';
import { AppShell } from './shell/AppShell';
import { PersonaSwitcher } from './PersonaSwitcher';
import { Auth0LoginGate } from './shell/Auth0LoginGate';
import { Auth0IdentitySlot } from './shell/Auth0IdentitySlot';
import { CallbackRoute } from './shell/CallbackRoute';

/**
 * Full STANDALONE shell — owns the router, the Redux store, the dark/light
 * ThemeProvider, the identity provider, and the demo app-shell chrome, and mounts
 * the exposed `WeeklyCommitApp` inside it. All of this (router ownership +
 * identity/chrome) is standalone-only and tree-shaken out of the exposed remote
 * build (REQ-I-008); the remote consumes the host's router + accessor and the
 * production host owns the equivalent chrome (§7).
 *
 * Two identity modes, gated by `VITE_AUTH_MODE` (§7, the host owns this value in
 * the federated remote; standalone uses it directly):
 *   - `auth0` → the deployed real-OAuth demo (9.17): `Auth0IdentityProvider` runs
 *     the PKCE login + feeds the token seam; a standalone `<Routes>` keeps the
 *     `/callback` code-exchange OUTSIDE the login gate (so it isn't shadowed),
 *     and the gate fronts the app on `/*`. The `PersonaSwitcher` is retired here.
 *   - else  → the demo/MSW path: `DemoIdentityProvider` + `PersonaSwitcher`
 *     (unchanged).
 */
export function StandaloneShell() {
  const isAuth0 = import.meta.env.VITE_AUTH_MODE === 'auth0';

  return (
    <Provider store={store}>
      <BrowserRouter
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <ThemeProvider>
          {isAuth0 ? (
            <Auth0IdentityProvider>
              <Flowbite theme={{ theme: flowbiteTheme }}>
                <FlowbiteThemeSync />
                <Routes>
                  {/* /callback renders OUTSIDE the gate so the SDK code-exchange
                      isn't shadowed by the gate's loading/login branch. */}
                  <Route path="/callback" element={<CallbackRoute />} />
                  <Route
                    path="/*"
                    element={
                      <Auth0LoginGate>
                        <AppShell identitySlot={<Auth0IdentitySlot />}>
                          <WeeklyCommitApp />
                        </AppShell>
                      </Auth0LoginGate>
                    }
                  />
                </Routes>
              </Flowbite>
            </Auth0IdentityProvider>
          ) : (
            <DemoIdentityProvider>
              <Flowbite theme={{ theme: flowbiteTheme }}>
                {/* Keep Flowbite's own theme-mode in sync with our [data-theme]
                    single source (#6, ST.7d) — must be inside <Flowbite>. */}
                <FlowbiteThemeSync />
                <AppShell identitySlot={<PersonaSwitcher />}>
                  <WeeklyCommitApp />
                </AppShell>
              </Flowbite>
            </DemoIdentityProvider>
          )}
        </ThemeProvider>
      </BrowserRouter>
    </Provider>
  );
}
