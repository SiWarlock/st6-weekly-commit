package com.st6.wc.commitment.mapper;

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
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.WorkType;
import com.st6.wc.rcdo.RcdoReadService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link CommitmentMapper} (task 3.4a, Appendix B.6) — the single commitment→DTO
 * mapper reused by {@code PlanMapper} (nested in a plan) and {@code CommitmentController} (a single
 * created commitment). Resolves the RC→DO→SO breadcrumb via {@link RcdoReadService} when linked,
 * null when unlinked; the DTO intentionally <strong>omits the dispute field</strong> (B.6-minus,
 * Option-A at the disputes slice); commitment-level {@code allowedActions} empty in this phase
 * (§15).
 */
class CommitmentMapperTest {

  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final CommitmentMapper mapper = new CommitmentMapper(rcdoReadService);

  private static WeeklyCommitment commitment(UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(UUID.randomUUID());
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("title");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    c.setVersion(0L);
    return c;
  }

  @Test
  void toDto_breadcrumbResolvedWhenLinked_nullWhenUnlinked() {
    UUID soId = UUID.randomUUID();
    RcdoBreadcrumbDto crumb =
        new RcdoBreadcrumbDto(UUID.randomUUID(), "RC", UUID.randomUUID(), "DO", soId, "SO");
    when(rcdoReadService.resolveBreadcrumb(soId)).thenReturn(crumb);

    WeeklyCommitmentDto linked = mapper.toDto(commitment(soId));
    assertThat(linked.supportingOutcomeBreadcrumb()).isEqualTo(crumb);
    assertThat(linked.commitmentKind()).isEqualTo(CommitmentKind.PLANNED);
    assertThat(linked.allowedActions()).isEmpty();

    WeeklyCommitmentDto unlinked = mapper.toDto(commitment(null));
    assertThat(unlinked.supportingOutcomeBreadcrumb()).isNull();
    verify(rcdoReadService, never()).resolveBreadcrumb(null);
  }

  @Test
  void toDto_omitsDisputeField() throws Exception {
    when(rcdoReadService.resolveBreadcrumb(any())).thenReturn(null);
    String json = new ObjectMapper().writeValueAsString(mapper.toDto(commitment(null)));
    assertThat(json).doesNotContain("hasUnresolvedDispute").doesNotContain("dispute");
  }
}
