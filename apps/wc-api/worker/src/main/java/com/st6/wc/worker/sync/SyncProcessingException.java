package com.st6.wc.worker.sync;

import java.util.UUID;

/**
 * Thrown by {@link SyncMessageListener} after a sync failure is recorded, to trigger SQS redrive →
 * DLQ. The message is <strong>ids-only</strong> and there is <strong>no cause-chain</strong> — so
 * the SQS framework's redrive logging never surfaces the raw Graph error (which may carry a
 * calendar body / attendee email / token, rule #7). The persisted {@code failureCode}/{@code
 * safeMessage} carry the (already non-PII) detail; this exception exists only to fail the message.
 */
public class SyncProcessingException extends RuntimeException {

  public SyncProcessingException(UUID syncRecordId) {
    super("Sync processing failed for syncRecord=" + syncRecordId);
  }
}
