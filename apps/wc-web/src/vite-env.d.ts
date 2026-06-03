/// <reference types="vite/client" />

// Typed build-time env (Appendix D.1) — strict typing for `import.meta.env`
// so the auth-mode XOR branch is exhaustively typed (no `any`).
interface ImportMetaEnv {
  readonly VITE_AUTH_MODE?: 'demo' | 'auth0';
  readonly VITE_API_BASE_URL?: string;
}
