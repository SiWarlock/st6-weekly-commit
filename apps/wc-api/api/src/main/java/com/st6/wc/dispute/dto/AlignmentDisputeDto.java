package com.st6.wc.dispute.dto;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Alignment dispute across the API boundary (Appendix B.8; the E17/E18/E19 response). A record,
 * never an entity (forbidden-pattern #3 — the audit quartet must not leak; pinned by the endpoint's
 * {@code .doesNotExist()} leak test). {@code managerEmployeeId} is the opener (the direct manager),
 * exposed per B.8. {@code allowedActions} is server-authoritative affordance (§15) — empty until
 * the RESPOND (5.4) / RESOLVE (5.5) slices enforce those actions ("no affordance without
 * enforcement").
 */
public record AlignmentDisputeDto(
    UUID id,
    UUID commitmentId,
    UUID managerEmployeeId,
    DisputeStatus status,
    FlagType flagType,
    String managerNote,
    String icResponse,
    Instant resolvedAt,
    List<AllowedAction> allowedActions,
    long version) {

  public AlignmentDisputeDto {
    allowedActions = List.copyOf(allowedActions);
  }
}
