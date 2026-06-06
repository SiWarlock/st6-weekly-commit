import { BrowserRouter, useNavigate } from "react-router-dom";
import { Auth0Provider, type AppState } from "@auth0/auth0-react";
import { resolveAuth0Config } from "./auth/authConfig";
import { Portal } from "./portal/Portal";

/**
 * Auth0 wrapper. Mounted INSIDE <BrowserRouter> so `onRedirectCallback` can use
 * the router's `navigate` to land the post-login redirect on the requested route
 * (mirrors apps/wc-web's Auth0IdentityProvider). The same <BrowserRouter> is the
 * router the federated remote consumes.
 */
function AuthGate() {
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
        scope: "openid profile email",
      }}
      useRefreshTokens
      cacheLocation="localstorage"
      onRedirectCallback={(appState?: AppState) =>
        navigate(appState?.returnTo ?? "/")
      }
    >
      <Portal />
    </Auth0Provider>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <AuthGate />
    </BrowserRouter>
  );
}
