package com.st6.wc.plan.dto;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.enums.PlanState;
import com.st6.wc.review.dto.ManagerReviewDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A weekly plan across the API boundary (task 3.3a, Appendix B.5) — the E3/E4/E8/E9/E10/E11
 * response shape: identity + Mon–Sun bounds + lifecycle state/timestamps, convenience counts, the
 * nested {@link WeeklyCommitmentDto}[] + {@link ManagerReviewDto} (null while {@code DRAFT}), the
 * server-authoritative {@code allowedActions[]} (B.1/F.4 — UI affordance only, §15), and the
 * optimistic-lock {@code version}. A record, never the JPA entity (forbidden-pattern #3). Lists are
 * defensively copied to immutable at construction.
 */
public record WeeklyPlanDto(
    UUID id,
    UUID employeeId,
    String employeeDisplayName,
    LocalDate weekStartDate,
    LocalDate weekEndDate,
    PlanState state,
    Instant generatedAt,
    Instant lockedAt,
    Instant reconciliationStartedAt,
    Instant reconciledAt,
    int plannedCount,
    int unplannedCount,
    List<WeeklyCommitmentDto> commitments,
    ManagerReviewDto managerReview,
    List<AllowedAction> allowedActions,
    long version) {

  public WeeklyPlanDto {
    commitments = List.copyOf(commitments);
    allowedActions = List.copyOf(allowedActions);
  }
}
