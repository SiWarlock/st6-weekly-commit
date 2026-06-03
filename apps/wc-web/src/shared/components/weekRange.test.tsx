import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { WeekRangeLabel } from './WeekRangeLabel';
import { formatWeekRange } from '../lib/formatWeek';

describe('formatWeek (org-tz Mon–Sun)', () => {
  it('week_range_label_formats_mon_sun: WeekRangeLabel renders the Mon–Sun range', () => {
    render(<WeekRangeLabel weekStart="2026-06-01" />);
    expect(screen.getByText('Jun 1–7, 2026')).toBeInTheDocument();
  });

  it('formatWeekRange: explicit same-month, cross-month, and compact forms', () => {
    expect(formatWeekRange('2026-06-01')).toBe('Jun 1–7, 2026');
    expect(formatWeekRange('2026-06-29')).toBe('Jun 29 – Jul 5, 2026');
    // Year-boundary week shows BOTH years (the Dec start isn't mislabeled).
    expect(formatWeekRange('2025-12-29')).toBe('Dec 29, 2025 – Jan 4, 2026');
    expect(formatWeekRange('2026-06-01', 'compact')).toBe('Jun 1');
  });

  it('formatWeekRange: relative form vs an injectable reference date', () => {
    // reference inside the week → "This week"; otherwise falls back to explicit.
    expect(formatWeekRange('2026-06-01', 'relative', '2026-06-03')).toBe(
      'This week',
    );
    expect(formatWeekRange('2026-06-08', 'relative', '2026-06-03')).toBe(
      'Next week',
    );
    expect(formatWeekRange('2026-05-25', 'relative', '2026-06-03')).toBe(
      'Last week',
    );
  });
});
