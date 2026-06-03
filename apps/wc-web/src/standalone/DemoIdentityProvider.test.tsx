import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach, vi } from 'vitest';
import type { ReactElement } from 'react';
import { Provider } from 'react-redux';
import { DemoIdentityProvider } from './DemoIdentityProvider';
import { PersonaSwitcher } from './PersonaSwitcher';
import { useDemoIdentity } from './demoIdentity';
import { store } from '../app/store';
import { baseApi } from '../app/baseApi';
import {
  applyDemoAuthHeader,
  hasAccessTokenProvider,
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../app/authAccessor';

afterEach(() => {
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
  vi.restoreAllMocks();
});

/** Run the injected demo applier and read back the demo header it attaches. */
function demoHeaderValue(): string | null {
  const headers = new Headers();
  applyDemoAuthHeader(headers);
  return headers.get('X-Demo-Employee-Id');
}

/**
 * Render within the Redux store — `DemoIdentityProvider` now `useDispatch`es to
 * reset the RTK cache on persona change (ST.7d), so it requires a `<Provider>`
 * (always present in production via `StandaloneShell`).
 */
function renderWithStore(ui: ReactElement) {
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('DemoIdentityProvider + PersonaSwitcher (standalone-only demo identity)', () => {
  it('demo_provider_wires_9_1_seam: mounting injects both 9.1 providers; persona id resolves', () => {
    renderWithStore(
      <DemoIdentityProvider>
        <div />
      </DemoIdentityProvider>,
    );
    // The 9.1 accessor seam + the demo-header applier are wired by the provider.
    expect(hasAccessTokenProvider()).toBe(true);
    expect(demoHeaderValue()).toBeTruthy();
  });

  it('persona_switch_changes_demo_header: switching persona changes the X-Demo-Employee-Id the seam yields', async () => {
    const user = userEvent.setup();
    renderWithStore(
      <DemoIdentityProvider>
        <PersonaSwitcher />
      </DemoIdentityProvider>,
    );

    const before = demoHeaderValue();
    expect(before).toBeTruthy();

    const select = screen.getByRole('combobox', { name: /persona/i });
    const options = within(select)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value);
    const other = options.find((v) => v !== before);
    expect(other).toBeTruthy();

    await user.selectOptions(select, other as string);

    expect(demoHeaderValue()).toBe(other);
    expect(demoHeaderValue()).not.toBe(before);
  });

  it('empty_persona_degrades_to_no_demo_header: a falsy persona attaches no X-Demo-Employee-Id (parity with the pre-split truthy guard)', async () => {
    const user = userEvent.setup();
    function ClearPersona() {
      const { setPersonaId } = useDemoIdentity();
      return (
        <button type="button" onClick={() => setPersonaId('')}>
          clear persona
        </button>
      );
    }
    renderWithStore(
      <DemoIdentityProvider>
        <ClearPersona />
      </DemoIdentityProvider>,
    );

    // Default persona → a header is attached.
    expect(demoHeaderValue()).toBeTruthy();

    // Falsy/empty persona → the applier's truthy guard attaches nothing (no
    // empty `X-Demo-Employee-Id:`), preserving the pre-split degrade behavior.
    await user.click(screen.getByRole('button', { name: /clear persona/i }));
    expect(demoHeaderValue()).toBeNull();
  });

  // ST.7d — PRIORITY correctness: identity-scoped queries (`/api/me`,
  // `/api/plans/current`, the manager reads) are cached under argless keys, so a
  // persona/header change alone does NOT change the cache key → no refetch → the
  // previous persona's data would stay on screen. The provider must reset the RTK
  // cache on persona change so every active query re-issues with the new identity.
  it('persona_switch_resets_api_cache: switching persona dispatches baseApi.util.resetApiState() so identity-scoped queries refetch; the initial mount does NOT reset (stale-data correctness fix)', async () => {
    const user = userEvent.setup();
    const resetType = baseApi.util.resetApiState().type;
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    function SwitchPersona() {
      const { setPersonaId } = useDemoIdentity();
      return (
        <button
          type="button"
          onClick={() => setPersonaId('demo-employee-ic-2')}
        >
          switch persona
        </button>
      );
    }

    render(
      <Provider store={store}>
        <DemoIdentityProvider>
          <SwitchPersona />
        </DemoIdentityProvider>
      </Provider>,
    );

    // Initial mount must NOT reset the cache (no needless wipe on first render).
    expect(
      dispatchSpy.mock.calls.some(
        ([a]) => (a as { type?: string }).type === resetType,
      ),
    ).toBe(false);

    await user.click(screen.getByRole('button', { name: /switch persona/i }));

    // After a persona switch, the cache is reset → all queries refetch with the
    // new X-Demo-Employee-Id.
    expect(
      dispatchSpy.mock.calls.some(
        ([a]) => (a as { type?: string }).type === resetType,
      ),
    ).toBe(true);
  });
});
