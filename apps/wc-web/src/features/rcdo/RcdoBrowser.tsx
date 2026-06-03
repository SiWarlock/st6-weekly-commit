import { useMemo, useState } from 'react';
import { LoadingState } from '../../shared/components/LoadingState';
import { EmptyState } from '../../shared/components/EmptyState';
import { ErrorState } from '../../shared/components/ErrorState';
import { useGetRcdoQuery, type RcdoTreeDto } from './rcdoApi';

/** Case-insensitive client-side filter: keep only SOs (and their ancestors) whose title matches. */
function filterTree(tree: RcdoTreeDto, query: string): RcdoTreeDto {
  const q = query.trim().toLowerCase();
  if (q === '') {
    return tree;
  }
  return {
    rallyCries: tree.rallyCries
      .map((rc) => ({
        ...rc,
        definingObjectives: rc.definingObjectives
          .map((dobj) => ({
            ...dobj,
            supportingOutcomes: dobj.supportingOutcomes.filter((so) =>
              so.title.toLowerCase().includes(q),
            ),
          }))
          .filter((dobj) => dobj.supportingOutcomes.length > 0),
      }))
      .filter((rc) => rc.definingObjectives.length > 0),
  };
}

/**
 * Browse + client-side search the read-only RC→DO→SO hierarchy (E2). Self-fetches
 * the full tree (B.4 returns it whole for browse/search) and renders the §7
 * view-states: LoadingState while pending, ErrorState (safe message) on failure,
 * EmptyState for an empty tree. The Supporting-Outcome SELECTION lives in the
 * controlled SupportingOutcomePicker; this is the read-only explorer.
 */
export function RcdoBrowser() {
  const { data, isLoading, isError, error } = useGetRcdoQuery();
  const [query, setQuery] = useState('');

  const filtered = useMemo(
    () => (data ? filterTree(data, query) : undefined),
    [data, query],
  );

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
  if (data.rallyCries.length === 0) {
    return (
      <EmptyState
        title="No outcomes"
        message="The supporting-outcome catalog has not been seeded yet."
      />
    );
  }

  const tree = filtered ?? data;
  return (
    <div data-cy="rcdo-browser" className="space-y-4">
      <input
        type="search"
        aria-label="Search outcomes"
        placeholder="Search outcomes…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="w-full rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      />
      <ul className="space-y-3">
        {tree.rallyCries.map((rc) => (
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
                      <li key={so.id} className="text-body text-ink-primary">
                        {so.title}
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
