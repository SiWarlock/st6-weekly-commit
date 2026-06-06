import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, afterEach, vi } from 'vitest';
import WeeklyCommitApp from './WeeklyCommitApp';
import {
  hasAccessTokenProvider,
  setAccessTokenProvider,
  getAccessToken,
} from '../app/authAccessor';

// §29 regression pin (the live host "No access-token provider configured" bug):
// the host accessor must be registered SYNCHRONOUSLY during WeeklyCommitApp's own
// render — BEFORE the child <AppRoutes/> renders and dispatches the eager first RTK
// Query (useCurrentUser → meApi → prepareHeaders → getAccessToken()). Child effects
// run before parent effects, so a mount-useEffect registration is too late and the
// first query throws. This probe replaces AppRoutes and captures whether the seam
// was already wired at the child's first render; a useEffect-based registration
// would record `false` here.
let seenAtChildRender: boolean | null = null;
vi.mock('../routes/AppRoutes', () => ({
  AppRoutes: () => {
    seenAtChildRender = hasAccessTokenProvider();
    return null;
  },
}));

afterEach(() => {
  seenAtChildRender = null;
  setAccessTokenProvider(null);
  vi.unstubAllEnvs();
});

describe('WeeklyCommitApp — host-accessor registration timing (§29)', () => {
  it('registers the host getAccessToken SYNCHRONOUSLY: the seam is wired before the child (first-query) renders', () => {
    render(
      <MemoryRouter>
        <WeeklyCommitApp getAccessToken={async () => 'host-jwt'} />
      </MemoryRouter>,
    );
    // True ⇒ provider was set during WeeklyCommitApp's render (before AppRoutes),
    // so prepareHeaders on the eager first query finds it. False ⇒ the §29 bug.
    expect(seenAtChildRender).toBe(true);
  });

  it('the synchronously-registered provider resolves the host token', async () => {
    render(
      <MemoryRouter>
        <WeeklyCommitApp getAccessToken={async () => 'host-jwt-xyz'} />
      </MemoryRouter>,
    );
    expect(await getAccessToken()).toBe('host-jwt-xyz');
  });

  it('standalone path (no getAccessToken prop) does NOT register — the seam stays owned by the standalone provider', () => {
    setAccessTokenProvider(null);
    render(
      <MemoryRouter>
        <WeeklyCommitApp />
      </MemoryRouter>,
    );
    // No prop ⇒ WeeklyCommitApp registers nothing (Auth0IdentityProvider owns it).
    expect(seenAtChildRender).toBe(false);
  });
});
