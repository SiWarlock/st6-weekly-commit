package com.st6.wc.manager.query;

import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import java.util.UUID;

/**
 * Summary-level command-center filter criteria (task 6.5a) — optional predicates AND-combined over
 * {@code manager_plan_summary}; any {@code null} field = not filtered. {@code reviewState} is
 * pre-translated by the service ({@code OVERDUE} → {@code overdue=true}; the three stored statuses
 * → {@code reviewStatus}). The cross-table filters (DO / priority / workType / alignmentStatus) are
 * 6.5a-2 — they slot in here as additional fields without changing this contract.
 */
public record CommandCenterFilters(
    UUID employeeId, PlanState planState, ReviewStatus reviewStatus, Boolean overdue) {}
