/**
 * data-cy selector vocabulary — the SINGLE source of selector truth for the BDD suite.
 *
 * Convention (ARCHITECTURE Appendix C.5 is silent on this — documented here, candidate for a
 * one-line C.5 addendum): every UI element the e2e suite targets exposes `data-cy="<kebab-case>"`.
 * Step definitions import `SEL` / `cySel` and NEVER inline raw selector strings, so the binding
 * the frontend must honor lives in exactly one place.
 *
 * RECONCILE (Carry-forward, origin 11.7): align these keys 1:1 with wc-web's real `data-cy`
 * attributes once the frontend is runnable. Until then this is the proposed contract, not a
 * verified one (authored-not-green slice).
 */
export const SEL = {
  // Plan + lock (ic_plan)
  planView: 'plan-view',
  planState: 'plan-state',
  commitmentRow: 'commitment-row',
  lockButton: 'lock-button',
  errorBanner: 'error-banner',
  errorCode: 'error-code',
  safeMessage: 'safe-message',
  reviewDueDate: 'review-due-date',
  baselineEdit: 'baseline-edit',
  supportingOutcomePicker: 'supporting-outcome-picker',
  supportingOutcomeOption: 'supporting-outcome-option',

  // Reconcile + carry-forward (reconcile)
  reconcileOutcome: 'reconcile-outcome',
  unplannedAdd: 'unplanned-add',
  unplannedBadge: 'unplanned-badge',
  supportingOutcomeLink: 'supporting-outcome-link',
  carryForwardButton: 'carry-forward-button',
  carryForwardSourceLink: 'carry-forward-source-link',
  baselinePanel: 'baseline-panel',

  // Dispute loop (dispute)
  disputePanel: 'dispute-panel',
  disputeState: 'dispute-state',
  disputeRespondSo: 'dispute-respond-so',
  disputeResolve: 'dispute-resolve',
  reviewState: 'review-state',

  // Manager command center + heatmap (heatmap)
  commandCenter: 'command-center',
  directReportRow: 'direct-report-row',
  heatmap: 'heatmap',
  heatmapCell: 'heatmap-cell',
  riskBadge: 'risk-badge',
  drilldownSoBreakdown: 'drilldown-so-breakdown',

  // Outlook sync (sync)
  syncWarning: 'sync-warning',
  syncRetryButton: 'sync-retry-button',
  lifecycleStatus: 'lifecycle-status',

  // Authorization (authz)
  personaSwitcher: 'persona-switcher',
  accessDenied: 'access-denied',
} as const;

export type SelKey = keyof typeof SEL;

/** Build a `[data-cy="…"]` attribute selector from a vocabulary key. */
export const cySel = (key: SelKey): string => `[data-cy="${SEL[key]}"]`;
