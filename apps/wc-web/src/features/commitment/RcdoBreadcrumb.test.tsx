import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RcdoBreadcrumb } from './RcdoBreadcrumb';
import type { RcdoBreadcrumbDto } from '../../shared/lib/dtos';

function bc(overrides: Partial<RcdoBreadcrumbDto> = {}): RcdoBreadcrumbDto {
  return {
    rallyCryId: 'rc-1',
    rallyCryTitle: 'Win the quarter',
    definingObjectiveId: 'do-1',
    definingObjectiveTitle: 'Ship v2',
    supportingOutcomeId: 'so-1',
    supportingOutcomeTitle: 'Onboarding flow',
    ...overrides,
  };
}

describe('RcdoBreadcrumb (read-only RC › DO › SO chip, §5)', () => {
  it('rcdo_breadcrumb_renders_do_so_when_linked: renders DO › SO with the SO title emphasized; the org-wide Rally Cry is omitted from the per-card chip (Cadence atoms.jsx:142-149)', () => {
    const { container } = render(<RcdoBreadcrumb breadcrumb={bc()} />);

    expect(
      container.querySelector('[data-cy="rcdo-breadcrumb"]'),
    ).not.toBeNull();
    // DO title shown; SO title emphasized in its own element.
    expect(screen.getByText('Ship v2')).toBeInTheDocument();
    const so = container.querySelector('[data-cy="rcdo-so"]');
    expect(so).toHaveTextContent('Onboarding flow');
    // The Rally Cry is intentionally NOT rendered (constant org-wide → noise).
    expect(screen.queryByText('Win the quarter')).toBeNull();
    // Emphasize the SO TITLE, not the UUID — the id is never rendered.
    expect(container).not.toHaveTextContent('so-1');
  });

  it('rcdo_breadcrumb_renders_missing_warning_when_unlinked: with no breadcrumb, renders the warning-tone "No Supporting Outcome linked" variant (icon + text)', () => {
    const { container } = render(<RcdoBreadcrumb />);

    const missing = container.querySelector(
      '[data-cy="rcdo-breadcrumb-missing"]',
    );
    expect(missing).not.toBeNull();
    expect(
      screen.getByText(/no supporting outcome linked/i),
    ).toBeInTheDocument();
    // Glyph present (not color alone).
    expect(missing!.querySelector('svg')).not.toBeNull();
    // The linked variant is NOT rendered.
    expect(container.querySelector('[data-cy="rcdo-breadcrumb"]')).toBeNull();
  });

  it('so_text_not_monospace: the SO/objective text renders in the regular body weight, not font-mono (§C.3)', () => {
    const { container } = render(<RcdoBreadcrumb breadcrumb={bc()} />);
    const so = container.querySelector('[data-cy="rcdo-so"]');
    expect(so).not.toBeNull();
    expect(so!.className).not.toMatch(/font-mono/);
  });
});
