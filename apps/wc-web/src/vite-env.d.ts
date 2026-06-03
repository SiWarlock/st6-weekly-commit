/// <reference types="vite/client" />

// Typed build-time env (Appendix D.1) — strict typing for `import.meta.env`
// so the auth-mode XOR branch is exhaustively typed (no `any`).
interface ImportMetaEnv {
  readonly VITE_AUTH_MODE?: 'demo' | 'auth0';
  readonly VITE_API_BASE_URL?: string;
  // ST.7a — standalone MSW mock toggle: 'true' forces mocks on (e.g. a demo-video
  // build); 'false' forces them off; unset = on in dev, off in prod.
  readonly VITE_USE_MOCKS?: 'true' | 'false';
}
