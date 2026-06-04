package com.st6.wc.sns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.st6.wc.enums.EventKind;
import com.st6.wc.sns.payload.SyncJobPointer;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsOperations;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof for the real {@link AwsSnsLifecycleGateway} (Wave-2 s7, §10 / rules #4, #7). Publishes
 * the pointer-only {@link SyncJobPointer} to the configured topic via {@link SnsTemplate}, and
 * <strong>propagates</strong> on failure — the single rule-#4 swallow point stays {@code
 * SnsLifecyclePublisher} (a gateway-internal catch would hide failures from the {@code
 * PENDING_PUBLISH}-retry posture).
 */
class SnsLifecycleGatewayTest {

  private static final String ARN = "arn:aws:sns:us-east-1:123456789012:wc-lifecycle";

  private final SnsOperations sns = mock(SnsOperations.class);
  private final LifecycleSnsGateway gateway = new AwsSnsLifecycleGateway(sns, ARN);

  // --- publishes ONLY the pointer to the configured topic (rule #7 — no enrichment) ----
  @Test
  void publishesPointerToTopic() {
    SyncJobPointer pointer =
        new SyncJobPointer(UUID.randomUUID(), EventKind.IC_PLANNING, "aws", "trace-1");

    gateway.publish(pointer);

    // the notification payload IS exactly the pointer (Jackson-serialized to the configured ARN);
    // NOTHING else crosses the wire (rule #7 — pointer-only, no enrichment).
    @SuppressWarnings("unchecked")
    ArgumentCaptor<SnsNotification<SyncJobPointer>> captor =
        ArgumentCaptor.forClass(SnsNotification.class);
    verify(sns).sendNotification(eq(ARN), captor.capture());
    assertThat(captor.getValue().getPayload()).isEqualTo(pointer);
    assertThat(captor.getValue().getSubject())
        .as("fixed non-PII subject (rule #7 — no input-derived metadata)")
        .isEqualTo("wc-lifecycle-sync");
    verifyNoMoreInteractions(sns);
  }

  // --- propagates on SNS failure (rule #4 — the publisher is the single swallow point) ----
  @Test
  void propagatesOnSnsFailure() {
    SyncJobPointer pointer =
        new SyncJobPointer(UUID.randomUUID(), EventKind.IC_PLANNING, "aws", "trace-1");
    doThrow(new RuntimeException("SNS unavailable"))
        .when(sns)
        .sendNotification(anyString(), any(SnsNotification.class));

    assertThatThrownBy(() -> gateway.publish(pointer)).isInstanceOf(RuntimeException.class);
  }
}
