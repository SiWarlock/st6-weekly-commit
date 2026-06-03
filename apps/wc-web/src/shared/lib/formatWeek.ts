export type WeekFormat = 'explicit' | 'relative' | 'compact';

const MS_DAY = 86_400_000;

/**
 * Parse a server `weekStart` (the org-tz Monday) as a UTC date. Date-only, so we
 * format in UTC to avoid the negative-offset "shows a day early" drift. The
 * org-tz week *derivation* from a timestamp is a separate, server-owned concern.
 */
function parseUtcDate(iso: string): Date {
  return new Date(iso.includes('T') ? iso : `${iso}T00:00:00Z`);
}

/** Day-number (UTC) of the Monday of the week containing `d`. */
function mondayDayNumber(d: Date): number {
  const offsetToMonday = (d.getUTCDay() + 6) % 7; // 0=Sun → 6, 1=Mon → 0, …
  return Math.floor((d.getTime() - offsetToMonday * MS_DAY) / MS_DAY);
}

function part(d: Date, opts: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', ...opts }).format(
    d,
  );
}

/**
 * Format a Monday–Sunday week range from `weekStartISO` (the org-tz Monday;
 * the explicit/compact forms assume this precondition and do NOT re-normalize —
 * the server provides the Monday per B.20).
 *   explicit  → "Jun 1–7, 2026" (same month) / "Jun 29 – Jul 5, 2026" (cross)
 *   compact   → "Jun 1"
 *   relative  → "This/Last/Next week" vs `referenceISO`, else explicit
 */
export function formatWeekRange(
  weekStartISO: string,
  format: WeekFormat = 'explicit',
  referenceISO?: string,
): string {
  const start = parseUtcDate(weekStartISO);
  const end = new Date(start.getTime() + 6 * MS_DAY);

  const startMonth = part(start, { month: 'short' });
  const startDay = part(start, { day: 'numeric' });

  if (format === 'compact') {
    return `${startMonth} ${startDay}`;
  }

  if (format === 'relative' && referenceISO !== undefined) {
    const diffWeeks =
      (mondayDayNumber(start) - mondayDayNumber(parseUtcDate(referenceISO))) /
      7;
    if (diffWeeks === 0) return 'This week';
    if (diffWeeks === 1) return 'Next week';
    if (diffWeeks === -1) return 'Last week';
    // otherwise fall through to the explicit range
  }

  const endMonth = part(end, { month: 'short' });
  const endDay = part(end, { day: 'numeric' });
  const startYear = part(start, { year: 'numeric' });
  const endYear = part(end, { year: 'numeric' });

  // Year-boundary week: show BOTH years so the December portion isn't mislabeled.
  if (startYear !== endYear) {
    return `${startMonth} ${startDay}, ${startYear} – ${endMonth} ${endDay}, ${endYear}`;
  }
  return startMonth === endMonth
    ? `${startMonth} ${startDay}–${endDay}, ${endYear}`
    : `${startMonth} ${startDay} – ${endMonth} ${endDay}, ${endYear}`;
}
