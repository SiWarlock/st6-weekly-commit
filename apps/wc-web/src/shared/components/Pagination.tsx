import { HiChevronLeft, HiChevronRight } from 'react-icons/hi';

/** The B.20 paginated-envelope `page` object (Spring Page — 0-based `number`). */
export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PaginationProps {
  page: PageInfo;
  onPageChange?: (pageNumber: number) => void;
}

/** Pagination control driven by the B.20 envelope; hidden when ≤ 1 page. */
export function Pagination({ page, onPageChange }: PaginationProps) {
  if (page.totalPages <= 1) {
    return null;
  }

  const { number, totalPages } = page;
  const atFirst = number <= 0;
  const atLast = number >= totalPages - 1;
  const buttonClasses =
    'inline-flex items-center rounded-md border border-border-strong bg-surface-raised p-1.5 text-ink-secondary hover:bg-surface-hover hover:text-ink-primary disabled:cursor-not-allowed disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-brand-ring';

  return (
    <nav
      data-cy="pagination"
      aria-label="Pagination"
      className="flex items-center gap-3"
    >
      <button
        type="button"
        aria-label="Previous page"
        disabled={atFirst}
        onClick={() => onPageChange?.(number - 1)}
        className={buttonClasses}
      >
        <HiChevronLeft aria-hidden className="h-4 w-4" />
      </button>
      <span data-cy="page-indicator" className="text-meta text-ink-secondary">
        Page {number + 1} of {totalPages}
      </span>
      <button
        type="button"
        aria-label="Next page"
        disabled={atLast}
        onClick={() => onPageChange?.(number + 1)}
        className={buttonClasses}
      >
        <HiChevronRight aria-hidden className="h-4 w-4" />
      </button>
    </nav>
  );
}
