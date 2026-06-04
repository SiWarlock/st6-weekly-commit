package com.st6.wc.commitment.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.rcdo.RcdoReadService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof of {@link CommitmentMapper} (task 3.4a, Appendix B.6) — the single commitment→DTO
 * mapper reused by {@code PlanMapper} (nested in a plan) and {@code CommitmentController} (a single
 * created commitment). Resolves the RC→DO→SO breadcrumb via {@link RcdoReadService} when linked,
 * null when unlinked; nests the commitment's current {@code OPEN}/{@code IC_RESPONDED} dispute via
 * {@link DisputeMapper} (B.6 Option-A, task 5.3b — else null); commitment-level {@code
 * allowedActions} empty in this phase (§15).
 */
class CommitmentMapperTest {

  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final CommitmentMapper mapper =
      new CommitmentMapper(
          rcdoReadService, new AllowedActionResolver(), disputes, new DisputeMapper());

  private static WeeklyPlan reconcilingPlan(UUID owner) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(owner);
    p.setWeekStartDate(LocalDate.of(2026, 6, 1));
    p.setWeekEndDate(LocalDate.of(2026, 6, 7));
    p.setState(PlanState.RECONCILING);
    return p;
  }

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

  private static AlignmentDispute dispute(UUID commitmentId, DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(UUID.randomUUID());
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("please re-scope");
    d.setVersion(0L);
    return d;
  }

  // --- 5.3b: an OPEN dispute on the commitment is nested via DisputeMapper (B.6 Option-A) ----
  @Test
  void toDto_commitmentWithOpenDispute_nestsDisputeDto() {
    when(rcdoReadService.resolveBreadcrumb(any())).thenReturn(null);
    WeeklyCommitment c = commitment(null);
    AlignmentDispute open = dispute(c.getId(), DisputeStatus.OPEN);
    when(disputes.findByCommitmentIdAndStatusIn(eq(c.getId()), any()))
        .thenReturn(Optional.of(open));

    WeeklyCommitmentDto dto = mapper.toDto(c);

    assertThat(dto.dispute()).isNotNull();
    assertThat(dto.dispute().id()).isEqualTo(open.getId());
    assertThat(dto.dispute().status()).isEqualTo(DisputeStatus.OPEN);
    assertThat(dto.dispute().flagType()).isEqualTo(FlagType.MISALIGNED);
    assertThat(dto.dispute().managerNote()).isEqualTo("please re-scope");
  }

  // --- 5.3b: an IC_RESPONDED dispute is still unresolved → also nested ({OPEN,IC_RESPONDED}) ----
  @Test
  void toDto_commitmentWithIcRespondedDispute_nestsDisputeDto() {
    when(rcdoReadService.resolveBreadcrumb(any())).thenReturn(null);
    WeeklyCommitment c = commitment(null);
    when(disputes.findByCommitmentIdAndStatusIn(eq(c.getId()), any()))
        .thenReturn(Optional.of(dispute(c.getId(), DisputeStatus.IC_RESPONDED)));

    WeeklyCommitmentDto dto = mapper.toDto(c);

    assertThat(dto.dispute()).isNotNull();
    assertThat(dto.dispute().status()).isEqualTo(DisputeStatus.IC_RESPONDED);
  }

  // --- 5.3b: no unresolved dispute (the finder's empty branch — incl. resolved-only) → null ----
  @Test
  void toDto_commitmentWithNoUnresolvedDispute_disputeNull() {
    when(rcdoReadService.resolveBreadcrumb(any())).thenReturn(null);
    WeeklyCommitment c = commitment(null);
    when(disputes.findByCommitmentIdAndStatusIn(eq(c.getId()), any())).thenReturn(Optional.empty());

    assertThat(mapper.toDto(c).dispute()).isNull();
  }

  // --- 5.3b: the lookup queries ONLY the unresolved bucket {OPEN, IC_RESPONDED} (RESOLVED
  // excluded)
  @Test
  void toDto_queriesUnresolvedBucketOnly() {
    when(rcdoReadService.resolveBreadcrumb(any())).thenReturn(null);
    WeeklyCommitment c = commitment(null);
    when(disputes.findByCommitmentIdAndStatusIn(eq(c.getId()), any())).thenReturn(Optional.empty());

    mapper.toDto(c);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<DisputeStatus>> bucket = ArgumentCaptor.forClass(Collection.class);
    verify(disputes).findByCommitmentIdAndStatusIn(eq(c.getId()), bucket.capture());
    assertThat(bucket.getValue())
        .containsExactlyInAnyOrder(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED);
  }

  // --- 4.4b: the context-aware overload fills allowedActions via the resolver (eligible
  // commitment)
  @Test
  void toDto_withContext_eligibleEmitsCarryForward() {
    UUID owner = UUID.randomUUID();
    WeeklyPlan plan = reconcilingPlan(owner);
    when(disputes.findByCommitmentIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

    WeeklyCommitmentDto dto = mapper.toDto(commitment(null), plan, owner, false);

    assertThat(dto.allowedActions()).contains(AllowedAction.CARRY_FORWARD);
  }

  // --- 4.4b: a non-owner reader (manager-direct-report) sees no CARRY_FORWARD via the overload
  // ----
  @Test
  void toDto_withContext_nonOwner_noCarryForward() {
    WeeklyPlan plan = reconcilingPlan(UUID.randomUUID());
    when(disputes.findByCommitmentIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

    WeeklyCommitmentDto dto = mapper.toDto(commitment(null), plan, UUID.randomUUID(), true);

    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.CARRY_FORWARD);
  }

  private static WeeklyPlan lockedPlan(UUID owner) {
    WeeklyPlan p = reconcilingPlan(owner);
    p.setState(PlanState.LOCKED);
    return p;
  }

  // --- 5.5b: a manager viewing a LOCKED commitment with NO unresolved dispute → OPEN_DISPUTE on
  // the
  // commitment (the viewerIsDirectManager=true path threads through commitmentActions) ----
  @Test
  void toDto_withContext_managerViewer_undisputedLocked_emitsOpenDispute() {
    UUID owner = UUID.randomUUID();
    WeeklyPlan plan = lockedPlan(owner);
    when(disputes.findByCommitmentIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

    WeeklyCommitmentDto dto = mapper.toDto(commitment(null), plan, UUID.randomUUID(), true);

    assertThat(dto.allowedActions()).contains(AllowedAction.OPEN_DISPUTE);
    assertThat(dto.dispute()).isNull();
  }

  // --- 5.5b: a manager viewing a commitment WITH an OPEN dispute → no OPEN_DISPUTE on the
  // commitment (rule #6), but RESOLVE_DISPUTE on the nested dispute (viewerIsDirectManager threaded
  // into DisputeMapper) ----
  @Test
  void toDto_withContext_managerViewer_disputed_nestsResolveAndHidesOpen() {
    UUID owner = UUID.randomUUID();
    WeeklyPlan plan = lockedPlan(owner);
    WeeklyCommitment c = commitment(null);
    when(disputes.findByCommitmentIdAndStatusIn(eq(c.getId()), any()))
        .thenReturn(Optional.of(dispute(c.getId(), DisputeStatus.OPEN)));

    WeeklyCommitmentDto dto = mapper.toDto(c, plan, UUID.randomUUID(), true);

    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.OPEN_DISPUTE);
    assertThat(dto.dispute().allowedActions()).contains(AllowedAction.RESOLVE_DISPUTE);
  }

  // --- 5.5b: the owning IC viewing their own commitment's OPEN dispute → RESPOND_DISPUTE nested,
  // no
  // manager affordances ----
  @Test
  void toDto_withContext_ownerViewer_openDispute_nestsRespond() {
    UUID owner = UUID.randomUUID();
    WeeklyPlan plan = lockedPlan(owner);
    WeeklyCommitment c = commitment(null);
    when(disputes.findByCommitmentIdAndStatusIn(eq(c.getId()), any()))
        .thenReturn(Optional.of(dispute(c.getId(), DisputeStatus.OPEN)));

    WeeklyCommitmentDto dto = mapper.toDto(c, plan, owner, false);

    assertThat(dto.dispute().allowedActions()).containsExactly(AllowedAction.RESPOND_DISPUTE);
    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.OPEN_DISPUTE);
  }
}
