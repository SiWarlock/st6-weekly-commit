package com.st6.wc.dispute.mapper;

import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link AlignmentDispute} entity to its {@link AlignmentDisputeDto} (task 5.3, Appendix
 * B.8). {@code allowedActions} is empty in this slice — the RESPOND/RESOLVE affordances are emitted
 * by the slices that enforce them (5.4/5.5), under "no affordance without enforcement" (§15/§24).
 */
@Component
public class DisputeMapper {

  public AlignmentDisputeDto toDto(AlignmentDispute dispute) {
    return new AlignmentDisputeDto(
        dispute.getId(),
        dispute.getCommitmentId(),
        dispute.getManagerEmployeeId(),
        dispute.getStatus(),
        dispute.getFlagType(),
        dispute.getManagerNote(),
        dispute.getIcResponse(),
        dispute.getResolvedAt(),
        List.of(),
        dispute.getVersion());
  }
}
