import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Pagination } from './Pagination';

describe('Pagination (B.20 envelope)', () => {
  it('pagination_derives_from_b20_envelope: renders controls from page object; hidden when totalPages <= 1', async () => {
    const onPageChange = vi.fn();
    const { container, rerender } = render(
      <Pagination
        page={{ number: 0, size: 20, totalElements: 50, totalPages: 3 }}
        onPageChange={onPageChange}
      />,
    );
    expect(container.querySelector('[data-cy="pagination"]')).not.toBeNull();
    // 0-based page.number → human "Page 1 of 3".
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();

    // Prev disabled on first page; Next advances.
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /next/i }));
    expect(onPageChange).toHaveBeenCalledWith(1);

    // Single page → renders nothing.
    rerender(
      <Pagination
        page={{ number: 0, size: 20, totalElements: 3, totalPages: 1 }}
      />,
    );
    expect(container.querySelector('[data-cy="pagination"]')).toBeNull();
  });
});
