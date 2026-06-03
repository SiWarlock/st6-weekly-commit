package com.st6.wc.rcdo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Top tier of the {@link RcdoTreeDto} hierarchy — a Rally Cry node with its nested Defining
 * Objectives (task 3.1, Appendix B.4). A record, never a JPA entity (forbidden-pattern #3). Mirrors
 * B.4 verbatim: {@code id}, {@code title}, {@code description?}, {@code active}, {@code
 * definingObjectives[]}. The nested list is defensively copied to an immutable list at construction
 * (read-only DTO).
 */
public record RallyCryNode(
    UUID id,
    String title,
    String description,
    boolean active,
    List<DefiningObjectiveNode> definingObjectives) {

  public RallyCryNode {
    definingObjectives = List.copyOf(definingObjectives);
  }
}
