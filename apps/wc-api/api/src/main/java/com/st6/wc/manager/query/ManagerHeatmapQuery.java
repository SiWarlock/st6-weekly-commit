package com.st6.wc.manager.query;

import com.st6.wc.employee.Employee;
import com.st6.wc.manager.dto.HeatmapCellDto;
import com.st6.wc.manager.mapper.ManagerProjectionMapper;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.rcdo.DefiningObjective;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Criteria-API heatmap read (task 6.5b/E14, §9/§14) — ONE query cross-joining {@code employee} (the
 * report display name) + {@code defining_objective} (the DO title) onto {@code
 * manager_heatmap_cell} via the flat-UUID FKs, scoped to the authenticated manager. N+1-free (a
 * fixed single statement regardless of cell count; NOT paginated → no count query). An api-layer
 * query over the shared projection + RCDO entities, mirroring {@link ManagerCommandCenterQuery}.
 * The IDOR scope ({@code manager_employee_id} = the authenticated manager) is set by the service
 * and is the load-bearing WHERE — a manager can never address another's cells.
 */
@Repository
public class ManagerHeatmapQuery {

  @PersistenceContext private EntityManager em;

  private final ManagerProjectionMapper mapper;

  public ManagerHeatmapQuery(ManagerProjectionMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * The manager's active-direct-report cells for {@code weekStart}, optionally narrowed to one
   * Defining Objective. {@code managerEmployeeId} is the IDOR scope (never a request value).
   */
  public List<HeatmapCellDto> findCells(
      UUID managerEmployeeId, LocalDate weekStart, UUID definingObjectiveId) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<ManagerHeatmapCell> h = cq.from(ManagerHeatmapCell.class);
    Root<Employee> e = cq.from(Employee.class);
    Root<DefiningObjective> d = cq.from(DefiningObjective.class);
    cq.multiselect(h, e.get("displayName"), d.get("title"));
    List<Predicate> where = new ArrayList<>();
    where.add(cb.equal(e.get("id"), h.get("employeeId"))); // display-name join (1:1 via FK)
    where.add(cb.equal(d.get("id"), h.get("definingObjectiveId"))); // DO-title join (1:1 via FK)
    where.add(
        cb.equal(h.get("managerEmployeeId"), managerEmployeeId)); // IDOR scope — never widened
    where.add(cb.equal(h.get("weekStartDate"), weekStart));
    if (definingObjectiveId != null) {
      where.add(cb.equal(h.get("definingObjectiveId"), definingObjectiveId));
    }
    cq.where(where.toArray(new Predicate[0]));
    // stable grid order: by report name, then DO column
    cq.orderBy(cb.asc(e.get("displayName")), cb.asc(h.get("definingObjectiveId")));
    return em.createQuery(cq).getResultList().stream()
        .map(
            t ->
                mapper.toHeatmapCellDto(
                    t.get(0, ManagerHeatmapCell.class),
                    t.get(1, String.class),
                    t.get(2, String.class)))
        .toList();
  }
}
