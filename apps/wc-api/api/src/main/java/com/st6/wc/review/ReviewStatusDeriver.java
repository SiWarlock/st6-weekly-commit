package com.st6.wc.review;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.ReviewStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Derives a plan's manager-review status server-side (task 5.2, §3 / §9 / REQ-F-011/013) — never
 * client-set (B.7). {@code REVIEWED_WITH_DISPUTES} iff the plan's commitments carry ≥1 dispute in
 * the unresolved bucket {@code {OPEN, IC_RESPONDED}} (a {@code RESOLVED} dispute is historical and
 * does not count), else {@code REVIEWED}. The matching count also drives the DTO's {@code
 * unresolvedDisputeCount}.
 */
@Service
public class ReviewStatusDeriver {

  private final WeeklyCommitmentRepository commitments;
  private final AlignmentDisputeRepository disputes;

  public ReviewStatusDeriver(
      WeeklyCommitmentRepository commitments, AlignmentDisputeRepository disputes) {
    this.commitments = commitments;
    this.disputes = disputes;
  }

  private static final List<DisputeStatus> UNRESOLVED =
      List.of(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED);

  /**
   * The number of unresolved disputes ({@code OPEN} or {@code IC_RESPONDED}) on the plan's
   * commitments. An empty commitment set short-circuits to 0 (an empty SQL {@code IN} is invalid).
   */
  public int unresolvedDisputeCount(UUID planId) {
    List<UUID> commitmentIds =
        commitments.findByWeeklyPlanIdOrderByIdAsc(planId).stream()
            .map(WeeklyCommitment::getId)
            .toList();
    if (commitmentIds.isEmpty()) {
      return 0;
    }
    return disputes.countByCommitmentIdInAndStatusIn(commitmentIds, UNRESOLVED);
  }

  /** The single-sourced rule: any unresolved dispute ⇒ {@code REVIEWED_WITH_DISPUTES}. */
  public static ReviewStatus statusFor(int unresolvedDisputeCount) {
    return unresolvedDisputeCount > 0 ? ReviewStatus.REVIEWED_WITH_DISPUTES : ReviewStatus.REVIEWED;
  }

  public ReviewStatus derive(UUID planId) {
    return statusFor(unresolvedDisputeCount(planId));
  }
}
