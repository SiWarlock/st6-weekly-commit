package com.st6.wc.manager;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.manager.dto.PageEnvelope;
import com.st6.wc.manager.dto.ReviewStateFilter;
import com.st6.wc.manager.query.CommandCenterFilters;
import com.st6.wc.manager.query.ManagerCommandCenterQuery;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * E13 manager command-center read (task 6.5a, §6/§9/§14). Authorizes the coarse manager-only gate
 * ({@code authorizeTeamHeatmapAccess} → {@code 403 MANAGER_ROLE_REQUIRED} for an IC), then the
 * <strong>IDOR-safe direct-report scoping</strong>: the query is scoped to {@code
 * manager_employee_id = the authenticated manager} (NEVER a request value), so a manager can never
 * address another's rows — unreachable, not just hidden. Applies the F.5 default sort when the
 * client sends none, clamps the page size (backstop to the {@code spring.data.web.pageable}
 * config), translates {@code reviewState} ({@code OVERDUE} → the derived {@code
 * is_review_overdue}), delegates to the N+1-free Criteria query, maps to B.11, and wraps in the
 * B.20 envelope.
 */
@Service
public class ManagerQueryService {

  private static final int MAX_PAGE_SIZE = 100; // F.5 / B.20 (backstop to the resolver config)
  private static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.desc("weekStartDate"), Sort.Order.asc("employeeDisplayName"));

  private final DomainAuthorizationService authz;
  private final ManagerCommandCenterQuery query;

  public ManagerQueryService(DomainAuthorizationService authz, ManagerCommandCenterQuery query) {
    this.authz = authz;
    this.query = query;
  }

  public PageEnvelope<ManagerCommandCenterRowDto> commandCenter(
      UserPrincipal principal,
      LocalDate weekStart,
      UUID employeeId,
      PlanState planState,
      ReviewStateFilter reviewState,
      UUID definingObjectiveId,
      Priority priority,
      WorkType workType,
      AlignmentStatus alignmentStatus,
      Pageable pageable) {
    authz.authorizeTeamHeatmapAccess(principal); // coarse: non-manager → 403 MANAGER_ROLE_REQUIRED

    CommandCenterFilters filters =
        toFilters(
            employeeId,
            planState,
            reviewState,
            definingObjectiveId,
            priority,
            workType,
            alignmentStatus);
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : DEFAULT_SORT;
    Pageable effective = PageRequest.of(pageable.getPageNumber(), size, sort);

    // IDOR scope: principal.employeeId() — never a request value
    Page<ManagerCommandCenterRowDto> page =
        query.findCommandCenter(principal.employeeId(), weekStart, filters, effective);
    return PageEnvelope.of(page);
  }

  private static CommandCenterFilters toFilters(
      UUID employeeId,
      PlanState planState,
      ReviewStateFilter reviewState,
      UUID definingObjectiveId,
      Priority priority,
      WorkType workType,
      AlignmentStatus alignmentStatus) {
    ReviewStatus reviewStatus = null;
    Boolean overdue = null;
    if (reviewState == ReviewStateFilter.OVERDUE) {
      overdue = true; // the derived is_review_overdue filter — there is no stored OVERDUE status
    } else if (reviewState != null) {
      reviewStatus = ReviewStatus.valueOf(reviewState.name());
    }
    return new CommandCenterFilters(
        employeeId,
        planState,
        reviewStatus,
        overdue,
        definingObjectiveId,
        priority,
        workType,
        alignmentStatus);
  }
}
