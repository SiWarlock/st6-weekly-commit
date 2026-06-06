import { useCallback } from "react";
import { useAuth0 } from "@auth0/auth0-react";
import { WeeklyCommitMount } from "./WeeklyCommitMount";
import "./portal.css";

const AUDIENCE = import.meta.env.VITE_AUTH0_AUDIENCE as string;

/** Dummy parent-portal nav so it's visually obvious Weekly Commit is embedded. */
const NAV_SECTIONS = [
  {
    heading: "Workspace",
    items: [
      { label: "Dashboard", icon: "▦" },
      { label: "Directory", icon: "☰" },
      { label: "Weekly Commit", icon: "◎", active: true },
      { label: "Reports", icon: "▤" },
    ],
  },
  {
    heading: "Admin",
    items: [
      { label: "Billing", icon: "◷" },
      { label: "Settings", icon: "⚙" },
    ],
  },
];

function PortalChrome({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user, logout } = useAuth0();

  return (
    <div className="portal">
      <header className="portal-topbar">
        <div className="portal-brand">
          <span className="portal-logo">▲</span>
          <span className="portal-brand-name">Acme Portal</span>
          <span className="portal-brand-sub">Employee Workspace</span>
        </div>
        <div className="portal-topbar-right">
          <span className="portal-plugin-tag">plugin: wc_web (remote)</span>
          {isAuthenticated && (
            <div className="portal-user">
              <span className="portal-user-name">
                {user?.name ?? user?.email ?? "Signed in"}
              </span>
              <button
                type="button"
                className="portal-btn portal-btn-ghost"
                onClick={() =>
                  logout({
                    logoutParams: {
                      // Return to the portal root (under its base path, e.g.
                      // /portal/) — BASE_URL carries the trailing slash.
                      returnTo: `${window.location.origin}${import.meta.env.BASE_URL}`,
                    },
                  })
                }
              >
                Log out
              </button>
            </div>
          )}
        </div>
      </header>

      <div className="portal-body">
        <nav className="portal-nav" aria-label="Acme Portal navigation">
          {NAV_SECTIONS.map((section) => (
            <div key={section.heading} className="portal-nav-section">
              <p className="portal-nav-heading">{section.heading}</p>
              {section.items.map((item) => (
                <div
                  key={item.label}
                  className={
                    "portal-nav-item" +
                    ("active" in item && item.active ? " is-active" : "")
                  }
                >
                  <span className="portal-nav-icon" aria-hidden="true">
                    {item.icon}
                  </span>
                  {item.label}
                </div>
              ))}
            </div>
          ))}
        </nav>

        <main className="portal-main">
          <div className="portal-panel">
            <div className="portal-panel-header">
              <h1 className="portal-panel-title">Weekly Commit</h1>
              <span className="portal-panel-badge">
                Embedded micro-frontend
              </span>
            </div>
            <div className="portal-panel-body">{children}</div>
          </div>
        </main>
      </div>
    </div>
  );
}

/**
 * The auth-gated main panel. Renders a login prompt when signed out, a spinner
 * while Auth0 resolves the session (incl. the redirect code-exchange), and the
 * mounted remote once authenticated. Gating the remote behind `isAuthenticated`
 * also keeps the remote's <Routes> from running during the /callback exchange.
 */
function WeeklyCommitPanel() {
  const {
    isAuthenticated,
    isLoading,
    error,
    loginWithRedirect,
    getAccessTokenSilently,
  } = useAuth0();

  const getAccessToken = useCallback(
    () =>
      getAccessTokenSilently({ authorizationParams: { audience: AUDIENCE } }),
    [getAccessTokenSilently],
  );

  if (isLoading) {
    return (
      <div className="wc-mount-status" role="status" aria-live="polite">
        <span className="wc-spinner" aria-hidden="true" />
        Checking your session…
      </div>
    );
  }

  if (error) {
    return (
      <div className="wc-mount-error" role="alert">
        <strong>Sign-in failed.</strong>
        <p>{error.message}</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="portal-login">
        <h2 className="portal-login-title">Sign in to load Weekly Commit</h2>
        <p className="portal-login-sub">
          The Weekly Commit module authenticates with your Acme SSO (Auth0) and
          calls the live API.
        </p>
        <button
          type="button"
          className="portal-btn portal-btn-primary"
          onClick={() => loginWithRedirect({ appState: { returnTo: "/" } })}
        >
          Sign in with Auth0
        </button>
      </div>
    );
  }

  return <WeeklyCommitMount getAccessToken={getAccessToken} />;
}

export function Portal() {
  return (
    <PortalChrome>
      <WeeklyCommitPanel />
    </PortalChrome>
  );
}
