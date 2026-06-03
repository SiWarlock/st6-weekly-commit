package com.st6.wc.rcdo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Second tier of the {@link RcdoTreeDto} hierarchy — a Defining Objective node with its nested
 * Supporting Outcomes (task 3.1, Appendix B.4). A record, never a JPA entity (forbidden-pattern
 * #3); carries {@code rallyCryId} as the parent id. Mirrors B.4 verbatim: {@code id}, {@code
 * rallyCryId}, {@code title}, {@code description?}, {@code active}, {@code supportingOutcomes[]}.
 * The nested list is defensively copied to an immutable list at construction (read-only DTO).
 */
public record DefiningObjectiveNode(
    UUID id,
    UUID rallyCryId,
    String title,
    String description,
    boolean active,
    List<SupportingOutcomeNode> supportingOutcomes) {

  public DefiningObjectiveNode {
    supportingOutcomes = List.copyOf(supportingOutcomes);
  }
}
