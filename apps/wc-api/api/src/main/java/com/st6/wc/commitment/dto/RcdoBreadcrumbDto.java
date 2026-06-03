package com.st6.wc.commitment.dto;

import java.util.UUID;

/**
 * The RC→DO→SO label trail for a linked commitment's Supporting Outcome (task 3.3a, Appendix B.6 /
 * §5) — display breadcrumb only. Resolved by {@code RcdoReadService.resolveBreadcrumb}; null on a
 * {@link WeeklyCommitmentDto} when {@code supportingOutcomeId} is null. A record, never an entity.
 */
public record RcdoBreadcrumbDto(
    UUID rallyCryId,
    String rallyCryTitle,
    UUID definingObjectiveId,
    String definingObjectiveTitle,
    UUID supportingOutcomeId,
    String supportingOutcomeTitle) {}
