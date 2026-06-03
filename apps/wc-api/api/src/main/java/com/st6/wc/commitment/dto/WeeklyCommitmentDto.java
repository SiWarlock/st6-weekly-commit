package com.st6.wc.commitment.dto;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.WorkType;
import java.util.List;
import java.util.UUID;

/**
 * A weekly commitment across the API boundary (task 3.3a, Appendix B.6; nested in {@link
 * com.st6.wc.plan.dto.WeeklyPlanDto}). A record, never the JPA entity (forbidden-pattern #3).
 *
 * <p><strong>Transitional subset:</strong> this intentionally <strong>omits the dispute
 * field</strong> B.6 currently lists (`hasUnresolvedDispute`). Building it now is throwaway — the
 * Phase-3 disputes slice lands the user-approved Option-A edit ({@code dispute?:
 * AlignmentDisputeDto}, dropping `hasUnresolvedDispute`) + the B.6 + frontend-mirror reconciliation
 * in one coordinated change. So 3.3a's DTO is B.6-minus-the-dispute-field (documented, not drift).
 * {@code allowedActions} is empty in 3.3a (CARRY_FORWARD/OPEN_DISPUTE/COMMENT are emitted by their
 * enforcing slices — §15).
 */
public record WeeklyCommitmentDto(
    UUID id,
    UUID weeklyPlanId,
    CommitmentKind commitmentKind,
    String title,
    String description,
    UUID supportingOutcomeId,
    RcdoBreadcrumbDto supportingOutcomeBreadcrumb,
    Priority priority,
    WorkType workType,
    Confidence confidence,
    AlignmentStatus alignmentStatus,
    String managerAlignmentNote,
    ReconciliationOutcome reconciliationOutcome,
    String outcomeNote,
    UUID carryForwardSourceCommitmentId,
    List<AllowedAction> allowedActions,
    long version) {

  public WeeklyCommitmentDto {
    allowedActions = List.copyOf(allowedActions);
  }
}
