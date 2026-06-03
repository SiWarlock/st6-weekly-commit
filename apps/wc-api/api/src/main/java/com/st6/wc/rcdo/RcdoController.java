package com.st6.wc.rcdo;

import com.st6.wc.rcdo.dto.RcdoTreeDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/rcdo} (task 3.1, §5 E2 / Appendix B.4) — returns the full nested read-only RCDO
 * hierarchy. <strong>Org-wide read</strong>: RCDO is shared reference data, not a per-user
 * resource, so this carries only the coarse authn gate (the 2.6 chain stops unauthenticated
 * requests with a 401 problem+json before they reach here) and makes <strong>no {@code
 * DomainAuthorizationService} call</strong> — same posture as {@code GET /api/me}. Read-only: there
 * is no create/update/delete RCDO mapping anywhere (REQ-D-003). Returns a DTO, never an entity
 * (forbidden-pattern #3).
 */
@RestController
public class RcdoController {

  private final RcdoReadService rcdoReadService;

  public RcdoController(RcdoReadService rcdoReadService) {
    this.rcdoReadService = rcdoReadService;
  }

  @GetMapping("/api/rcdo")
  public RcdoTreeDto rcdo() {
    return rcdoReadService.getRcdoTree();
  }
}
