import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { useGetRcdoQuery, type RcdoTreeDto } from './rcdoApi';

export interface SupportingOutcomePickerProps {
  /** The currently selected Supporting Outcome id (controlled), or null. */
  value: string | null;
  /** Emits the chosen `supportingOutcomeId` (consumed by 9.6's commitment form). */
  onChange: (supportingOutcomeId: string) => void;
}

interface OutcomePath {
  rcTitle: string;
  doTitle: string;
  soTitle: string;
}

/** Resolve a Supporting Outcome's RC → DO → SO path for the breadcrumb. */
function findPath(tree: RcdoTreeDto, soId: string): OutcomePath | null {
  for (const rc of tree.rallyCries) {
    for (const dobj of rc.definingObjectives) {
      for (const so of dobj.supportingOutcomes) {
        if (so.id === soId) {
          return { rcTitle: rc.title, doTitle: dobj.title, soTitle: so.title };
        }
      }
    }
  }
  return null;
}

/**
 * Controlled Supporting-Outcome selector (REQ-UX-001). Selecting exactly one
 * outcome emits its `supportingOutcomeId` via `onChange`; the rendered selection
 * (the RC→DO→SO breadcrumb + the pressed option) is driven solely by the `value`
 * prop — no internal source of truth — so 9.6's commitment form owns the value
 * for submit/validation. Self-fetches the read-only tree (E2).
 */
export function SupportingOutcomePicker({
  value,
  onChange,
}: SupportingOutcomePickerProps) {
  const { data, isLoading, isError, error } = useGetRcdoQuery();

  // Check error before the loading/!data guard: a failed query has no `data`.
  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'Could not load outcomes.';
    return <ErrorState message={message} />;
  }
  if (isLoading || !data) {
    return <LoadingState variant="cards" delayMs={0} />;
  }

  const selected = value ? findPath(data, value) : null;

  return (
    <div data-cy="so-picker" className="space-y-4">
      {selected && (
        <nav
          data-cy="so-breadcrumb"
          aria-label="Selected supporting outcome"
          className="flex flex-wrap items-center gap-1 text-meta text-ink-secondary"
        >
          <span>{selected.rcTitle}</span>
          <span aria-hidden>›</span>
          <span>{selected.doTitle}</span>
          <span aria-hidden>›</span>
          <span className="font-semibold text-ink-primary">
            {selected.soTitle}
          </span>
        </nav>
      )}
      <ul className="space-y-3">
        {data.rallyCries.map((rc) => (
          <li key={rc.id}>
            <span className="text-label font-semibold text-ink-primary">
              {rc.title}
            </span>
            <ul className="ml-4 mt-1 space-y-2">
              {rc.definingObjectives.map((dobj) => (
                <li key={dobj.id}>
                  <span className="text-body text-ink-secondary">
                    {dobj.title}
                  </span>
                  <ul className="ml-4 mt-1 space-y-1">
                    {dobj.supportingOutcomes.map((so) => (
                      <li key={so.id}>
                        <button
                          type="button"
                          aria-pressed={so.id === value}
                          onClick={() => onChange(so.id)}
                          className={`rounded-md px-2 py-1 text-body ${
                            so.id === value
                              ? 'bg-surface-hover font-semibold text-ink-primary'
                              : 'text-ink-secondary hover:bg-surface-hover'
                          }`}
                        >
                          {so.title}
                        </button>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}
