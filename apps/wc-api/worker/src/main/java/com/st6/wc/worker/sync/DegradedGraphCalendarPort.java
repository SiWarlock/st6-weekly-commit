package com.st6.wc.worker.sync;

import com.st6.wc.sync.OutlookCalendarSyncRecord;

/**
 * The §10 / REQ-I-004 / REQ-E-007 fail-safe port — selected by {@link GraphRealModeConfig} when
 * {@code app.graph.mode=real} but any {@code GRAPH_*} credential is missing/blank. Rather than
 * crash the worker at boot (a blank client-secret would fail credential construction) or silently
 * mask the misconfiguration as a fake {@code SYNCED} event, it <strong>records a safe
 * failure</strong>: every {@code createEvent} throws a clean, cause-less {@link
 * GraphCalendarException}, so {@link SyncMessageListener} lands the record {@code FAILED} with a
 * fixed non-PII {@code safeMessage} + manual-retry — never blocking the core lifecycle, never
 * logging a secret (Appendix D.6, line 1058). The single safe degrade warning is logged once at
 * selection time by {@link GraphRealModeConfig}, not per message.
 */
public class DegradedGraphCalendarPort implements GraphCalendarPort {

  @Override
  public String createEvent(OutlookCalendarSyncRecord record) {
    throw new GraphCalendarException(record.getId());
  }
}
