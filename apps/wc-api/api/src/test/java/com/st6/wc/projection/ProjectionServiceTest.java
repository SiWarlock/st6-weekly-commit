package com.st6.wc.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RiskBadge;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.review.ManagerReview;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code ProjectionService} unit proof (task 3.5 base + 6.2 §9 derivation completion). Synchronous
 * recompute of the {@code manager_plan_summary} (one row, plan grain) + {@code
 * manager_heatmap_cell} (one row per Defining Objective with ≥1 linked commitment) from source.
 * Pins the §9 count predicates: misaligned = {@code alignment_status=MISALIGNED}
 * <strong>OR</strong> an {@code OPEN}/{@code IC_RESPONDED} dispute with {@code
 * flag_type=MISALIGNED} (deduped per commitment); {@code unresolvedDisputeCount} = commitments with
 * an unresolved dispute; <strong>blocked = {@code reconciliation_outcome=BLOCKED}</strong> (6.2
 * correction from the latent {@code work_type=BLOCKER}); needsReview = {@code NEEDS_REVIEW};
 * carry-forward = a non-null successor link (unchanged, §30). The {@code MISALIGNED} risk-badge
 * fires from the same union. Repos + RcdoReadService + the dispute repo mocked.
 */
class ProjectionServiceTest {

  private final ManagerPlanSummaryRepository summaries = mock(ManagerPlanSummaryRepository.class);
  private final ManagerHeatmapCellRepository cells = mock(ManagerHeatmapCellRepository.class);
  private final RcdoReadService rcdo = mock(RcdoReadService.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC);
  private final ProjectionService service =
      new ProjectionService(summaries, cells, rcdo, clock, disputes);

  private static final UUID MGR = UUID.randomUUID();
  private static final UUID IC = UUID.randomUUID();
  private static final UUID PLAN = UUID.randomUUID();
  private static final UUID DO1 = UUID.randomUUID();
  private static final UUID DO2 = UUID.randomUUID();
  private static final UUID SO1 = UUID.randomUUID();
  private static final UUID SO2 = UUID.randomUUID();
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  private static SupportingOutcome so(UUID id, UUID doId) {
    SupportingOutcome s = new SupportingOutcome();
    s.setId(id);
    s.setDefiningObjectiveId(doId);
    return s;
  }

  private static WeeklyCommitment commitment(
      UUID so, AlignmentStatus align, WorkType work, ReconciliationOutcome outcome) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(PLAN);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setSupportingOutcomeId(so);
    c.setAlignmentStatus(align);
    c.setWorkType(work);
    c.setReconciliationOutcome(outcome);
    c.setPriority(Priority.P1);
    c.setConfidence(Confidence.MEDIUM);
    return c;
  }

  private static AlignmentDispute dispute(UUID commitmentId, DisputeStatus status, FlagType flag) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(MGR);
    d.setStatus(status);
    d.setFlagType(flag);
    d.setManagerNote("n");
    return d;
  }

  private static WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN);
    p.setEmployeeId(IC);
    p.setWeekStartDate(WEEK);
    p.setState(state);
    return p;
  }

  private static ManagerReview review() {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(PLAN);
    r.setManagerEmployeeId(MGR);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-08T22:00:00Z")); // future vs the fixed clock
    return r;
  }

  private static ManagerHeatmapCell existingCell(UUID definingObjectiveId) {
    ManagerHeatmapCell cell = new ManagerHeatmapCell();
    cell.setId(UUID.randomUUID());
    cell.setManagerEmployeeId(MGR);
    cell.setEmployeeId(IC);
    cell.setWeekStartDate(WEEK);
    cell.setDefiningObjectiveId(definingObjectiveId);
    return cell;
  }

  private void stubEmptyProjectionLookups() {
    when(summaries.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(MGR, IC, WEEK))
        .thenReturn(Optional.empty());
    when(cells.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(MGR, IC, WEEK))
        .thenReturn(List.of());
  }

  private ManagerPlanSummary captureSummary() {
    ArgumentCaptor<ManagerPlanSummary> sum = ArgumentCaptor.forClass(ManagerPlanSummary.class);
    verify(summaries).save(sum.capture());
    return sum.getValue();
  }

  private List<ManagerHeatmapCell> captureCells(int times) {
    ArgumentCaptor<ManagerHeatmapCell> cell = ArgumentCaptor.forClass(ManagerHeatmapCell.class);
    verify(cells, org.mockito.Mockito.times(times)).save(cell.capture());
    return cell.getAllValues();
  }

  // --- summary counts + per-DO heatmap cells from source; BLOCKED now via reconciliation_outcome
  // --
  @Test
  void recompute_derivesSummaryAndHeatmapCells() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    when(rcdo.findSupportingOutcome(SO2)).thenReturn(so(SO2, DO2));

    List<WeeklyCommitment> commitments =
        List.of(
            commitment(SO1, AlignmentStatus.NEEDS_REVIEW, WorkType.STRATEGIC, null), // DO1
            commitment(
                SO1,
                AlignmentStatus.MISALIGNED,
                WorkType.STRATEGIC,
                ReconciliationOutcome.BLOCKED), // DO1 — blocked via the corrected source
            commitment(SO2, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null)); // DO2

    service.recompute(plan(PlanState.RECONCILING), MGR, commitments, review());

    ManagerPlanSummary s = captureSummary();
    assertThat(s.getManagerEmployeeId()).isEqualTo(MGR);
    assertThat(s.getEmployeeId()).isEqualTo(IC);
    assertThat(s.getPlannedCount()).isEqualTo(3);
    assertThat(s.getMisalignedCount()).isEqualTo(1);
    assertThat(s.getNeedsReviewCount()).isEqualTo(1);
    assertThat(s.getBlockedCount()).isEqualTo(1); // from reconciliation_outcome=BLOCKED
    assertThat(s.getCarryForwardCount()).isZero();
    assertThat(s.getUnresolvedDisputeCount()).isZero();

    List<ManagerHeatmapCell> saved = captureCells(2);
    ManagerHeatmapCell do1 =
        saved.stream()
            .filter(x -> x.getDefiningObjectiveId().equals(DO1))
            .findFirst()
            .orElseThrow();
    assertThat(do1.getCommitmentCount()).isEqualTo(2);
    assertThat(do1.getMisalignedCount()).isEqualTo(1);
    assertThat(do1.getBlockedCount()).isEqualTo(1);
    assertThat(do1.getRiskBadges())
        .containsExactlyInAnyOrder(
            RiskBadge.MISALIGNED, RiskBadge.NEEDS_REVIEW, RiskBadge.BLOCKED, RiskBadge.UNREVIEWED);
    ManagerHeatmapCell do2 =
        saved.stream()
            .filter(x -> x.getDefiningObjectiveId().equals(DO2))
            .findFirst()
            .orElseThrow();
    assertThat(do2.getMisalignedCount()).isZero();
    assertThat(do2.getRiskBadges()).containsExactly(RiskBadge.UNREVIEWED);
  }

  // --- §9 union half: an ALIGNED commitment with an OPEN MISALIGNED dispute counts as misaligned
  // --
  @Test
  void recompute_misalignedCount_unionsOpenMisalignedDispute() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    WeeklyCommitment c = commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null);
    when(disputes.findByCommitmentIdInAndStatusIn(any(), any()))
        .thenReturn(List.of(dispute(c.getId(), DisputeStatus.OPEN, FlagType.MISALIGNED)));

    service.recompute(plan(PlanState.LOCKED), MGR, List.of(c), review());

    assertThat(captureSummary().getMisalignedCount()).isEqualTo(1);
    ManagerHeatmapCell cell = captureCells(1).get(0);
    assertThat(cell.getMisalignedCount()).isEqualTo(1);
    assertThat(cell.getRiskBadges()).contains(RiskBadge.MISALIGNED);
  }

  // --- §9 dedup: a commitment that is BOTH alignment=MISALIGNED AND disputed counts ONCE ----
  @Test
  void recompute_misalignedCount_dedupesAlignmentAndDispute() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    WeeklyCommitment c = commitment(SO1, AlignmentStatus.MISALIGNED, WorkType.STRATEGIC, null);
    when(disputes.findByCommitmentIdInAndStatusIn(any(), any()))
        .thenReturn(List.of(dispute(c.getId(), DisputeStatus.OPEN, FlagType.MISALIGNED)));

    service.recompute(plan(PlanState.LOCKED), MGR, List.of(c), review());

    assertThat(captureSummary().getMisalignedCount()).isEqualTo(1); // not 2
  }

  // --- §9 unresolvedDisputeCount: OPEN + IC_RESPONDED both count; RESOLVED never reaches here ----
  @Test
  void recompute_unresolvedDisputeCount_countsOpenAndIcResponded() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    WeeklyCommitment c1 = commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null);
    WeeklyCommitment c2 = commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null);
    when(disputes.findByCommitmentIdInAndStatusIn(any(), any()))
        .thenReturn(
            List.of(
                dispute(c1.getId(), DisputeStatus.OPEN, FlagType.NEEDS_REVISION),
                dispute(c2.getId(), DisputeStatus.IC_RESPONDED, FlagType.NEEDS_REVISION)));

    service.recompute(plan(PlanState.LOCKED), MGR, List.of(c1, c2), review());

    assertThat(captureSummary().getUnresolvedDisputeCount()).isEqualTo(2);
    assertThat(captureCells(1).get(0).getUnresolvedDisputeCount()).isEqualTo(2);
  }

  // --- a NEEDS_REVISION dispute bumps unresolvedDisputeCount but NOT misalignedCount/the badge
  // ----
  @Test
  void recompute_needsRevisionDispute_doesNotCountMisaligned() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    WeeklyCommitment c = commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null);
    when(disputes.findByCommitmentIdInAndStatusIn(any(), any()))
        .thenReturn(List.of(dispute(c.getId(), DisputeStatus.OPEN, FlagType.NEEDS_REVISION)));

    service.recompute(plan(PlanState.LOCKED), MGR, List.of(c), review());

    ManagerPlanSummary s = captureSummary();
    assertThat(s.getUnresolvedDisputeCount()).isEqualTo(1);
    assertThat(s.getMisalignedCount()).isZero();
    assertThat(captureCells(1).get(0).getRiskBadges()).doesNotContain(RiskBadge.MISALIGNED);
  }

  // --- §17 R5 regression: blocked is reconciliation_outcome=BLOCKED, NOT work_type=BLOCKER ----
  @Test
  void recompute_blockedCount_fromReconciliationOutcome() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    when(rcdo.findSupportingOutcome(SO2)).thenReturn(so(SO2, DO2));
    // DO1: a reconciliation_outcome=BLOCKED commitment → blocked; DO2: a work_type=BLOCKER
    // commitment with NO blocked outcome → NOT blocked (the corrected source)
    WeeklyCommitment blocked =
        commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, ReconciliationOutcome.BLOCKED);
    WeeklyCommitment blockerWorkType =
        commitment(SO2, AlignmentStatus.ALIGNED, WorkType.BLOCKER, null);

    service.recompute(
        plan(PlanState.RECONCILING), MGR, List.of(blocked, blockerWorkType), review());

    assertThat(captureSummary().getBlockedCount()).isEqualTo(1);
    List<ManagerHeatmapCell> saved = captureCells(2);
    ManagerHeatmapCell do1 =
        saved.stream()
            .filter(x -> x.getDefiningObjectiveId().equals(DO1))
            .findFirst()
            .orElseThrow();
    assertThat(do1.getBlockedCount()).isEqualTo(1);
    assertThat(do1.getRiskBadges()).contains(RiskBadge.BLOCKED);
    ManagerHeatmapCell do2 =
        saved.stream()
            .filter(x -> x.getDefiningObjectiveId().equals(DO2))
            .findFirst()
            .orElseThrow();
    assertThat(do2.getBlockedCount()).isZero(); // work_type=BLOCKER no longer fires blocked
    assertThat(do2.getRiskBadges()).doesNotContain(RiskBadge.BLOCKED);
  }

  // --- 6.3b stale-cell deletion: a DO no longer touched by any commitment has its cell DELETED
  // (the recompute is no longer upsert-only — a DO remap, e.g. dispute-respond's rule-#2 SO
  // revision, must not leave an orphan cell); a still-touched DO's cell is kept + updated ----
  @Test
  void recompute_deletesStaleCells_keepsLiveCells() {
    when(summaries.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(MGR, IC, WEEK))
        .thenReturn(Optional.empty());
    ManagerHeatmapCell liveDo1 = existingCell(DO1); // a DO the new commitment set still touches
    ManagerHeatmapCell staleDo2 =
        existingCell(DO2); // a DO the new commitment set no longer touches
    when(cells.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(MGR, IC, WEEK))
        .thenReturn(List.of(liveDo1, staleDo2));
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    WeeklyCommitment onlyDo1 = commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null);

    service.recompute(plan(PlanState.LOCKED), MGR, List.of(onlyDo1), review());

    verify(cells).delete(staleDo2); // DO2 orphan removed
    verify(cells, never()).delete(liveDo1); // DO1 still live → kept
    ArgumentCaptor<ManagerHeatmapCell> saved = ArgumentCaptor.forClass(ManagerHeatmapCell.class);
    verify(cells).save(saved.capture());
    assertThat(saved.getValue().getDefiningObjectiveId()).isEqualTo(DO1); // DO1 cell upserted
  }

  // --- REQ-F-013 (069 ADD): a REVIEWED_WITH_DISPUTES review stays NOT overdue even when past
  // reviewDueAt — is_review_overdue + the OVERDUE_REVIEW/UNREVIEWED badges are gated on
  // status==NOT_REVIEWED, so the not-NOT_REVIEWED branch short-circuits regardless of the Clock
  // ----
  @Test
  void recompute_reviewedWithDisputesPastDue_notOverdueNoBadge() {
    stubEmptyProjectionLookups();
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    ManagerReview withDisputesPastDue = new ManagerReview();
    withDisputesPastDue.setId(UUID.randomUUID());
    withDisputesPastDue.setWeeklyPlanId(PLAN);
    withDisputesPastDue.setManagerEmployeeId(MGR);
    withDisputesPastDue.setStatus(ReviewStatus.REVIEWED_WITH_DISPUTES);
    withDisputesPastDue.setReviewDueAt(
        Instant.parse("2026-06-01T00:00:00Z")); // BEFORE the fixed clock (2026-06-02T12:00Z)
    WeeklyCommitment c = commitment(SO1, AlignmentStatus.ALIGNED, WorkType.STRATEGIC, null);

    service.recompute(plan(PlanState.RECONCILED), MGR, List.of(c), withDisputesPastDue);

    assertThat(captureSummary().isReviewOverdue()).isFalse(); // not NOT_REVIEWED ⇒ never overdue
    assertThat(captureCells(1).get(0).getRiskBadges())
        .doesNotContain(RiskBadge.OVERDUE_REVIEW, RiskBadge.UNREVIEWED);
  }

  // --- empty commitment set → no dispute query (empty SQL IN is invalid), zero counts ----
  @Test
  void recompute_emptyCommitments_noDisputeQuery() {
    stubEmptyProjectionLookups();

    service.recompute(plan(PlanState.LOCKED), MGR, List.of(), review());

    ManagerPlanSummary s = captureSummary();
    assertThat(s.getUnresolvedDisputeCount()).isZero();
    assertThat(s.getMisalignedCount()).isZero();
    assertThat(s.getBlockedCount()).isZero();
    verify(disputes, never()).findByCommitmentIdInAndStatusIn(any(), any());
    verify(cells, never()).save(any()); // no linked commitments → no cells
  }
}
