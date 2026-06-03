import { lazy, Suspense } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Link } from 'react-router-dom';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { AppRoutes } from './AppRoutes';
import { IsManagerContext } from './isManager';
import { RouteErrorBoundary } from './RouteErrorBoundary';
import { LoadingState } from '../shared/components/LoadingState';

afterEach(() => {
  vi.restoreAllMocks();
});

/** Mount AppRoutes at `path` inside a host router with a fixed manager persona. */
function renderAt(path: string, isManager: boolean) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <IsManagerContext.Provider value={isManager}>
        <AppRoutes />
      </IsManagerContext.Provider>
    </MemoryRouter>,
  );
}

describe('AppRoutes — lazy, code-split route tree (9.4 / REQ-NF-005)', () => {
  // path → unique content phrase rendered by that route's lazy chunk, + whether
  // the route is manager-gated (needs isManager=true to resolve).
  const ROUTES: ReadonlyArray<{
    path: string;
    phrase: RegExp;
    manager: boolean;
  }> = [
    { path: '/', phrase: /weekly commit/i, manager: false },
    { path: '/weekly-commit', phrase: /coming in 9\.7/i, manager: false },
    {
      path: '/weekly-commit/history/abc-123',
      phrase: /coming in 9\.8/i,
      manager: false,
    },
    {
      path: '/manager/command-center',
      phrase: /coming in 9\.9/i,
      manager: true,
    },
    { path: '/manager/heatmap', phrase: /coming in 9\.10/i, manager: true },
  ];

  it('each_route_renders_its_lazy_chunk_behind_suspense: every route shows the Suspense LoadingState, then resolves its own lazy element', async () => {
    for (const { path, phrase, manager } of ROUTES) {
      const { unmount } = renderAt(path, manager);
      // The shared LoadingState (role=status) is the Suspense fallback while the
      // dynamic import is pending (sub-second initial render intent, REQ-NF-005).
      expect(screen.getByRole('status')).toBeInTheDocument();
      // ...then the route's own lazy chunk resolves and renders.
      expect(await screen.findByText(phrase)).toBeInTheDocument();
      unmount();
    }
  });

  it('ic_persona_has_no_manager_route_entry: an IC (isManager=false) hitting a manager URL is redirected to the default route and sees no manager element or entry point (REQ-UX-005)', async () => {
    const { container } = renderAt('/manager/command-center', false);

    // Redirected to the persona-aware default route ('/') — the manager chunk
    // never renders for an IC.
    expect(await screen.findByText(/weekly commit/i)).toBeInTheDocument();
    expect(screen.queryByText(/coming in 9\.9/i)).toBeNull();
    // ...and the default surface exposes NO manager entry point in the UI.
    expect(container.querySelector('a[href*="/manager"]')).toBeNull();
  });

  it('manager_persona_resolves_manager_routes: a manager (isManager=true) resolves both /manager/* lazy chunks (REQ-UX-005 positive control)', async () => {
    const cc = renderAt('/manager/command-center', true);
    expect(await screen.findByText(/coming in 9\.9/i)).toBeInTheDocument();
    cc.unmount();

    renderAt('/manager/heatmap', true);
    expect(await screen.findByText(/coming in 9\.10/i)).toBeInTheDocument();
  });

  it('failed_lazy_import_renders_errorstate: a rejected dynamic import surfaces the shared ErrorState (a generic, leak-free message), never a blank screen', async () => {
    // Silence React's expected error-boundary console noise for the caught throw.
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const Boom = lazy(() =>
      Promise.reject(new Error('secret-chunk-path load failed')),
    );

    render(
      <RouteErrorBoundary>
        <Suspense fallback={<LoadingState delayMs={0} />}>
          <Boom />
        </Suspense>
      </RouteErrorBoundary>,
    );

    // The error view-state renders (data-cy=error-state from the shared ErrorState).
    await waitFor(() =>
      expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull(),
    );
    // Safety rule #7: the raw error/chunk detail is NOT leaked to the user.
    expect(screen.queryByText(/secret-chunk-path/)).toBeNull();
  });

  it('route_change_resolves_correct_lazy_module: navigating between routes swaps to the correct lazy chunk', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/weekly-commit']}>
        <IsManagerContext.Provider value={false}>
          <nav>
            <Link to="/weekly-commit/history/p-1">history</Link>
          </nav>
          <AppRoutes />
        </IsManagerContext.Provider>
      </MemoryRouter>,
    );

    expect(await screen.findByText(/coming in 9\.7/i)).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /history/i }));

    expect(await screen.findByText(/coming in 9\.8/i)).toBeInTheDocument();
    expect(screen.queryByText(/coming in 9\.7/i)).toBeNull();
  });
});
