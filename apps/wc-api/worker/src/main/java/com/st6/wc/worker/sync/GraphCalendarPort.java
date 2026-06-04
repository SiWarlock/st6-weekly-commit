package com.st6.wc.worker.sync;

import com.st6.wc.sync.OutlookCalendarSyncRecord;

/**
 * The Microsoft Graph calendar publish seam (Wave-2 s8, §10) — abstracts the actual calendar op so
 * the {@link SyncMessageListener} depends only on this interface. A {@code GRAPH_MODE=demo-success}
 * stub ({@link DemoSuccessGraphCalendarPort}) ships in s8; the real Graph adapter swaps in behind
 * the SAME port at s9 (the no-op-seam-then-real pattern, LESSONS §43). An implementation that
 * throws is caught by the listener, which records {@code FAILED} + rethrows a sanitized exception
 * (the single redrive point — never leaks the raw Graph error, rule #7).
 */
public interface GraphCalendarPort {

  /** Creates the calendar event for the record and returns the resulting Graph event id. */
  String createEvent(OutlookCalendarSyncRecord record);
}
