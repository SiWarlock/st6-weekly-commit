import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, afterEach } from 'vitest';
import { StandaloneShell } from './StandaloneShell';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../app/authAccessor';

afterEach(() => {
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
});

describe('StandaloneShell (full standalone provider tree)', () => {
  it('standalone_mounts_app_with_full_providers: mounts WeeklyCommitApp inside router+store+theme+identity with chrome', async () => {
    const { container } = render(<StandaloneShell />);

    // WC content (the lazy '/' route's App token-probe) resolves — the demo
    // identity provider wired the accessor seam, so WeeklyCommitApp is "ready".
    await waitFor(() =>
      expect(container.querySelector('[data-cy="token-probe"]')).not.toBeNull(),
    );

    // Standalone-only chrome is present.
    expect(screen.getByRole('button', { name: /theme/i })).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: /persona/i }),
    ).toBeInTheDocument();
  });
});
