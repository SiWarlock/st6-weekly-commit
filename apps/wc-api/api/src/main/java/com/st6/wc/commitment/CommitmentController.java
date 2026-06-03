package com.st6.wc.commitment;

import com.st6.wc.commitment.dto.CreateCommitmentRequest;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.identity.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/plans/{id}/commitments} (E5, task 3.4a, §5 / §6 rule #3). Thin: {@code @Valid}
 * runs the Appendix-E field validation on the (constructor-normalized) request → 400 on violation;
 * the service authorizes the parent plan first (the chokepoint) → codeless 404 on a cross-owner/
 * missing plan; on success → 201 with the created {@link WeeklyCommitmentDto}.
 */
@RestController
public class CommitmentController {

  private final CommitmentService commitmentService;

  public CommitmentController(CommitmentService commitmentService) {
    this.commitmentService = commitmentService;
  }

  @PostMapping("/api/plans/{id}/commitments")
  @ResponseStatus(HttpStatus.CREATED)
  public WeeklyCommitmentDto create(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("id") UUID planId,
      @Valid @RequestBody CreateCommitmentRequest request) {
    return commitmentService.create(principal, planId, request);
  }
}
