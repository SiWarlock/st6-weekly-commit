package com.st6.wc.sns.payload;

import com.st6.wc.enums.EventKind;
import java.util.UUID;

/**
 * The SNS/SQS lifecycle pointer payload (task 3.5, §10 / safety rule #7) —
 * <strong>pointer-only</strong>: the durable {@code outlook_calendar_sync_record} id + the event
 * kind + env + traceId, and <strong>nothing else</strong> (no calendar bodies, notes, tokens, or
 * PII). The worker loads the record by {@code syncRecordId} and reads everything it needs from the
 * row. Lives in {@code shared} because both {@code :api} (publish) and {@code :worker} (consume)
 * bind it.
 */
public record SyncJobPointer(UUID syncRecordId, EventKind eventKind, String env, String traceId) {}
