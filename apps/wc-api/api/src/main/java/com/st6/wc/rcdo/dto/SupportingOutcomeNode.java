package com.st6.wc.rcdo.dto;

import java.util.UUID;

/**
 * Leaf of the {@link RcdoTreeDto} hierarchy — a Supporting Outcome node (task 3.1, Appendix B.4).
 * The link target every locked planned commitment must reference (safety rule #1, enforced in 3.5).
 * A record, never a JPA entity (forbidden-pattern #3); carries {@code definingObjectiveId} as the
 * parent id. Mirrors B.4 verbatim: {@code id}, {@code definingObjectiveId}, {@code title}, {@code
 * description?}, {@code active}.
 */
public record SupportingOutcomeNode(
    UUID id, UUID definingObjectiveId, String title, String description, boolean active) {}
