package com.st6.wc.commitment;

import com.st6.wc.commitment.dto.CreateCommitmentRequest;
import com.st6.wc.commitment.dto.CreateUnplannedCommitmentRequest;
import com.st6.wc.commitment.dto.PatchCommitmentRequest;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.identity.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commitment write endpoints (task 3.4a/3.4b, §5 / §6 rule #3). Thin controllers — {@code @Valid}
 * runs the Appendix-E field validation → 400 on violation; the service authorizes first (the
 * chokepoint, codeless 404 on a cross-owner/missing resource) then applies the state rules.
 *
 * <ul>
 *   <li>{@code POST /api/plans/{id}/commitments} (E5) → 201 created {@link WeeklyCommitmentDto};
 *   <li>{@code POST /api/plans/{id}/unplanned-commitments} (E11) → 201 UNPLANNED commitment
 *       (owner-only, plan {@code LOCKED}/{@code RECONCILING}, server-forced kind/work-type);
 *   <li>{@code PATCH /api/commitments/{id}} (E6) → 200 updated DTO (owner-only, DRAFT-gated
 *       baseline + read-only {@code alignmentStatus} post-lock + RECONCILING outcome recording);
 *   <li>{@code DELETE /api/commitments/{id}} (E7) → 204 (owner-only, DRAFT-only).
 * </ul>
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

  @PostMapping("/api/plans/{id}/unplanned-commitments")
  @ResponseStatus(HttpStatus.CREATED)
  public WeeklyCommitmentDto createUnplanned(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("id") UUID planId,
      @Valid @RequestBody CreateUnplannedCommitmentRequest request) {
    return commitmentService.createUnplanned(principal, planId, request);
  }

  @PatchMapping("/api/commitments/{id}")
  public WeeklyCommitmentDto update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("id") UUID commitmentId,
      @RequestBody PatchCommitmentRequest request) {
    // No @Valid: PatchCommitmentRequest carries no bean constraints — partial-update validation +
    // normalization live in the service (reusing TextNormalizer, uniform with E5, on present
    // fields).
    return commitmentService.update(principal, commitmentId, request);
  }

  @DeleteMapping("/api/commitments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable("id") UUID commitmentId) {
    commitmentService.discard(principal, commitmentId);
  }
}
