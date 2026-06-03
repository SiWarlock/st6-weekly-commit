package com.st6.wc.plan.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.rcdo.RcdoReadService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link PlanMapper} (task 3.3a, Appendix B.5/B.6). Assembles a {@link WeeklyPlanDto}
 * (records, never entities — forbidden-pattern #3) from the plan + its commitments, resolving each
 * linked commitment's RC→DO→SO breadcrumb via {@link RcdoReadService} (mocked here) and the
 * server-authoritative {@code allowedActions[]} via the real {@link AllowedActionResolver}. {@code
 * managerReview} is null while DRAFT (3.5 wires the real review mapping). The nested {@link
 * WeeklyCommitmentDto} intentionally <strong>omits the dispute field</strong> (Q1 — added at the
 * disputes slice via Option-A).
 */
class PlanMapperTest {

  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final PlanMapper mapper = new PlanMapper(rcdoReadService, new AllowedActionResolver());

  private static final UUID OWNER = UUID.randomUUID();

  private static WeeklyPlan draftPlan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(OWNER);
    p.setWeekStartDate(LocalDate.of(2026, 6, 1));
    p.setWeekEndDate(LocalDate.of(2026, 6, 7));
    p.setState(PlanState.DRAFT);
    p.setVersion(0L);
    return p;
  }

  private static WeeklyCommitment commitment(CommitmentKind kind, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(UUID.randomUUID());
    c.setCommitmentKind(kind);
    c.setTitle("title");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(kind == CommitmentKind.UNPLANNED ? WorkType.UNPLANNED : WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    c.setVersion(0L);
    return c;
  }

  // --- maps the B.5 fields, counts, nested commitments; managerReview null while DRAFT ----
  @Test
  void toWeeklyPlanDto_mapsB5Fields_countsAndNullReviewWhileDraft() {
    WeeklyPlan plan = draftPlan();
    WeeklyCommitment planned = commitment(CommitmentKind.PLANNED, null);
    WeeklyCommitment unplanned = commitment(CommitmentKind.UNPLANNED, null);

    WeeklyPlanDto dto =
        mapper.toWeeklyPlanDto(plan, "Ada Lovelace", List.of(planned, unplanned), OWNER);

    assertThat(dto.id()).isEqualTo(plan.getId());
    assertThat(dto.employeeId()).isEqualTo(OWNER);
    assertThat(dto.employeeDisplayName()).isEqualTo("Ada Lovelace");
    assertThat(dto.weekStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(dto.weekEndDate()).isEqualTo(LocalDate.of(2026, 6, 7));
    assertThat(dto.state()).isEqualTo(PlanState.DRAFT);
    assertThat(dto.plannedCount()).isEqualTo(1);
    assertThat(dto.unplannedCount()).isEqualTo(1);
    assertThat(dto.commitments()).hasSize(2);
    assertThat(dto.managerReview())
        .as("null while DRAFT (B.5; 3.5 wires the real review)")
        .isNull();
    assertThat(dto.version()).isZero();
  }

  // --- RED #6: breadcrumb resolved for a linked commitment, null when unlinked ----
  @Test
  void breadcrumb_resolvedWhenLinked_nullWhenUnlinked() {
    UUID soId = UUID.randomUUID();
    RcdoBreadcrumbDto crumb =
        new RcdoBreadcrumbDto(UUID.randomUUID(), "RC", UUID.randomUUID(), "DO", soId, "SO-title");
    when(rcdoReadService.resolveBreadcrumb(soId)).thenReturn(crumb);

    WeeklyCommitmentDto linked =
        mapper.toWeeklyCommitmentDto(commitment(CommitmentKind.PLANNED, soId), OWNER);
    assertThat(linked.supportingOutcomeBreadcrumb()).isEqualTo(crumb);
    assertThat(linked.supportingOutcomeId()).isEqualTo(soId);

    WeeklyCommitmentDto unlinked =
        mapper.toWeeklyCommitmentDto(commitment(CommitmentKind.PLANNED, null), OWNER);
    assertThat(unlinked.supportingOutcomeBreadcrumb()).isNull();
    verify(rcdoReadService, never())
        .resolveBreadcrumb(null); // never resolve for an unlinked commitment
  }

  // --- RED #7: the serialized WeeklyCommitmentDto has NO hasUnresolvedDispute / dispute key (Q1)
  // --
  @Test
  void commitmentDto_omitsDisputeField() throws Exception {
    when(rcdoReadService.resolveBreadcrumb(any())).thenReturn(null);
    WeeklyCommitmentDto dto =
        mapper.toWeeklyCommitmentDto(commitment(CommitmentKind.PLANNED, null), OWNER);

    String json = new ObjectMapper().writeValueAsString(dto);
    assertThat(json).doesNotContain("hasUnresolvedDispute").doesNotContain("dispute");
  }
}
