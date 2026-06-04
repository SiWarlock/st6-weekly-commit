import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { AppShell } from './AppShell';
import { ThemeProvider } from '../../app/theme/ThemeProvider';
import { DEMO_PERSONAS, type DemoIdentityContextValue } from '../demoIdentity';

// ST.8a — deterministic-seam tests for the standalone demo app-shell. The shell
// chrome (app-bar + persona-gated sub-nav + breadcrumb) is the host-chrome the
// production host owns (§7); standalone simulates it for the demo, tree-shaken
// from the remote (REQ-I-008, guarded by boundary.test.ts). These tests pin the
// SEAMS (route-driven nav, persona gating, breadcrumb, persona-switch reroute) —
// pixel fidelity vs the mockup is the ST.8d gstack canon-compare, not here.

// Gating + identity are read through the existing seams; the tests substitute
// values so the chrome renders deterministically without the network/getMe.
let mockIsManager = false;
vi.mock('../../routes/isManager', () => ({
  useIsManager: () => mockIsManager,
}));

let mockIdentity: DemoIdentityContextValue;
vi.mock('../demoIdentity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../demoIdentity')>();
  return { ...actual, useDemoIdentity: () => mockIdentity };
});

const MGR = DEMO_PERSONAS.find((p) => p.role === 'MANAGER')!;
const IC = DEMO_PERSONAS.find((p) => p.role === 'IC')!;

function LocationDisplay() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname}</div>;
}

function renderShell(initialPath: string) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <AppShell>
          <LocationDisplay />
        </AppShell>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  mockIsManager = false;
  mockIdentity = {
    personas: DEMO_PERSONAS,
    personaId: IC.id,
    setPersonaId: vi.fn(),
  };
});

describe('ST.8a standalone app-shell — deterministic seams', () => {
  it('nav_tab_navigates_to_route: manager nav tabs drive navigate() to the existing routes (no new routes)', () => {
    mockIsManager = true;
    mockIdentity.personaId = MGR.id;
    renderShell('/manager/command-center');

    fireEvent.click(screen.getByRole('button', { name: 'Heatmap' }));
    expect(screen.getByTestId('loc')).toHaveTextContent('/manager/heatmap');

    fireEvent.click(screen.getByRole('button', { name: 'My Weekly Commit' }));
    expect(screen.getByTestId('loc')).toHaveTextContent('/weekly-commit');

    fireEvent.click(screen.getByRole('button', { name: 'My Team' }));
    expect(screen.getByTestId('loc')).toHaveTextContent(
      '/manager/command-center',
    );
  });

  it('manager_tabs_persona_gated: an IC sees a lone "My Weekly Commit" (no manager segments); a manager sees the full segmented nav', () => {
    // IC — scope to the Primary nav (the breadcrumb also says "My Weekly Commit").
    renderShell('/weekly-commit');
    const nav = screen.getByRole('navigation', { name: 'Primary' });
    expect(within(nav).getByText('My Weekly Commit')).toBeInTheDocument();
    expect(within(nav).queryByText('My Team')).toBeNull();
    expect(within(nav).queryByText('Command Center')).toBeNull();
    expect(within(nav).queryByText('Heatmap')).toBeNull();

    // Manager (on the team surface) — full nav.
    mockIsManager = true;
    mockIdentity.personaId = MGR.id;
    renderShell('/manager/command-center');
    expect(
      screen.getByRole('button', { name: 'My Team' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'My Weekly Commit' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Command Center' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Heatmap' })).toBeInTheDocument();
  });

  it('active_tab_and_breadcrumb_reflect_route: active state + breadcrumb derive from useLocation(), not local state', () => {
    mockIsManager = true;
    mockIdentity.personaId = MGR.id;
    renderShell('/manager/heatmap');

    expect(screen.getByRole('button', { name: 'Heatmap' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(
      screen.getByRole('button', { name: 'Command Center' }),
    ).not.toHaveAttribute('aria-current', 'page');

    const crumb = screen.getByTestId('breadcrumb');
    expect(crumb).toHaveTextContent('My Team');
    expect(crumb).toHaveTextContent('Heatmap');
  });

  it('breadcrumb_on_weekly_commit_reads_week_label: the mine-surface breadcrumb is "My Weekly Commit › Week of Jun 1–7, 2026"', () => {
    renderShell('/weekly-commit');
    const crumb = screen.getByTestId('breadcrumb');
    expect(crumb).toHaveTextContent('My Weekly Commit');
    expect(crumb).toHaveTextContent('Week of Jun 1–7, 2026');
  });

  it('persona_switch_sets_persona_and_navigates_to_root: picking a persona calls setPersonaId (the §16 reset trigger) and navigates to "/" — the persona-aware RootRedirect does the role landing (no role branch in the switcher)', () => {
    // Manager context → pick an IC.
    mockIsManager = true;
    mockIdentity.personaId = MGR.id;
    const { unmount } = renderShell('/manager/command-center');
    fireEvent.click(screen.getByRole('button', { name: /persona/i }));
    fireEvent.click(
      screen.getByRole('button', {
        name: new RegExp(IC.label.split(' (')[0]!, 'i'),
      }),
    );
    expect(mockIdentity.setPersonaId).toHaveBeenCalledWith(IC.id);
    expect(screen.getByTestId('loc').textContent).toBe('/');
    unmount();

    // IC context → pick the manager. Target is "/" regardless of role.
    mockIsManager = false;
    mockIdentity = {
      personas: DEMO_PERSONAS,
      personaId: IC.id,
      setPersonaId: vi.fn(),
    };
    renderShell('/weekly-commit');
    fireEvent.click(screen.getByRole('button', { name: /persona/i }));
    fireEvent.click(
      screen.getByRole('button', {
        name: new RegExp(MGR.label.split(' (')[0]!, 'i'),
      }),
    );
    expect(mockIdentity.setPersonaId).toHaveBeenCalledWith(MGR.id);
    expect(screen.getByTestId('loc').textContent).toBe('/');
  });

  it('app_bar_shows_brand_demo_pill_and_week_label: the demo chrome carries the ST6 brand, a "Demo" pill, and the week label', () => {
    renderShell('/weekly-commit');
    expect(screen.getByText('ST6 Weekly Commit')).toBeInTheDocument();
    expect(screen.getByText('Demo')).toBeInTheDocument();
    expect(
      screen.getAllByText('Week of Jun 1–7, 2026').length,
    ).toBeGreaterThan(0);
  });
});
