import { type ReactNode } from 'react';
import { HiCalendar } from 'react-icons/hi';
import { PersonaSwitcher } from '../PersonaSwitcher';
import { ThemeToggle } from '../ThemeToggle';
import { currentWeekLabel } from './navModel';

interface AppBarProps {
  /**
   * The right-side identity affordance. Defaults to the demo `PersonaSwitcher`;
   * `StandaloneShell` injects the `Auth0IdentitySlot` in auth0 mode (9.17). Keeps
   * AppBar mode-agnostic (no `VITE_AUTH_MODE` read here).
   */
  identitySlot?: ReactNode;
}

/**
 * ST.8a — the demo top app-bar (the chrome the production PA host owns in the
 * composed app; standalone simulates it for the demo, tree-shaken from the remote
 * — REQ-I-008). Brand mark + "ST6 Weekly Commit" + a "Demo" pill (left), the week
 * label, and a right-side identity slot (the PersonaSwitcher or the auth0 identity
 * slot + the ThemeToggle). Token-native — the brand mark uses `currentColor` +
 * `text-white`, never a hex.
 */
export function AppBar({ identitySlot }: AppBarProps = {}) {
  return (
    <header className="flex items-center gap-4 border-b border-border bg-surface-raised px-6 py-3">
      <div className="flex items-center gap-2">
        <span className="flex h-6 w-6 items-center justify-center rounded-md bg-brand-500 text-white">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2.4}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden
            className="h-4 w-4"
          >
            <path d="M5 13l4 4L19 7" />
          </svg>
        </span>
        <span className="text-body font-semibold text-ink-primary">
          ST6 Weekly Commit
        </span>
        <span className="rounded-full bg-brand-soft px-2 py-1 text-meta font-medium uppercase tracking-wide text-brand-ink">
          Demo
        </span>
      </div>

      <span className="ml-auto inline-flex items-center gap-1 text-label text-ink-muted">
        <HiCalendar aria-hidden className="h-4 w-4" />
        {currentWeekLabel(new Date())}
      </span>

      <div className="flex items-center gap-3">
        {identitySlot ?? <PersonaSwitcher />}
        <ThemeToggle />
      </div>
    </header>
  );
}
