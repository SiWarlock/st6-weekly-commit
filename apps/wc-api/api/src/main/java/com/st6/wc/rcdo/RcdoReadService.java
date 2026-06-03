package com.st6.wc.rcdo;

import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.rcdo.dto.RcdoTreeDto;
import com.st6.wc.rcdo.mapper.RcdoMapper;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.rcdo.repo.RallyCryRepository;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query-only read service for the RCDO strategy hierarchy (task 3.1, §5 E2 / §6 / Appendix B.4).
 * Exposes <strong>only</strong> reads — no create/update/delete (REQ-D-003). {@link #getRcdoTree()}
 * pulls three flat id-ordered finders (small bounded catalog — no fetch-joins/N+1) and assembles
 * the nested {@link RcdoTreeDto} via {@link RcdoMapper}; ordering by id over the deliberately
 * sequential seed UUIDs yields the logical strategy order on the wire. {@link
 * #findSupportingOutcome(UUID)} is the linking seam the 3.4/3.5 commitment→Supporting-Outcome flow
 * reuses — it returns the entity (an internal seam, not an API-boundary return) and raises the
 * IDOR-safe {@link ResourceNotFoundOrUnauthorizedException} (→ 404 via the 2.6 chain) on an unknown
 * id. Read-only transaction (multi-query consistency).
 */
@Service
@Transactional(readOnly = true)
public class RcdoReadService {

  private final RallyCryRepository rallyCries;
  private final DefiningObjectiveRepository definingObjectives;
  private final SupportingOutcomeRepository supportingOutcomes;
  private final RcdoMapper mapper;

  public RcdoReadService(
      RallyCryRepository rallyCries,
      DefiningObjectiveRepository definingObjectives,
      SupportingOutcomeRepository supportingOutcomes,
      RcdoMapper mapper) {
    this.rallyCries = rallyCries;
    this.definingObjectives = definingObjectives;
    this.supportingOutcomes = supportingOutcomes;
    this.mapper = mapper;
  }

  /** The full nested RCDO tree (E2), ordered logically via the id-asc finders. */
  public RcdoTreeDto getRcdoTree() {
    return mapper.toTree(
        rallyCries.findAllByOrderByIdAsc(),
        definingObjectives.findAllByOrderByIdAsc(),
        supportingOutcomes.findAllByOrderByIdAsc());
  }

  /**
   * Resolves a Supporting Outcome by id for commitment→SO linking (3.4/3.5). Unknown id → IDOR-safe
   * 404 (never reveals existence). The returned entity stays inside the service layer.
   */
  public SupportingOutcome findSupportingOutcome(UUID id) {
    return supportingOutcomes
        .findById(id)
        .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
  }

  /**
   * Resolves the RC→DO→SO display breadcrumb for a linked commitment's Supporting Outcome (task
   * 3.3a, Appendix B.6) — walks SO → its Defining Objective → its Rally Cry via the flat-FK chain.
   * Keeps RCDO knowledge in the RCDO service (callers pass only a {@code supportingOutcomeId}). A
   * missing link is a data-integrity violation (the FKs guarantee the chain) → IDOR-safe 404.
   */
  public RcdoBreadcrumbDto resolveBreadcrumb(UUID supportingOutcomeId) {
    SupportingOutcome so =
        supportingOutcomes
            .findById(supportingOutcomeId)
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    DefiningObjective definingObjective =
        definingObjectives
            .findById(so.getDefiningObjectiveId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    RallyCry rallyCry =
        rallyCries
            .findById(definingObjective.getRallyCryId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    return new RcdoBreadcrumbDto(
        rallyCry.getId(),
        rallyCry.getTitle(),
        definingObjective.getId(),
        definingObjective.getTitle(),
        so.getId(),
        so.getTitle());
  }
}
