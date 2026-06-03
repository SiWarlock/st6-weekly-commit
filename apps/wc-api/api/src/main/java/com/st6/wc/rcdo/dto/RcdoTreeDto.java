package com.st6.wc.rcdo.dto;

import java.util.List;

/**
 * The {@code GET /api/rcdo} response (task 3.1, §5 E2 / Appendix B.4) — the full nested RCDO
 * hierarchy as an <strong>object wrapper</strong> {@code { rallyCries: RallyCryNode[] }}, never a
 * bare array (per the §5 envelope conventions). Read-only; no mutation endpoint exists anywhere
 * (REQ-D-003). The 2nd DTO across the API boundary (after {@code MeDto}); a record, never a JPA
 * entity (forbidden-pattern #3). The list is defensively copied to an immutable list at
 * construction.
 */
public record RcdoTreeDto(List<RallyCryNode> rallyCries) {

  public RcdoTreeDto {
    rallyCries = List.copyOf(rallyCries);
  }
}
