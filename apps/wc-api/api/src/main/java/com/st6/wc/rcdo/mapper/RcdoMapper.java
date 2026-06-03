package com.st6.wc.rcdo.mapper;

import com.st6.wc.rcdo.DefiningObjective;
import com.st6.wc.rcdo.RallyCry;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.rcdo.dto.DefiningObjectiveNode;
import com.st6.wc.rcdo.dto.RallyCryNode;
import com.st6.wc.rcdo.dto.RcdoTreeDto;
import com.st6.wc.rcdo.dto.SupportingOutcomeNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assembles three flat entity lists (Rally Cry / Defining Objective / Supporting Outcome) into the
 * nested {@link RcdoTreeDto} (task 3.1, Appendix B.4). A pure transform — no DB, no I/O. Children
 * are grouped under their parent id, <strong>preserving the input order</strong> of each list (the
 * 3.1 finders pre-order by id, so the wire order is the logical strategy order). {@code active} is
 * mapped verbatim — inactive nodes are <strong>never filtered</strong> (the field is the contract,
 * REQ-D-003). A child whose parent id matches no parent is excluded (defensive; FKs make this
 * impossible in a well-formed seed). Entities never cross the boundary (records only,
 * forbidden-pattern #3).
 */
@Component
public class RcdoMapper {

  public RcdoTreeDto toTree(
      List<RallyCry> rallyCries,
      List<DefiningObjective> definingObjectives,
      List<SupportingOutcome> supportingOutcomes) {

    // 1. group Supporting Outcomes by their Defining Objective parent (input order preserved)
    Map<UUID, List<SupportingOutcomeNode>> soByDo = new LinkedHashMap<>();
    for (SupportingOutcome so : supportingOutcomes) {
      soByDo
          .computeIfAbsent(so.getDefiningObjectiveId(), k -> new ArrayList<>())
          .add(
              new SupportingOutcomeNode(
                  so.getId(),
                  so.getDefiningObjectiveId(),
                  so.getTitle(),
                  so.getDescription(),
                  so.isActive()));
    }

    // 2. group Defining Objectives by their Rally Cry parent, nesting each one's Supporting
    // Outcomes
    Map<UUID, List<DefiningObjectiveNode>> doByRc = new LinkedHashMap<>();
    for (DefiningObjective d : definingObjectives) {
      doByRc
          .computeIfAbsent(d.getRallyCryId(), k -> new ArrayList<>())
          .add(
              new DefiningObjectiveNode(
                  d.getId(),
                  d.getRallyCryId(),
                  d.getTitle(),
                  d.getDescription(),
                  d.isActive(),
                  soByDo.getOrDefault(d.getId(), List.of())));
    }

    // 3. build the Rally Cry nodes in input order, nesting each one's Defining Objectives
    List<RallyCryNode> nodes = new ArrayList<>();
    for (RallyCry rc : rallyCries) {
      nodes.add(
          new RallyCryNode(
              rc.getId(),
              rc.getTitle(),
              rc.getDescription(),
              rc.isActive(),
              doByRc.getOrDefault(rc.getId(), List.of())));
    }
    return new RcdoTreeDto(nodes);
  }
}
