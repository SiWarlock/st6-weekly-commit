package com.st6.wc.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.DisputeService;
import com.st6.wc.dispute.dto.OpenDisputeRequest;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * RISK-003 transactional-atomicity proof (task 6.3b, §9/§17): a projection-upsert failure during a
 * dispute/review trigger rolls back the WHOLE domain mutation — no partial drift. Drives the real
 * {@link DisputeService#open} (its own {@code @Transactional}) against real PG16 with a
 * {@code @MockBean} {@link ManagerPlanSummaryRepository} whose {@code save} throws; asserts the
 * dispute row (the just-flushed domain mutation), the heatmap cell, and the audit are ALL absent
 * after the exception — the projection upsert, the dispute INSERT, and the audit are one atomic
 * unit.
 *
 * <p>The {@code @MockBean} projection repo makes this a distinct context; it lives in its own class
 * (LESSONS §29 — the shared {@link AbstractAppBootTest} PG container + the capped Hikari pool keep
 * the extra context cheap). The seeded plan is LOCKED directly (no real lock here — the projection
 * repo is mocked), so {@code open}'s post-state guard passes.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
class ProjectionTriggerRollbackTest extends AbstractAppBootTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private AuditEventRepository auditEvents;

  @Autowired private DisputeService disputeService;

  @MockBean
  private ManagerPlanSummaryRepository summaries; // its save() throws — the injected fault

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    heatmapCells.deleteAll();
    disputes.deleteAll();
    reviews.deleteAll();
    commitments.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(String email, RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  // --- a projection-upsert failure during dispute-open rolls the WHOLE mutation back (RISK-003)
  // ---
  @Test
  void projectionFailure_rollsBackMutation() {
    // the projection upsert blows up; the lookup returns empty so newSummary→save is reached
    when(summaries.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    doThrow(new RuntimeException("projection upsert boom")).when(summaries).save(any());

    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    ManagerRelationship rel = new ManagerRelationship();
    rel.setId(UUID.randomUUID());
    rel.setManagerEmployeeId(mgr.getId());
    rel.setDirectReportEmployeeId(ic.getId());
    rel.setActive(true);
    relationships.saveAndFlush(rel);

    WeeklyPlan plan = new WeeklyPlan();
    plan.setId(UUID.randomUUID());
    plan.setEmployeeId(ic.getId());
    plan.setWeekStartDate(WEEK);
    plan.setWeekEndDate(WEEK.plusDays(6));
    plan.setState(PlanState.LOCKED); // disputes are a post-lock manager action
    plans.saveAndFlush(plan);

    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(plan.getId());
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship it");
    c.setSupportingOutcomeId(SO_1_1);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    commitments.saveAndFlush(c);

    ManagerReview review = new ManagerReview();
    review.setId(UUID.randomUUID());
    review.setWeeklyPlanId(plan.getId());
    review.setManagerEmployeeId(mgr.getId());
    review.setStatus(ReviewStatus.NOT_REVIEWED);
    review.setReviewDueAt(Instant.parse("2026-06-30T22:00:00Z"));
    reviews.saveAndFlush(review);

    UserPrincipal mgrp = new UserPrincipal(mgr.getId(), RoleType.MANAGER, true);

    assertThatThrownBy(
            () ->
                disputeService.open(
                    mgrp, c.getId(), new OpenDisputeRequest(FlagType.MISALIGNED, "off-strategy")))
        .isInstanceOf(RuntimeException.class);

    // the whole txn rolled back: the (flushed) dispute INSERT, any heatmap cell, and the audit are
    // ALL absent — no partial drift between the domain mutation and the read model.
    assertThat(disputes.findAll()).isEmpty();
    assertThat(heatmapCells.findAll()).isEmpty();
    assertThat(auditEvents.findAll()).isEmpty();
  }
}
