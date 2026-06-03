package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.AuditService;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Atomicity proof for the 4.1 outcome write (§9 — every reconciliation mutation updates the manager
 * projection synchronously <em>in the same transaction</em>). Forces a failure on the
 * <strong>post-recompute</strong> step by making {@link AuditService#record} throw (it is
 * {@code @Transactional(REQUIRED)}, so it joins the outcome transaction — a throw rolls the whole
 * unit back). Asserts that NEITHER the commitment outcome NOR the projection write survives —
 * proving the commitment save, the projection upsert, and the audit are one atomic unit.
 * {@code @MockBean} AuditService is class-scoped, so this lives in its own class (the happy-path
 * audit assertion stays in {@code CommitmentPatchDeleteEndpointTest}).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class CommitmentOutcomeAtomicityTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;

  @MockBean private AuditService auditService;

  @AfterEach
  void cleanup() {
    summaries.deleteAll();
    heatmapCells.deleteAll();
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

  private WeeklyPlan saveReconcilingPlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.RECONCILING);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment saveCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Original");
    c.setSupportingOutcomeId(SEED_SO_1_1);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  // --- a forced post-recompute failure rolls back BOTH the outcome and the projection write ----
  @Test
  void patch_forcedAuditFailure_rollsBackOutcomeAndProjection() throws Exception {
    doThrow(new RuntimeException("boom"))
        .when(auditService)
        .record(any(), any(), any(), any(), any(), any());

    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    ManagerRelationship rel = new ManagerRelationship();
    rel.setId(UUID.randomUUID());
    rel.setManagerEmployeeId(mgr.getId());
    rel.setDirectReportEmployeeId(ic.getId());
    rel.setActive(true);
    relationships.saveAndFlush(rel);
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    WeeklyCommitment c = saveCommitment(plan.getId());
    ManagerReview review = new ManagerReview();
    review.setId(UUID.randomUUID());
    review.setWeeklyPlanId(plan.getId());
    review.setManagerEmployeeId(mgr.getId());
    review.setStatus(ReviewStatus.NOT_REVIEWED);
    review.setReviewDueAt(Instant.parse("2026-06-02T22:00:00Z"));
    reviews.saveAndFlush(review);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationOutcome\":\"COMPLETED\",\"outcomeNote\":\"x\"}"))
        .andExpect(status().is5xxServerError());

    // the whole transaction rolled back: outcome NOT persisted AND no projection row created
    assertThat(commitments.findById(c.getId()).orElseThrow().getReconciliationOutcome()).isNull();
    assertThat(summaries.findAll()).isEmpty();
    assertThat(heatmapCells.findAll()).isEmpty();
  }
}
