package com.st6.wc.sns;

import com.st6.wc.sns.payload.SyncJobPointer;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * The real AWS {@link LifecycleSnsGateway} (Wave-2 s7, §10 / safety rules #4, #7). Publishes the
 * pointer-only {@link SyncJobPointer} (Jackson-serialized) to the configured SNS topic via {@code
 * SnsOperations} ({@code SnsTemplate}); the worker reloads the durable {@code
 * outlook_calendar_sync_record} by id and reads everything else from the row (rule #7 — no calendar
 * bodies / secrets / notes / PII cross the wire).
 *
 * <p>Active only when {@code app.sns.topic-arn} is <strong>non-empty</strong> (the deployed API —
 * bound from {@code ${SNS_TOPIC_ARN}}); otherwise the {@link LoggingLifecycleSnsGateway} stub is
 * the bean (local/demo/test + the aws-profile Jobs that never receive {@code SNS_TOPIC_ARN}). The
 * selection is a length-based {@code @ConditionalOnExpression} over an
 * <strong>empty-defaulted</strong> placeholder ({@code '${app.sns.topic-arn:}'.length() > 0}) — so
 * an aws-profile context lacking {@code SNS_TOPIC_ARN} boots cleanly (the empty default always
 * resolves) instead of crashing on an unresolvable placeholder, and the two beans stay
 * mutually-exclusive (the stub is the exact inverse, {@code length() == 0}) without component-scan
 * ordering (not {@code @ConditionalOnMissingBean}).
 *
 * <p><strong>Rule #4 — this gateway PROPAGATES on failure.</strong> It does NOT catch: an SNS
 * publish error throws out to {@code SnsLifecyclePublisher}, the single swallow point, which leaves
 * the record {@code PENDING_PUBLISH} (retryable). A catch here would hide failures from the retry
 * posture.
 */
@Component
@ConditionalOnExpression("'${app.sns.topic-arn:}'.length() > 0")
public class AwsSnsLifecycleGateway implements LifecycleSnsGateway {

  /** Fixed, non-PII SNS subject (rule #7 — metadata only; the payload is the pointer record). */
  private static final String SUBJECT = "wc-lifecycle-sync";

  // Depend on the SnsOperations interface, not the concrete SnsTemplate — cleaner DI + avoids the
  // SpotBugs EI_EXPOSE_REP2 false-positive on storing an injected concrete (the project convention
  // is interface injection; the auto-configured SnsTemplate bean injects as SnsOperations).
  private final SnsOperations sns;
  private final String topicArn;

  public AwsSnsLifecycleGateway(SnsOperations sns, @Value("${app.sns.topic-arn}") String topicArn) {
    this.sns = sns;
    this.topicArn = topicArn;
  }

  @Override
  public void publish(SyncJobPointer pointer) {
    // Pointer-only (rule #7) — the SnsNotification payload IS the pointer (Jackson-serialized),
    // with
    // only a fixed non-PII subject. No try/catch — propagate to the publisher's single swallow
    // (#4).
    sns.sendNotification(topicArn, SnsNotification.builder(pointer).subject(SUBJECT).build());
  }
}
