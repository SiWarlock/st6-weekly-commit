package com.st6.wc.me;

import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.me.dto.MeDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/me} — the first real {@code /api/**} endpoint (task 2.7, §5 E1). Thin controller:
 * the 2.6 chain has already authenticated the caller into a {@link UserPrincipal} in the {@code
 * SecurityContext}; this returns the caller's OWN identity, so it carries only the coarse authn
 * gate — <strong>no {@code DomainAuthorizationService} call</strong> (self by definition).
 * Unauthenticated requests are stopped by the chain (401 problem+json) before reaching here.
 */
@RestController
public class MeController {

  private final MeService meService;

  public MeController(MeService meService) {
    this.meService = meService;
  }

  @GetMapping("/api/me")
  public MeDto me(@AuthenticationPrincipal UserPrincipal principal) {
    return meService.toMeDto(principal);
  }
}
