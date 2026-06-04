package com.st6.wc.manager.query;

import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.WorkType;
import java.util.UUID;

/**
 * Command-center filter criteria — optional predicates AND-combined over the manager-scoped query;
 * any {@code null} field = not filtered. {@code reviewState} is pre-translated by the service
 * ({@code OVERDUE} → {@code overdue=true}; the three stored statuses → {@code reviewStatus}). The
 * first four are summary-level column-equals over {@code manager_plan_summary} (task 6.5a). The
 * last four are the 6.5a-2 cross-table EXISTS filters: {@code definingObjectiveId} over {@code
 * manager_heatmap_cell} (the DO grain), and {@code priority}/{@code workType}/{@code
 * alignmentStatus} over {@code weekly_commitment} (the §4-indexed commitment columns). An internal
 * carrier — not a boundary DTO.
 */
public record CommandCenterFilters(
    UUID employeeId,
    PlanState planState,
    ReviewStatus reviewStatus,
    Boolean overdue,
    UUID definingObjectiveId,
    Priority priority,
    WorkType workType,
    AlignmentStatus alignmentStatus) {}
