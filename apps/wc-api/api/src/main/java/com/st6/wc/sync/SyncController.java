package com.st6.wc.sync;

import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.sync.dto.OutlookSyncRecordDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outlook-sync read+retry surface (§5 E22/E23, §10). Thin controller — the owner-only E22 authz,
 * the E23 rule-#3 chokepoint + the §28 afterCommit republish all live in {@link SyncReadService} /
 * {@link SyncRetryService} (controllers never authorize, forbidden-pattern #4). E22 returns a bare
 * array; E23 returns the updated record (200) with {@code status = RETRY_REQUESTED}.
 */
@RestController
public class SyncController {

  private final SyncReadService readService;
  private final SyncRetryService retryService;

  public SyncController(SyncReadService readService, SyncRetryService retryService) {
    this.readService = readService;
    this.retryService = retryService;
  }

  @GetMapping("/api/outlook-sync")
  public List<OutlookSyncRecordDto> list(
      @AuthenticationPrincipal UserPrincipal principal, @RequestParam("planId") UUID planId) {
    return readService.listForPlan(principal, planId);
  }

  @PostMapping("/api/outlook-sync/{syncRecordId}/retry")
  public OutlookSyncRecordDto retry(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("syncRecordId") UUID syncRecordId) {
    return retryService.retry(principal, syncRecordId);
  }
}
