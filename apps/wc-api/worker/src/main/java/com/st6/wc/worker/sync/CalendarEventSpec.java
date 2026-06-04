package com.st6.wc.worker.sync;

import java.time.Instant;
import java.util.UUID;

/**
 * The non-PII calendar-event spec the {@link GraphCalendarAdapter} hands to the {@link
 * GraphEventGateway} seam (Wave-2 s9, rule #7). Carries only a synthesized {@code subject} (derived
 * from the sync record's {@code eventKind} + week — never a calendar body / OKR / commitment text),
 * the event window, and the {@code recordId} (for ids-only logging). Keeps the Microsoft Graph SDK
 * types out of the adapter and its unit tests — only {@link MsGraphEventGateway} translates this to
 * a Graph {@code Event}.
 */
public record CalendarEventSpec(String subject, Instant start, Instant end, UUID recordId) {}
