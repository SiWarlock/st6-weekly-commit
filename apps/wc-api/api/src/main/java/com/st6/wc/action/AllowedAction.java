package com.st6.wc.action;

/**
 * The computed DTO-layer affordance vocabulary (Appendix B.1 / F.4) — the values that populate the
 * server-authoritative {@code allowedActions[]} on the plan/commitment/review response DTOs, each
 * mapping 1:1 to the endpoint the UI invokes (F.4). <strong>UI affordance only — never the
 * authorization source</strong> (§15): the server recomputes eligibility on every mutation; an
 * action present here only means "offer the button," not "authorized."
 *
 * <p>Deliberately <strong>not</strong> a {@code shared/enums/} member — it is a derived response
 * field, not a persisted/{@code VARCHAR}+{@code CHECK} column, so it is excluded from the 16-enum
 * {@code EnumVocabularyTest} pin (it lives in the {@code :api} DTO layer, Phase 3). A neutral
 * package because it is cross-cutting across the plan, commitment, and review DTOs.
 */
public enum AllowedAction {
  LOCK,
  START_RECONCILIATION,
  CLOSE_RECONCILIATION,
  ADD_UNPLANNED,
  CARRY_FORWARD,
  MARK_REVIEWED,
  OPEN_DISPUTE,
  RESPOND_DISPUTE,
  RESOLVE_DISPUTE,
  COMMENT,
  RETRY_SYNC
}
