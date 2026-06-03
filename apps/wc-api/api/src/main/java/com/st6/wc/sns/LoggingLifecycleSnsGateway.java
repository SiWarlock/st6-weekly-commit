package com.st6.wc.sns;

import com.st6.wc.sns.payload.SyncJobPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The default {@link LifecycleSnsGateway} for task 3.5 — logs the (pointer-only, safe) publish and
 * succeeds, so the lock transaction's outbox write + {@code PENDING_PUBLISH → QUEUED} transition
 * are exercisable without live SNS. The real AWS-SNS gateway (Phase 12) replaces this bean; tests
 * override it via {@code @MockBean}. Logs only ids/state (rule #7 — no bodies/secrets/PII).
 */
@Component
public class LoggingLifecycleSnsGateway implements LifecycleSnsGateway {

  private static final Logger log = LoggerFactory.getLogger(LoggingLifecycleSnsGateway.class);

  @Override
  public void publish(SyncJobPointer pointer) {
    log.info(
        "lifecycle SNS publish (stub): syncRecordId={} eventKind={} env={}",
        pointer.syncRecordId(),
        pointer.eventKind(),
        pointer.env());
  }
}
