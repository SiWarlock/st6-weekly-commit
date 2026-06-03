package com.st6.wc.dispute;

import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.dispute.dto.OpenDisputeRequest;
import com.st6.wc.identity.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/commitments/{id}/disputes} (E17, §5 / §6). Thin: {@link DisputeService}
 * authorizes manager-of-owner-only first (the chokepoint), so an IC-self → 403, a
 * non-direct-manager/missing → codeless 404. The {@code @AuthenticationPrincipal} is the
 * 2.6-resolved caller. Returns the created {@link AlignmentDisputeDto} (B.8) with {@code 201}.
 */
@RestController
public class DisputeController {

  private final DisputeService disputeService;

  public DisputeController(DisputeService disputeService) {
    this.disputeService = disputeService;
  }

  @PostMapping("/api/commitments/{id}/disputes")
  public ResponseEntity<AlignmentDisputeDto> open(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("id") UUID commitmentId,
      @Valid @RequestBody OpenDisputeRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(disputeService.open(principal, commitmentId, req));
  }
}
