package com.st6.wc.sns;

import com.st6.wc.sns.payload.SyncJobPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The fallback {@link LifecycleSnsGateway} (task 3.5; Wave-2 s7) — logs the (pointer-only, safe)
 * publish and succeeds, so the lock transaction's outbox write + {@code PENDING_PUBLISH → QUEUED}
 * transition are exercisable without live SNS (local/demo/test). The real {@link
 * AwsSnsLifecycleGateway} takes over when {@code app.sns.topic-arn} is configured (deployed {@code
 * aws}); this stub is active when it is NOT (mutually-exclusive property-conditional selection —
 * order-independent, unlike {@code @ConditionalOnMissingBean} on a component). Tests override it
 * via {@code @MockBean}. Logs only ids/state (rule #7 — no bodies/secrets/PII).
 */
@Component
@ConditionalOnProperty(name = "app.sns.topic-arn", havingValue = "false", matchIfMissing = true)
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
