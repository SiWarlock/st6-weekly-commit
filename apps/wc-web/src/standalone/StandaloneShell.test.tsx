import { render, screen } from '@testing-library/react';
import { describe, it, expect, afterEach } from 'vitest';
import { StandaloneShell } from './StandaloneShell';
import {
  setAccessTokenProvider,
  setDemoEmployeeIdProvider,
} from '../app/authAccessor';

afterEach(() => {
  setAccessTokenProvider(null);
  setDemoEmployeeIdProvider(null);
});

describe('StandaloneShell (full standalone provider tree)', () => {
  it('standalone_mounts_app_with_full_providers: mounts WeeklyCommitApp inside router+store+theme+identity with chrome', () => {
    const { container } = render(<StandaloneShell />);

    // WC content (App token-probe) renders — the demo identity provider wired
    // the accessor seam, so WeeklyCommitApp is "ready".
    expect(container.querySelector('[data-cy="token-probe"]')).not.toBeNull();

    // Standalone-only chrome is present.
    expect(screen.getByRole('button', { name: /theme/i })).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: /persona/i }),
    ).toBeInTheDocument();
  });
});
