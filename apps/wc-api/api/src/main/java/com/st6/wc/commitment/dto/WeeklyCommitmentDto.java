package com.st6.wc.commitment.dto;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
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
 * <p>{@code dispute} nests the commitment's current <strong>unresolved</strong> dispute ({@code
 * OPEN}/{@code IC_RESPONDED}) as an {@link AlignmentDisputeDto}, else {@code null} (task 5.3b — the
 * user-approved B.6 Option-A realization; the redundant {@code hasUnresolvedDispute} boolean it
 * replaced was never built, so the nested object's presence + status is the single source of
 * truth). The dispute is an aggregate child (≤1 unresolved per commitment, rule #6) exposed through
 * the commitment root; it rides the commitment's existing E3/E4 read authz (no new surface). {@code
 * allowedActions} is empty in 3.3a (CARRY_FORWARD/OPEN_DISPUTE/COMMENT are emitted by their
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
    AlignmentDisputeDto dispute,
    List<AllowedAction> allowedActions,
    long version) {

  public WeeklyCommitmentDto {
    allowedActions = List.copyOf(allowedActions);
  }
}
