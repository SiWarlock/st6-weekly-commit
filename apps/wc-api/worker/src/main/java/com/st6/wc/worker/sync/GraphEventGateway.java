package com.st6.wc.worker.sync;

/**
 * The seam over the actual Microsoft Graph calendar-create call (Wave-2 s9 — the §43 {@code
 * *Operations}-injection idiom). {@link GraphCalendarAdapter} depends only on this interface, so
 * its unit tests mock it (no live Graph call in tests); the lone SDK-touching implementation is
 * {@link MsGraphEventGateway}.
 */
public interface GraphEventGateway {

  /** Creates the calendar event on {@code userEmail}'s calendar; returns the Graph event id. */
  String createEvent(String userEmail, CalendarEventSpec spec);
}
