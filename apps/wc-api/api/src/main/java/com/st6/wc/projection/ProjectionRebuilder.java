package com.st6.wc.projection;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.st6.wc.audit.AuditService;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The internal projection rebuild logic (task 6.7, REQ-D-013, §9/§23) — truncates both manager
 * projection tables and recomputes them from source for every {@link WeeklyPlan} via the SAME
 * {@link ProjectionRefresher#recomputeForPlan} the 9 synchronous triggers use, so the rebuilt
 * read-model is byte-identical to the incremental one and drift/orphan cells are corrected ({@code
 * rebuild == incremental}). Runs as the SYSTEM actor (null {@code actor_employee_id}).
 * Unmanaged/unreviewed plans no-op (the §28 skip). One {@code @Transactional} run — a reader never
 * sees a half-rebuilt projection. Writes one safe-metadata audit row per run, even on 0 plans.
 *
 * <p>The inert logic component; activation as a one-shot job is {@link
 * com.st6.wc.job.ProjectionRebuildRunner}'s job (gated on {@code --app.job=rebuild-projections}).
 */
@Component
public class ProjectionRebuilder {

  static final String AUDIT_ACTION = "PROJECTIONS_REBUILT";

  private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilder.class);

  private final WeeklyPlanRepository plans;
  private final ManagerPlanSummaryRepository summaries;
  private final ManagerHeatmapCellRepository heatmapCells;
  private final ProjectionRefresher refresher;
  private final AuditService auditService;

  public ProjectionRebuilder(
      WeeklyPlanRepository plans,
      ManagerPlanSummaryRepository summaries,
      ManagerHeatmapCellRepository heatmapCells,
      ProjectionRefresher refresher,
      AuditService auditService) {
    this.plans = plans;
    this.summaries = summaries;
    this.heatmapCells = heatmapCells;
    this.refresher = refresher;
    this.auditService = auditService;
  }

  /**
   * Truncate both projection tables, then recompute from source for every plan (the §28 skip leaves
   * unmanaged/unreviewed plans unprojected). Atomic. Returns the number of plans processed.
   */
  @Transactional
  public int rebuild() {
    // Truncate first. Safe within the single rebuild txn: nothing loads a projection entity before
    // these bulk deletes, so the recompute's find-or-create can't read a stale L1-cached row.
    heatmapCells.deleteAllInBatch();
    summaries.deleteAllInBatch();

    List<WeeklyPlan> all = plans.findAll();
    for (WeeklyPlan plan : all) {
      refresher.recomputeForPlan(plan); // §28: unmanaged/unreviewed → no-op
    }

    auditRun(all.size());
    log.info(
        "Projection rebuild: {} plan(s) processed → {} summary + {} heatmap-cell row(s); one-shot.",
        all.size(),
        summaries.count(),
        heatmapCells.count());
    return all.size();
  }

  /** One SYSTEM-actor (null actor) run audit; safe count-only metadata via an escaped JSON node. */
  private void auditRun(int plansProcessed) {
    String safeMetadata =
        JsonNodeFactory.instance
            .objectNode()
            .put("plans_processed", plansProcessed)
            .put("summaries_written", summaries.count())
            .put("heatmap_cells_written", heatmapCells.count())
            .toString();
    auditService.record(
        AUDIT_ACTION,
        "ManagerProjection",
        null, // run-level — no single entity id
        null, // SYSTEM actor (§6): null actor_employee_id
        "Rebuilt manager projections from " + plansProcessed + " plan(s)",
        safeMetadata);
  }
}
