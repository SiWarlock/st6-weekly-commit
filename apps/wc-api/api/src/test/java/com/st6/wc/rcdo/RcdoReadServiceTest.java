package com.st6.wc.rcdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.rcdo.dto.RcdoTreeDto;
import com.st6.wc.rcdo.mapper.RcdoMapper;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.rcdo.repo.RallyCryRepository;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link RcdoReadService} (task 3.1, §5 E2 / §6 / Appendix B.4). Query-only:
 * assembles the nested tree from three flat finders + the {@link RcdoMapper}, and exposes the
 * {@code findSupportingOutcome} lookup-by-id seam that the 3.4/3.5 commitment→SO linking reuses.
 * The service performs <strong>no active-filtering</strong> (inactive nodes are the contract) and
 * raises the IDOR-safe {@link ResourceNotFoundOrUnauthorizedException} (→ 404 via 2.6) on an
 * unknown SO id. Repos are mocked — DB fidelity is proven in {@code RcdoEndpointTest}.
 */
class RcdoReadServiceTest {

  private final RallyCryRepository rallyCries = mock(RallyCryRepository.class);
  private final DefiningObjectiveRepository definingObjectives =
      mock(DefiningObjectiveRepository.class);
  private final SupportingOutcomeRepository supportingOutcomes =
      mock(SupportingOutcomeRepository.class);
  private final RcdoReadService service =
      new RcdoReadService(rallyCries, definingObjectives, supportingOutcomes, new RcdoMapper());

  private static RallyCry rallyCry(UUID id, String title, boolean active) {
    RallyCry rc = new RallyCry();
    rc.setId(id);
    rc.setTitle(title);
    rc.setActive(active);
    return rc;
  }

  private static DefiningObjective definingObjective(UUID id, UUID rallyCryId, String title) {
    DefiningObjective d = new DefiningObjective();
    d.setId(id);
    d.setRallyCryId(rallyCryId);
    d.setTitle(title);
    d.setActive(true);
    return d;
  }

  private static SupportingOutcome supportingOutcome(UUID id, UUID definingObjectiveId, String t) {
    SupportingOutcome s = new SupportingOutcome();
    s.setId(id);
    s.setDefiningObjectiveId(definingObjectiveId);
    s.setTitle(t);
    s.setActive(true);
    return s;
  }

  // --- tree assembly: pulls from the three ordered finders and nests via the mapper ----
  @Test
  void getRcdoTree_assemblesFromRepos() {
    UUID rcId = UUID.randomUUID();
    UUID doId = UUID.randomUUID();
    UUID soId = UUID.randomUUID();
    when(rallyCries.findAllByOrderByIdAsc()).thenReturn(List.of(rallyCry(rcId, "RC", true)));
    when(definingObjectives.findAllByOrderByIdAsc())
        .thenReturn(List.of(definingObjective(doId, rcId, "DO")));
    when(supportingOutcomes.findAllByOrderByIdAsc())
        .thenReturn(List.of(supportingOutcome(soId, doId, "SO")));

    RcdoTreeDto tree = service.getRcdoTree();

    assertThat(tree.rallyCries()).hasSize(1);
    assertThat(tree.rallyCries().get(0).id()).isEqualTo(rcId);
    assertThat(tree.rallyCries().get(0).definingObjectives().get(0).id()).isEqualTo(doId);
    assertThat(
            tree.rallyCries().get(0).definingObjectives().get(0).supportingOutcomes().get(0).id())
        .isEqualTo(soId);
  }

  // --- the service does NOT filter inactive nodes (do-not-filter is the contract) ----
  @Test
  void getRcdoTree_doesNotFilterInactive() {
    UUID rcId = UUID.randomUUID();
    when(rallyCries.findAllByOrderByIdAsc())
        .thenReturn(List.of(rallyCry(rcId, "Inactive RC", false)));
    when(definingObjectives.findAllByOrderByIdAsc()).thenReturn(List.of());
    when(supportingOutcomes.findAllByOrderByIdAsc()).thenReturn(List.of());

    RcdoTreeDto tree = service.getRcdoTree();

    assertThat(tree.rallyCries()).hasSize(1); // present, not filtered
    assertThat(tree.rallyCries().get(0).active()).isFalse();
  }

  // --- RED #5: SO lookup-by-id resolves a valid id (the 3.4/3.5 linking seam) ----
  @Test
  void findSupportingOutcome_valid_resolves() {
    UUID soId = UUID.randomUUID();
    UUID doId = UUID.randomUUID();
    SupportingOutcome so = supportingOutcome(soId, doId, "SO");
    when(supportingOutcomes.findById(soId)).thenReturn(Optional.of(so));

    SupportingOutcome resolved = service.findSupportingOutcome(soId);

    assertThat(resolved.getId()).isEqualTo(soId);
    assertThat(resolved.getDefiningObjectiveId())
        .isEqualTo(doId); // carries the parent for breadcrumb (3.3)
  }

  // --- RED #6: unknown SO id -> IDOR-safe 404 path (ResourceNotFoundOrUnauthorizedException) ----
  @Test
  void findSupportingOutcome_unknown_notFound() {
    UUID unknown = UUID.randomUUID();
    when(supportingOutcomes.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findSupportingOutcome(unknown))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
  }
}
