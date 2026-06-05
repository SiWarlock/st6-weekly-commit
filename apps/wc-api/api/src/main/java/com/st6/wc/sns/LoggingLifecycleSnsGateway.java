package com.st6.wc.sns;

import com.st6.wc.sns.payload.SyncJobPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * The fallback {@link LifecycleSnsGateway} (task 3.5; Wave-2 s7) — logs the (pointer-only, safe)
 * publish and succeeds, so the lock transaction's outbox write + {@code PENDING_PUBLISH → QUEUED}
 * transition are exercisable without live SNS (local/demo/test). The real {@link
 * AwsSnsLifecycleGateway} takes over when {@code app.sns.topic-arn} is non-empty (deployed API);
 * this stub is active when it is <strong>empty or absent</strong> — local/demo/test AND the
 * aws-profile Jobs that never receive {@code SNS_TOPIC_ARN} (the empty default). The condition is
 * the exact inverse of the real gateway's ({@code length() == 0}) over the empty-defaulted
 * placeholder — so exactly one bean activates and an aws Job lacking the env boots cleanly instead
 * of crashing on an unresolvable placeholder (order-independent, unlike
 * {@code @ConditionalOnMissingBean} on a component). Tests override it via {@code @MockBean}. Logs
 * only ids/state (rule #7 — no bodies/secrets/PII).
 */
@Component
@ConditionalOnExpression("'${app.sns.topic-arn:}'.length() == 0")
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
