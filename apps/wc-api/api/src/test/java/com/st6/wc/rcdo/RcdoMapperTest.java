package com.st6.wc.rcdo;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.rcdo.dto.DefiningObjectiveNode;
import com.st6.wc.rcdo.dto.RallyCryNode;
import com.st6.wc.rcdo.dto.RcdoTreeDto;
import com.st6.wc.rcdo.mapper.RcdoMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure-function proof of {@link RcdoMapper} (task 3.1, Appendix B.4). The mapper assembles three
 * flat entity lists (Rally Cry / Defining Objective / Supporting Outcome) into the nested {@link
 * RcdoTreeDto} object-wrapper by parent id, preserving input order, mapping {@code active} verbatim
 * (never filtering), and never leaking an entity across the boundary (records only,
 * forbidden-pattern #3). No DB — the mapper is a pure transform.
 */
class RcdoMapperTest {

  private final RcdoMapper mapper = new RcdoMapper();

  private static RallyCry rallyCry(UUID id, String title, boolean active) {
    RallyCry rc = new RallyCry();
    rc.setId(id);
    rc.setTitle(title);
    rc.setDescription(null);
    rc.setActive(active);
    return rc;
  }

  private static DefiningObjective definingObjective(
      UUID id, UUID rallyCryId, String title, boolean active) {
    DefiningObjective d = new DefiningObjective();
    d.setId(id);
    d.setRallyCryId(rallyCryId);
    d.setTitle(title);
    d.setDescription(null);
    d.setActive(active);
    return d;
  }

  private static SupportingOutcome supportingOutcome(
      UUID id, UUID definingObjectiveId, String title, boolean active) {
    SupportingOutcome s = new SupportingOutcome();
    s.setId(id);
    s.setDefiningObjectiveId(definingObjectiveId);
    s.setTitle(title);
    s.setDescription(null);
    s.setActive(active);
    return s;
  }

  // --- RED #2: empty input -> object wrapper with an empty (never null) array ----
  @Test
  void emptyLists_returnEmptyWrapper() {
    RcdoTreeDto tree = mapper.toTree(List.of(), List.of(), List.of());

    assertThat(tree).isNotNull();
    assertThat(tree.rallyCries())
        .isNotNull()
        .isEmpty(); // {"rallyCries":[]} — never null, never bare []
  }

  // --- RED #1: nested assembly with correct parent ids, order preserved ----
  @Test
  void assemblesNestedTreeWithParentIds() {
    UUID rcId = UUID.randomUUID();
    UUID do1 = UUID.randomUUID();
    UUID do2 = UUID.randomUUID();
    UUID so1 = UUID.randomUUID();
    UUID so2 = UUID.randomUUID();

    RcdoTreeDto tree =
        mapper.toTree(
            List.of(rallyCry(rcId, "RC", true)),
            List.of(
                definingObjective(do1, rcId, "DO-A", true),
                definingObjective(do2, rcId, "DO-B", true)),
            List.of(
                supportingOutcome(so1, do1, "SO-A1", true),
                supportingOutcome(so2, do2, "SO-B1", true)));

    assertThat(tree.rallyCries()).hasSize(1);
    RallyCryNode rc = tree.rallyCries().get(0);
    assertThat(rc.id()).isEqualTo(rcId);
    assertThat(rc.title()).isEqualTo("RC");
    // children nest under their parent, in input order
    assertThat(rc.definingObjectives())
        .extracting(DefiningObjectiveNode::title)
        .containsExactly("DO-A", "DO-B");
    DefiningObjectiveNode dA = rc.definingObjectives().get(0);
    DefiningObjectiveNode dB = rc.definingObjectives().get(1);
    assertThat(dA.rallyCryId()).isEqualTo(rcId); // parent id on the child (B.4)
    assertThat(dB.rallyCryId()).isEqualTo(rcId);
    assertThat(dA.supportingOutcomes()).hasSize(1);
    assertThat(dA.supportingOutcomes().get(0).id()).isEqualTo(so1);
    assertThat(dA.supportingOutcomes().get(0).definingObjectiveId()).isEqualTo(do1);
    assertThat(dB.supportingOutcomes().get(0).id()).isEqualTo(so2);
    assertThat(dB.supportingOutcomes().get(0).definingObjectiveId()).isEqualTo(do2);
  }

  // --- RED #3: inactive nodes are mapped active=false, NOT filtered out (the field is the
  // contract) -
  @Test
  void inactiveNodes_mappedActiveFalse_notFiltered() {
    UUID rcId = UUID.randomUUID();
    UUID doId = UUID.randomUUID();
    UUID soId = UUID.randomUUID();

    RcdoTreeDto tree =
        mapper.toTree(
            List.of(rallyCry(rcId, "RC", false)),
            List.of(definingObjective(doId, rcId, "DO", false)),
            List.of(supportingOutcome(soId, doId, "SO", false)));

    RallyCryNode rc = tree.rallyCries().get(0);
    assertThat(rc.active()).isFalse(); // serialized, not dropped
    assertThat(rc.definingObjectives().get(0).active()).isFalse();
    assertThat(rc.definingObjectives().get(0).supportingOutcomes().get(0).active()).isFalse();
  }

  // --- defensive: a child whose parent id matches no parent is excluded, never crashes ----
  @Test
  void childWithoutMatchingParent_excluded() {
    UUID rcId = UUID.randomUUID();
    UUID doId = UUID.randomUUID();
    UUID orphanDoId = UUID.randomUUID();

    RcdoTreeDto tree =
        mapper.toTree(
            List.of(rallyCry(rcId, "RC", true)),
            // one DO under the RC + one orphan DO whose rallyCryId matches no RC
            List.of(
                definingObjective(doId, rcId, "DO", true),
                definingObjective(orphanDoId, UUID.randomUUID(), "ORPHAN", true)),
            List.of());

    assertThat(tree.rallyCries()).hasSize(1);
    assertThat(tree.rallyCries().get(0).definingObjectives())
        .extracting(DefiningObjectiveNode::title)
        .containsExactly("DO"); // the orphan is not attached to a phantom parent
  }
}
