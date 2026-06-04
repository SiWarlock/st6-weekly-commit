package com.st6.wc.sync;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.sync.dto.OutlookSyncRecordDto;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link OutlookCalendarSyncRecord} entity to its B.10 {@link OutlookSyncRecordDto}
 * (E22/E23, §10/§21). Emits the {@code RETRY_SYNC} affordance iff {@link
 * AllowedActionResolver#canRetrySync} (status == {@code FAILED}) — the SAME predicate {@code
 * SyncRetryService} enforces, so the affordance and the enforcement never drift (§31). Never
 * returns the entity across the boundary (forbidden-pattern #3 — internal timestamps leak-tested
 * out).
 */
@Component
public class SyncRecordMapper {

  private final AllowedActionResolver resolver;

  public SyncRecordMapper(AllowedActionResolver resolver) {
    this.resolver = resolver;
  }

  public OutlookSyncRecordDto toDto(OutlookCalendarSyncRecord r) {
    List<AllowedAction> allowedActions =
        resolver.canRetrySync(r.getStatus()) ? List.of(AllowedAction.RETRY_SYNC) : List.of();
    return new OutlookSyncRecordDto(
        r.getId(),
        r.getOwnerEmployeeId(),
        r.getRelatedType(),
        r.getRelatedId(),
        r.getEventKind(),
        r.getWeekStartDate(),
        r.getStatus(),
        r.getGraphEventId(),
        r.getFailureCode(),
        r.getSafeMessage(),
        r.getRetryCount(),
        r.getTraceId(),
        allowedActions,
        r.getVersion());
  }
}
