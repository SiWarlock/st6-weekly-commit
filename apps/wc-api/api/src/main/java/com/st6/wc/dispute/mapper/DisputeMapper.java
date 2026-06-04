package com.st6.wc.dispute.mapper;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.enums.DisputeStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link AlignmentDispute} entity to its {@link AlignmentDisputeDto} (task 5.3, Appendix
 * B.8). The context-free {@link #toDto(AlignmentDispute)} emits an empty {@code allowedActions}
 * (write-response path — the UI re-reads the plan); the context-aware {@link
 * #toDto(AlignmentDispute, UUID, UUID, boolean)} (task 5.5b) computes the per-viewer affordances
 * that MIRROR the E18/E19 enforcement (§24, "no affordance without enforcement"). Used on the plan
 * read (E3/E4) via {@code CommitmentMapper}.
 */
@Component
public class DisputeMapper {

  /** Context-free map — empty {@code allowedActions} (write responses; the UI re-reads). */
  public AlignmentDisputeDto toDto(AlignmentDispute dispute) {
    return toDto(dispute, List.of());
  }

  /**
   * Context-aware map (task 5.5b) — fills {@code allowedActions} for the viewing actor: {@code
   * RESPOND_DISPUTE} iff the viewer is the owning IC ∧ the dispute is {@code OPEN} (mirrors E18);
   * {@code RESOLVE_DISPUTE} iff the viewer is the active direct manager ∧ the dispute is {@code
   * OPEN}|{@code IC_RESPONDED} (mirrors E19). {@code ownerEmployeeId} = the plan owner (the IC);
   * {@code viewerIsDirectManager} is determined once by {@code PlanMapper}.
   */
  public AlignmentDisputeDto toDto(
      AlignmentDispute dispute,
      UUID actorEmployeeId,
      UUID ownerEmployeeId,
      boolean viewerIsDirectManager) {
    return toDto(
        dispute, disputeActions(dispute, actorEmployeeId, ownerEmployeeId, viewerIsDirectManager));
  }

  private AlignmentDisputeDto toDto(AlignmentDispute dispute, List<AllowedAction> allowedActions) {
    return new AlignmentDisputeDto(
        dispute.getId(),
        dispute.getCommitmentId(),
        dispute.getManagerEmployeeId(),
        dispute.getStatus(),
        dispute.getFlagType(),
        dispute.getManagerNote(),
        dispute.getIcResponse(),
        dispute.getResolvedAt(),
        allowedActions,
        dispute.getVersion());
  }

  /**
   * The per-viewer dispute affordances (task 5.5b) — the boolean parallels of the E18/E19
   * authorizers (void-and-throw, so mirrored not shared, §31): RESPOND = owning IC ∧ {@code OPEN};
   * RESOLVE = direct manager ∧ {@code OPEN}|{@code IC_RESPONDED}. A {@code RESOLVED} dispute yields
   * neither (and is never nested anyway).
   */
  private static List<AllowedAction> disputeActions(
      AlignmentDispute dispute,
      UUID actorEmployeeId,
      UUID ownerEmployeeId,
      boolean viewerIsDirectManager) {
    List<AllowedAction> actions = new ArrayList<>();
    if (actorEmployeeId.equals(ownerEmployeeId) && dispute.getStatus() == DisputeStatus.OPEN) {
      actions.add(AllowedAction.RESPOND_DISPUTE); // E18: owning IC ∧ OPEN
    }
    if (viewerIsDirectManager
        && (dispute.getStatus() == DisputeStatus.OPEN
            || dispute.getStatus() == DisputeStatus.IC_RESPONDED)) {
      actions.add(AllowedAction.RESOLVE_DISPUTE); // E19: direct manager ∧ OPEN|IC_RESPONDED
    }
    return List.copyOf(actions);
  }
}
