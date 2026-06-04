package com.st6.wc.manager.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.projection.ManagerPlanSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ManagerProjectionMapper} unit proof (task 6.5a) — the {@code (projection, displayName)}
 * query row maps to the B.11 {@link ManagerCommandCenterRowDto} field-for-field, with the joined
 * display name and WITHOUT the projection-row {@code id} (B.11 carries no id — leak guard mirrored
 * in the endpoint test).
 */
class ManagerProjectionMapperTest {

  private final ManagerProjectionMapper mapper = new ManagerProjectionMapper();

  @Test
  void toRowDto_mapsAllB11FieldsFromSummaryPlusJoinedName() {
    UUID mgr = UUID.randomUUID();
    UUID emp = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    Instant due = Instant.parse("2026-06-04T22:00:00Z");
    Instant updated = Instant.parse("2026-06-03T12:00:00Z");
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID()); // internal id — must NOT appear on the DTO
    s.setManagerEmployeeId(mgr);
    s.setEmployeeId(emp);
    s.setWeeklyPlanId(plan);
    s.setWeekStartDate(LocalDate.of(2026, 6, 1));
    s.setPlanState(PlanState.RECONCILING);
    s.setReviewStatus(ReviewStatus.REVIEWED_WITH_DISPUTES);
    s.setReviewDueAt(due);
    s.setReviewOverdue(false);
    s.setPlannedCount(3);
    s.setUnplannedCount(1);
    s.setMisalignedCount(2);
    s.setNeedsReviewCount(1);
    s.setBlockedCount(1);
    s.setCarryForwardCount(1);
    s.setUnresolvedDisputeCount(2);
    s.setUpdatedAt(updated);

    ManagerCommandCenterRowDto dto = mapper.toRowDto(s, "Alice Adams");

    assertThat(dto.managerEmployeeId()).isEqualTo(mgr);
    assertThat(dto.employeeId()).isEqualTo(emp);
    assertThat(dto.employeeDisplayName()).isEqualTo("Alice Adams"); // the joined name
    assertThat(dto.weeklyPlanId()).isEqualTo(plan);
    assertThat(dto.weekStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(dto.planState()).isEqualTo(PlanState.RECONCILING);
    assertThat(dto.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
    assertThat(dto.reviewDueAt()).isEqualTo(due);
    assertThat(dto.isReviewOverdue()).isFalse();
    assertThat(dto.plannedCount()).isEqualTo(3);
    assertThat(dto.unplannedCount()).isEqualTo(1);
    assertThat(dto.misalignedCount()).isEqualTo(2);
    assertThat(dto.needsReviewCount()).isEqualTo(1);
    assertThat(dto.blockedCount()).isEqualTo(1);
    assertThat(dto.carryForwardCount()).isEqualTo(1);
    assertThat(dto.unresolvedDisputeCount()).isEqualTo(2);
    assertThat(dto.updatedAt()).isEqualTo(updated);
  }

  @Test
  void toRowDto_nullableReviewFields_passThroughNull() {
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID());
    s.setManagerEmployeeId(UUID.randomUUID());
    s.setEmployeeId(UUID.randomUUID());
    s.setWeeklyPlanId(UUID.randomUUID());
    s.setWeekStartDate(LocalDate.of(2026, 6, 1));
    s.setPlanState(PlanState.DRAFT);
    s.setReviewStatus(null); // null while DRAFT (B.11 nullable)
    s.setReviewDueAt(null);
    s.setReviewOverdue(false);
    s.setUpdatedAt(Instant.parse("2026-06-03T12:00:00Z"));

    ManagerCommandCenterRowDto dto = mapper.toRowDto(s, "Bob");

    assertThat(dto.reviewStatus()).isNull();
    assertThat(dto.reviewDueAt()).isNull();
    assertThat(dto.planState()).isEqualTo(PlanState.DRAFT);
  }
}
