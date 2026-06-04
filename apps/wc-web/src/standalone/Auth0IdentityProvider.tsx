import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { Auth0Provider, useAuth0, type AppState } from '@auth0/auth0-react';
import { resolveAuth0Config } from './auth0Config';
import { setAccessTokenProvider } from '../app/authAccessor';
import { baseApi } from '../app/baseApi';

/**
 * STANDALONE-ONLY OAuth identity (the deployed real-backend demo, §6/§16). The
 * Auth0 PKCE SPA counterpart of `DemoIdentityProvider`: it feeds the SDK's
 * `getAccessTokenSilently` into the 9.1 accessor seam so the already-built
 * `prepareHeaders` auth0 branch sends `Authorization: Bearer <jwt>`, and resets
 * the RTK cache on identity change (§16). The login GATE + the `/callback` route
 * live OUTSIDE this provider (in `StandaloneShell`'s standalone routes) so the
 * code-exchange can run inside `<Auth0Provider>` but outside the gate. Tree-shaken
 * out of the exposed remote build (REQ-I-008) — only `StandaloneShell` mounts it.
 */
function Auth0SeamWiring({ children }: { children: ReactNode }) {
  const { user, getAccessTokenSilently } = useAuth0();
  const dispatch = useDispatch();

  // A ref keeps the seam closure calling the CURRENT token fn without
  // re-registering the provider on every render.
  const tokenRef = useRef(getAccessTokenSilently);
  tokenRef.current = getAccessTokenSilently;

  // Wire the seam SYNCHRONOUSLY on first render — a lazy useState initializer
  // runs once, before the descendant `WeeklyCommitApp` renders and reads
  // `hasAccessTokenProvider()` for its auth0 readiness check. A mount effect runs
  // too late (after the child already rendered its no-accessor fallback).
  useState(() => {
    setAccessTokenProvider(() => tokenRef.current());
    return null;
  });
  useEffect(
    () => () => {
      setAccessTokenProvider(null);
    },
    [],
  );

  // §16 — reset the RTK cache on an authenticated-identity change (keyed on
  // `user.sub`), skipping the first mount. Argless identity-scoped queries
  // (`/api/me`, `/api/plans/current`) don't re-key on a token swap, so without
  // this the prior user's data would linger (with localStorage refresh-token
  // restore, a returning different user is a real case).
  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    dispatch(baseApi.util.resetApiState());
  }, [user?.sub, dispatch]);

  return <>{children}</>;
}

export function Auth0IdentityProvider({ children }: { children: ReactNode }) {
  // `useNavigate` requires the Router ancestor (StandaloneShell mounts this
  // inside <BrowserRouter>) so `onRedirectCallback` lands on the requested route.
  const navigate = useNavigate();
  const config = resolveAuth0Config(import.meta.env, window.location.origin);

  return (
    <Auth0Provider
      domain={config.domain}
      clientId={config.clientId}
      authorizationParams={{
        redirect_uri: config.redirectUri,
        // Load-bearing: without the API audience Auth0 returns an opaque
        // userinfo token the resource-server can't validate.
        audience: config.audience,
        scope: 'openid profile email',
      }}
      useRefreshTokens
      cacheLocation="localstorage"
      onRedirectCallback={(appState?: AppState) =>
        navigate(appState?.returnTo ?? '/')
      }
    >
      <Auth0SeamWiring>{children}</Auth0SeamWiring>
    </Auth0Provider>
  );
}
