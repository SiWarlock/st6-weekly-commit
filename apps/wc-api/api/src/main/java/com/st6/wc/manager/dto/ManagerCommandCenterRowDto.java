package com.st6.wc.manager.dto;

import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * B.11 — the E13 command-center row, mirroring {@code manager_plan_summary} (§9) field-for-field. A
 * {@code record} across the API boundary, <strong>never</strong> the entity (no projection-row
 * {@code id} / audit fields leak — forbidden-pattern #3, leak-tested). {@code weeklyPlanId} /
 * {@code reviewStatus} / {@code reviewDueAt} are nullable per B.11; {@code isReviewOverdue} mirrors
 * the §9 projection column (false while {@code REVIEWED_WITH_DISPUTES}).
 */
public record ManagerCommandCenterRowDto(
    UUID managerEmployeeId,
    UUID employeeId,
    String employeeDisplayName,
    UUID weeklyPlanId,
    LocalDate weekStartDate,
    PlanState planState,
    ReviewStatus reviewStatus,
    Instant reviewDueAt,
    boolean isReviewOverdue,
    int plannedCount,
    int unplannedCount,
    int misalignedCount,
    int needsReviewCount,
    int blockedCount,
    int carryForwardCount,
    int unresolvedDisputeCount,
    Instant updatedAt) {}
