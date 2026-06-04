package com.st6.wc.manager.query;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.employee.Employee;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.manager.mapper.ManagerProjectionMapper;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.ManagerPlanSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Criteria-API command-center read (task 6.5a, §9/§14) — ONE indexed projection query cross-joining
 * {@code employee} for the display name (flat-UUID FK, no JPA association → Specifications can't
 * sort a non-association join; Criteria controls the join + ORDER BY), AND-combining the optional
 * summary-level filters, applying the F.5 sort + pagination, plus ONE count query — N+1-free (a
 * fixed two statements regardless of row count). An api-layer query over the shared projection +
 * employee entities (the dynamic read is an api concern; the shared {@code
 * ManagerPlanSummaryRepository} stays a plain upsert repo). The IDOR scope ({@code
 * manager_employee_id} = the authenticated manager) is the first WHERE predicate, set by the
 * service — a manager can never address another's rows. The 6.5a-2 cross-table EXISTS filters slot
 * into {@link #summaryPredicates} without touching this structure.
 */
@Repository
public class ManagerCommandCenterQuery {

  @PersistenceContext private EntityManager em;

  private final ManagerProjectionMapper mapper;

  public ManagerCommandCenterQuery(ManagerProjectionMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * DTO sort-property → {@code ManagerPlanSummary} attribute; the joined display name is special.
   */
  private static final Map<String, String> SUMMARY_SORT =
      Map.of(
          "weekStartDate", "weekStartDate",
          "planState", "planState",
          "reviewStatus", "reviewStatus",
          "isReviewOverdue", "reviewOverdue",
          "employeeId", "employeeId",
          "updatedAt", "updatedAt");

  public Page<ManagerCommandCenterRowDto> findCommandCenter(
      UUID managerEmployeeId,
      LocalDate weekStart,
      CommandCenterFilters filters,
      Pageable pageable) {
    CriteriaBuilder cb = em.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<ManagerPlanSummary> s = cq.from(ManagerPlanSummary.class);
    Root<Employee> e = cq.from(Employee.class);
    cq.multiselect(s, e.get("displayName"));
    List<Predicate> where = new ArrayList<>();
    where.add(cb.equal(e.get("id"), s.get("employeeId"))); // display-name cross-join (1:1 via FK)
    where.addAll(summaryPredicates(cb, s, managerEmployeeId, weekStart, filters));
    where.addAll(crossTablePredicates(cb, cq, s, filters));
    cq.where(where.toArray(new Predicate[0]));
    cq.orderBy(orders(cb, s, e, pageable.getSort()));

    var query = em.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    List<ManagerCommandCenterRowDto> rows =
        query.getResultList().stream()
            .map(t -> mapper.toRowDto(t.get(0, ManagerPlanSummary.class), t.get(1, String.class)))
            .toList();

    // count is summary-only (the cross-join is 1:1 via the employee FK → no row multiplication);
    // the
    // cross-table EXISTS narrow it identically to the page query so the total stays consistent.
    CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
    Root<ManagerPlanSummary> cs = countQ.from(ManagerPlanSummary.class);
    List<Predicate> countWhere =
        new ArrayList<>(summaryPredicates(cb, cs, managerEmployeeId, weekStart, filters));
    countWhere.addAll(crossTablePredicates(cb, countQ, cs, filters));
    countQ.select(cb.count(cs)).where(countWhere.toArray(new Predicate[0]));
    long total = em.createQuery(countQ).getSingleResult();

    return new PageImpl<>(rows, pageable, total);
  }

  /** Summary-level predicates (no employee join) — reused by the page + count queries. */
  private static List<Predicate> summaryPredicates(
      CriteriaBuilder cb,
      Root<ManagerPlanSummary> s,
      UUID managerEmployeeId,
      LocalDate weekStart,
      CommandCenterFilters f) {
    List<Predicate> p = new ArrayList<>();
    p.add(cb.equal(s.get("managerEmployeeId"), managerEmployeeId)); // IDOR scope — never widened
    p.add(cb.equal(s.get("weekStartDate"), weekStart));
    if (f.employeeId() != null) {
      p.add(cb.equal(s.get("employeeId"), f.employeeId()));
    }
    if (f.planState() != null) {
      p.add(cb.equal(s.get("planState"), f.planState()));
    }
    if (f.reviewStatus() != null) {
      p.add(cb.equal(s.get("reviewStatus"), f.reviewStatus()));
    }
    if (f.overdue() != null) {
      p.add(cb.equal(s.get("reviewOverdue"), f.overdue()));
    }
    return p;
  }

  /**
   * The 6.5a-2 cross-table EXISTS predicates — each NARROWS the manager-scoped result; the IDOR
   * scope (set in {@link #summaryPredicates}) is never touched. {@code definingObjectiveId} is an
   * EXISTS over {@code manager_heatmap_cell} correlated to the outer summary at the full grain
   * (manager/report/week/DO); {@code priority}/{@code workType}/{@code alignmentStatus} are each an
   * EXISTS over {@code weekly_commitment} correlated on the outer plan id (so they can't reach a
   * commitment outside the manager's already-scoped report). The enclosing {@code query} supplies
   * the subquery factory, so the same helper serves both the page and count queries.
   */
  private static List<Predicate> crossTablePredicates(
      CriteriaBuilder cb,
      CriteriaQuery<?> query,
      Root<ManagerPlanSummary> s,
      CommandCenterFilters f) {
    List<Predicate> p = new ArrayList<>();
    if (f.definingObjectiveId() != null) {
      Subquery<UUID> sub = query.subquery(UUID.class);
      Root<ManagerHeatmapCell> h = sub.from(ManagerHeatmapCell.class);
      sub.select(h.get("id"));
      sub.where(
          cb.equal(h.get("managerEmployeeId"), s.get("managerEmployeeId")),
          cb.equal(h.get("employeeId"), s.get("employeeId")),
          cb.equal(h.get("weekStartDate"), s.get("weekStartDate")),
          cb.equal(h.get("definingObjectiveId"), f.definingObjectiveId()));
      p.add(cb.exists(sub));
    }
    if (f.priority() != null) {
      p.add(cb.exists(commitmentExists(cb, query, s, "priority", f.priority())));
    }
    if (f.workType() != null) {
      p.add(cb.exists(commitmentExists(cb, query, s, "workType", f.workType())));
    }
    if (f.alignmentStatus() != null) {
      p.add(cb.exists(commitmentExists(cb, query, s, "alignmentStatus", f.alignmentStatus())));
    }
    return p;
  }

  /**
   * A correlated EXISTS over {@code weekly_commitment} on the outer plan id + an attribute equals.
   */
  private static Subquery<UUID> commitmentExists(
      CriteriaBuilder cb,
      CriteriaQuery<?> query,
      Root<ManagerPlanSummary> s,
      String attribute,
      Object value) {
    Subquery<UUID> sub = query.subquery(UUID.class);
    Root<WeeklyCommitment> wc = sub.from(WeeklyCommitment.class);
    sub.select(wc.get("id"));
    sub.where(
        cb.equal(wc.get("weeklyPlanId"), s.get("weeklyPlanId")),
        cb.equal(wc.get(attribute), value));
    return sub;
  }

  private static List<Order> orders(
      CriteriaBuilder cb, Root<ManagerPlanSummary> s, Root<Employee> e, Sort sort) {
    List<Order> orders = new ArrayList<>();
    for (Sort.Order o : sort) {
      Path<?> path;
      if ("employeeDisplayName".equals(o.getProperty())) {
        path = e.get("displayName");
      } else if (SUMMARY_SORT.containsKey(o.getProperty())) {
        path = s.get(SUMMARY_SORT.get(o.getProperty()));
      } else {
        continue; // unknown sort property → ignored (the service guarantees a valid default sort)
      }
      orders.add(o.isAscending() ? cb.asc(path) : cb.desc(path));
    }
    return orders;
  }
}
