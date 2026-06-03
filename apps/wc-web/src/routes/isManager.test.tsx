import { renderHook } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { useIsManager } from './isManager';
import { useCurrentUser } from '../features/me/useCurrentUser';

vi.mock('../features/me/useCurrentUser');

afterEach(() => {
  vi.restoreAllMocks();
});

function mockCurrentUser(value: {
  isManager?: boolean;
  isLoading?: boolean;
  isError?: boolean;
  role?: 'IC' | 'MANAGER';
}) {
  vi.mocked(useCurrentUser).mockReturnValue({
    role: value.role,
    isManager: value.isManager ?? false,
    persona: undefined,
    displayName: undefined,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
  });
}

describe('useIsManager (real-role gating, REQ-UX-005)', () => {
  it('useIsManager_reads_real_role: returns MeDto.isManager once the role is confirmed', () => {
    mockCurrentUser({ isManager: true, role: 'MANAGER' });
    expect(renderHook(() => useIsManager()).result.current).toBe(true);

    mockCurrentUser({ isManager: false, role: 'IC' });
    expect(renderHook(() => useIsManager()).result.current).toBe(false);
  });

  it('gating_fails_closed_while_me_pending: returns false while getMe is pending OR errored — never flashes a manager entry to an unconfirmed actor', () => {
    // Pending: even an (eventually-manager) actor is gated false until confirmed.
    mockCurrentUser({ isManager: true, isLoading: true });
    expect(renderHook(() => useIsManager()).result.current).toBe(false);

    // Errored: fail-closed (no manager surface on a failed identity read).
    mockCurrentUser({ isManager: true, isError: true });
    expect(renderHook(() => useIsManager()).result.current).toBe(false);
  });
});
