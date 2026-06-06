package com.st6.wc.worker.sync;

import java.time.Instant;
import java.util.UUID;

/**
 * The non-PII calendar-event spec the {@link GraphCalendarAdapter} hands to the {@link
 * GraphEventGateway} seam (Wave-2 s9, rule #7). Carries a synthesized {@code subject} (a human
 * label from {@code eventKind} + week) and a {@code body} (a generic line + the §10 deep-link URL —
 * brief 106 / ARCH:1012), the event window, and the {@code recordId} (for ids-only logging).
 * <strong>Rule #7:</strong> NEITHER subject NOR body carries a calendar body / OKR / commitment /
 * owner name / email — only a generic label, the week, the {@code frontend-base-url} config, and
 * the planId UUID. Keeps the Microsoft Graph SDK types out of the adapter and its unit tests — only
 * {@link MsGraphEventGateway} translates this to a Graph {@code Event}.
 */
public record CalendarEventSpec(
    String subject, String body, Instant start, Instant end, UUID recordId) {}
