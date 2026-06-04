import { useState } from 'react';
import { HiChevronDown } from 'react-icons/hi';

export interface FilterOption {
  value: string;
  label: string;
}

/**
 * ST.8b-2 — a hand-rolled, token-native filter dropdown chip (the ST.8a
 * PersonaSwitcher precedent: a trigger button + a fixed click-catcher backdrop;
 * jsdom-deterministic, no Flowbite). Selecting an option calls `onSelect(value)`;
 * the "All" item clears the filter (`onSelect(undefined)`). A remote-reachable
 * feature surface — imports nothing standalone-only.
 */
export function FilterDropdown({
  label,
  options,
  onSelect,
}: {
  label: string;
  options: FilterOption[];
  onSelect: (value: string | undefined) => void;
}) {
  const [open, setOpen] = useState(false);

  const pick = (value: string | undefined) => {
    onSelect(value);
    setOpen(false);
  };

  return (
    <div className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex items-center gap-1 rounded-md border border-border bg-surface-raised px-2 py-1 text-label text-ink-secondary transition-colors duration-base hover:bg-surface-hover hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      >
        {label}
        <HiChevronDown aria-hidden className="h-4 w-4 text-ink-muted" />
      </button>

      {open && (
        <>
          <div
            className="fixed inset-0 z-40"
            aria-hidden
            onClick={() => setOpen(false)}
          />
          <div className="absolute left-0 z-50 mt-1 max-h-64 w-56 overflow-auto rounded-md border border-border bg-surface-raised py-1 shadow-pop">
            <button
              type="button"
              onClick={() => pick(undefined)}
              className="flex w-full items-center px-3 py-2 text-label text-ink-secondary hover:bg-surface-hover"
            >
              All
            </button>
            {options.map((o) => (
              <button
                key={o.value}
                type="button"
                onClick={() => pick(o.value)}
                className="flex w-full items-center px-3 py-2 text-label text-ink-primary hover:bg-surface-hover"
              >
                {o.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
