import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { HiSelector } from 'react-icons/hi';
import { useDemoIdentity } from './demoIdentity';

/**
 * STANDALONE-ONLY persona switch (demo chrome; tree-shaken from the exposed remote
 * build, REQ-I-008 — never imported by `src/remote/`). Restyled (ST.8a) into the
 * app-bar identity slot: a trigger (avatar initials + name + role + chevron) that
 * opens a "Viewing as" persona menu.
 *
 * Picking a persona calls `setPersonaId` — the §16 `resetApiState` trigger in
 * `DemoIdentityProvider` (preserved verbatim; the reset stays there) — and then
 * `navigate('/')`, so the persona-aware `RootRedirect` lands the new identity on
 * its role home (manager → command center, IC → weekly commit) from the
 * server-authoritative `useCurrentUser`, with no client-side role branch here.
 */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
}

/** Labels read "Ivy Chen (IC · draft)" — the display name is the part before " (". */
function displayName(label: string): string {
  return label.split(' (')[0] ?? label;
}

export function PersonaSwitcher() {
  const { personas, personaId, setPersonaId } = useDemoIdentity();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const current = personas.find((p) => p.id === personaId) ?? personas[0];
  const currentName = current ? displayName(current.label) : '';

  const pick = (id: string) => {
    setPersonaId(id);
    setOpen(false);
    navigate('/');
  };

  return (
    <div className="relative">
      <button
        type="button"
        aria-label={`Demo persona — viewing as ${currentName}`}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex items-center gap-2 rounded-md border border-border-strong bg-surface-raised px-2 py-1 text-label text-ink-primary transition-colors duration-base hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
      >
        <span
          aria-hidden
          className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-soft text-meta font-semibold text-brand-ink"
        >
          {current ? initials(currentName) : ''}
        </span>
        <span className="flex flex-col items-start leading-tight">
          <span className="text-label text-ink-primary">{currentName}</span>
          <span className="text-meta text-ink-muted">
            {current?.role === 'MANAGER' ? 'Manager' : 'IC'}
          </span>
        </span>
        <HiSelector aria-hidden className="h-4 w-4 text-ink-muted" />
      </button>

      {open && (
        <>
          <div
            className="fixed inset-0 z-40"
            aria-hidden
            onClick={() => setOpen(false)}
          />
          <div className="absolute right-0 z-50 mt-1 w-56 rounded-md border border-border bg-surface-raised py-1 shadow-pop">
            <div className="px-3 py-1 text-meta uppercase tracking-wide text-ink-muted">
              Viewing as
            </div>
            {personas.map((p) => {
              const name = displayName(p.label);
              const active = p.id === personaId;
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => pick(p.id)}
                  className={`flex w-full items-center gap-2 px-3 py-2 text-label transition-colors duration-base hover:bg-surface-hover ${
                    active ? 'text-ink-primary' : 'text-ink-secondary'
                  }`}
                >
                  <span
                    aria-hidden
                    className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-soft text-meta font-semibold text-brand-ink"
                  >
                    {initials(name)}
                  </span>
                  <span className="flex-1 text-left">{name}</span>
                  <span className="text-meta text-ink-muted">
                    {p.role === 'MANAGER' ? 'Manager' : 'IC'}
                  </span>
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
