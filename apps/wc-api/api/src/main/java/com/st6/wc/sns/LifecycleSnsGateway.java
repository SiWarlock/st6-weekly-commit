package com.st6.wc.sns;

import com.st6.wc.sns.payload.SyncJobPointer;

/**
 * The SNS publish seam (task 3.5, §10) — abstracts the actual pointer publish so the lock
 * transaction's post-commit hook ({@link SnsLifecyclePublisher}) depends only on this interface.
 * The real AWS SNS implementation lands in Phase 12 (infra); 3.5 ships {@link
 * LoggingLifecycleSnsGateway} (a logging no-op success) so the lock + its outbox write are
 * exercisable without live SNS. A {@code publish} that throws is caught by the publisher and never
 * blocks the lock (rule #4).
 */
public interface LifecycleSnsGateway {

  void publish(SyncJobPointer pointer);
}
