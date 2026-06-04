package com.st6.wc.worker.sync;

import java.util.UUID;

/**
 * Thrown by the real {@link GraphCalendarAdapter} (and the fail-safe {@link
 * DegradedGraphCalendarPort}) when a calendar create cannot complete — an unresolved owner, a
 * missing-credential degrade, or a Graph API error. Like {@link SyncProcessingException} (§44) it
 * is <strong>cause-less by construction</strong> (there is NO {@code (…, Throwable)} overload) and
 * its message is <strong>ids-only</strong> — so a raw Graph error (which may carry a calendar body
 * / attendee email / token, rule #7) can never ride a cause-chain or the message into a log. {@link
 * SyncMessageListener} catches it, records a fixed non-PII {@code failureCode}/{@code safeMessage},
 * and redrives the message to the DLQ.
 */
public class GraphCalendarException extends RuntimeException {

  public GraphCalendarException(UUID syncRecordId) {
    super("Graph calendar create failed for syncRecord=" + syncRecordId);
  }
}
