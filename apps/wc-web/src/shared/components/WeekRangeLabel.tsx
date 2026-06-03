import { formatWeekRange, type WeekFormat } from '../lib/formatWeek';

export interface WeekRangeLabelProps {
  /** The org-tz Monday (ISO date) the week starts on. */
  weekStart: string;
  format?: WeekFormat;
  /** Reference date for the `relative` form (defaults to today in the app). */
  referenceDate?: string;
}

/** Renders a Monday–Sunday week range via `formatWeek` (org-tz). */
export function WeekRangeLabel({
  weekStart,
  format = 'explicit',
  referenceDate,
}: WeekRangeLabelProps) {
  return (
    <span data-cy="week-range" className="text-meta text-ink-secondary">
      {formatWeekRange(weekStart, format, referenceDate)}
    </span>
  );
}
