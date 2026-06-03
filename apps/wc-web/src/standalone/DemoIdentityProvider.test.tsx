import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { DemoIdentityProvider } from './DemoIdentityProvider';
import { PersonaSwitcher } from './PersonaSwitcher';
import { useDemoIdentity } from './demoIdentity';
import {
  applyDemoAuthHeader,
  hasAccessTokenProvider,
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../app/authAccessor';

afterEach(() => {
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
});

/** Run the injected demo applier and read back the demo header it attaches. */
function demoHeaderValue(): string | null {
  const headers = new Headers();
  applyDemoAuthHeader(headers);
  return headers.get('X-Demo-Employee-Id');
}

describe('DemoIdentityProvider + PersonaSwitcher (standalone-only demo identity)', () => {
  it('demo_provider_wires_9_1_seam: mounting injects both 9.1 providers; persona id resolves', () => {
    render(
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
    render(
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
    render(
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
});
