/// <reference types="vite/client" />

// Typed build-time env (Appendix D.1) — strict typing for `import.meta.env`
// so the auth-mode XOR branch is exhaustively typed (no `any`).
interface ImportMetaEnv {
  readonly VITE_AUTH_MODE?: 'demo' | 'auth0';
  readonly VITE_API_BASE_URL?: string;
  // ST.7a — standalone MSW mock toggle: 'true' forces mocks on (e.g. a demo-video
  // build); 'false' forces them off; unset = on in dev, off in prod.
  readonly VITE_USE_MOCKS?: 'true' | 'false';
  // 9.17 — Auth0 SPA config (required when VITE_AUTH_MODE=auth0; resolveAuth0Config
  // fails fast if unset in an auth0-mode build). See .env.example for the contract.
  readonly VITE_AUTH0_DOMAIN?: string;
  readonly VITE_AUTH0_CLIENT_ID?: string;
  readonly VITE_AUTH0_AUDIENCE?: string;
}
