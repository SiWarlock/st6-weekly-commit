import { useDemoIdentity } from './demoIdentity';

/**
 * STANDALONE-ONLY persona switch (demo chrome). Tree-shaken out of the exposed
 * remote build (REQ-I-008). Changing the persona updates the demo employee id
 * the 9.1 seam yields, which `prepareHeaders` attaches as `X-Demo-Employee-Id`.
 */
export function PersonaSwitcher() {
  const { personas, personaId, setPersonaId } = useDemoIdentity();

  return (
    <label className="inline-flex items-center gap-2 text-label text-ink-secondary">
      <span className="uppercase tracking-wide">Persona</span>
      <select
        aria-label="Persona"
        value={personaId}
        onChange={(event) => setPersonaId(event.target.value)}
        className="rounded-md border border-border-strong bg-surface-raised px-2 py-1 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      >
        {personas.map((persona) => (
          <option key={persona.id} value={persona.id}>
            {persona.label}
          </option>
        ))}
      </select>
    </label>
  );
}
