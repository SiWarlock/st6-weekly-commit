import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { DemoIdentityProvider } from './DemoIdentityProvider';
import { PersonaSwitcher } from './PersonaSwitcher';
import {
  getDemoEmployeeId,
  hasAccessTokenProvider,
  setAccessTokenProvider,
  setDemoEmployeeIdProvider,
} from '../app/authAccessor';

afterEach(() => {
  setAccessTokenProvider(null);
  setDemoEmployeeIdProvider(null);
});

describe('DemoIdentityProvider + PersonaSwitcher (standalone-only demo identity)', () => {
  it('demo_provider_wires_9_1_seam: mounting injects both 9.1 providers; persona id resolves', () => {
    render(
      <DemoIdentityProvider>
        <div />
      </DemoIdentityProvider>,
    );
    // The 9.1 accessor seam is now wired by the demo provider.
    expect(hasAccessTokenProvider()).toBe(true);
    expect(getDemoEmployeeId()).toBeTruthy();
  });

  it('persona_switch_changes_demo_header: switching persona changes the X-Demo-Employee-Id the seam yields', async () => {
    const user = userEvent.setup();
    render(
      <DemoIdentityProvider>
        <PersonaSwitcher />
      </DemoIdentityProvider>,
    );

    const before = getDemoEmployeeId();
    expect(before).toBeTruthy();

    const select = screen.getByRole('combobox', { name: /persona/i });
    const options = within(select)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value);
    const other = options.find((v) => v !== before);
    expect(other).toBeTruthy();

    await user.selectOptions(select, other as string);

    expect(getDemoEmployeeId()).toBe(other);
    expect(getDemoEmployeeId()).not.toBe(before);
  });
});
