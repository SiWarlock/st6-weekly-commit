package com.st6.wc.worker.sync;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real {@code GRAPH_MODE=real} {@link GraphCalendarPort} (Wave-2 s9, §10 / rules #4 + #7).
 * Resolves the owner's mailbox ({@code ownerEmployeeId → Employee.email}), builds a non-PII {@link
 * CalendarEventSpec} for the record's target week, and creates the event via the {@link
 * GraphEventGateway} seam — returning the real Graph event id to {@link SyncMessageListener}.
 *
 * <p><strong>Rule #4 / #7:</strong> an unresolved owner OR any Graph error is converted to a clean,
 * cause-less, ids-only {@link GraphCalendarException} — the raw Graph error (calendar body /
 * attendee / token) is NEVER logged, persisted, or chained. The listener records a fixed non-PII
 * {@code FAILED} + redrives; the core lifecycle is never blocked.
 *
 * <p>Selected by {@link GraphRealModeConfig} only when {@code app.graph.mode=real} AND all {@code
 * GRAPH_*} creds are present (otherwise the fail-safe {@link DegradedGraphCalendarPort} is wired).
 */
public class GraphCalendarAdapter implements GraphCalendarPort {

  private static final Logger log = LoggerFactory.getLogger(GraphCalendarAdapter.class);

  /** The org-day start for the synthesized weekly event block (fixed, non-PII). */
  private static final LocalTime EVENT_START = LocalTime.of(9, 0);

  private static final long EVENT_DURATION_SECONDS = 30L * 60L;

  private final GraphEventGateway gateway;
  private final EmployeeRepository employees;
  private final Clock clock;

  public GraphCalendarAdapter(
      GraphEventGateway gateway, EmployeeRepository employees, Clock clock) {
    this.gateway = gateway;
    this.employees = employees;
    this.clock = clock;
  }

  @Override
  public String createEvent(OutlookCalendarSyncRecord record) {
    Employee owner =
        employees
            .findById(record.getOwnerEmployeeId())
            .orElseThrow(() -> new GraphCalendarException(record.getId()));

    CalendarEventSpec spec = buildSpec(record);
    try {
      return gateway.createEvent(owner.getEmail(), spec);
    } catch (RuntimeException e) {
      // rule #7 — the raw Graph error is neither logged, persisted, nor chained; only the ids-only,
      // cause-less exception escapes (SyncMessageListener sanitizes further + redrives to the DLQ).
      log.warn("graph calendar create failed for syncRecord={}", record.getId());
      throw new GraphCalendarException(record.getId());
    }
  }

  private CalendarEventSpec buildSpec(OutlookCalendarSyncRecord record) {
    Instant start =
        record.getWeekStartDate().atTime(EVENT_START).atZone(clock.getZone()).toInstant();
    Instant end = start.plusSeconds(EVENT_DURATION_SECONDS);
    // rule #7 — the subject is derived from eventKind + week ONLY (no owner name / email / OKR /
    // commitment text); the sync record carries no free text, so nothing PII can reach Graph.
    String subject = record.getEventKind() + " — week of " + record.getWeekStartDate();
    return new CalendarEventSpec(subject, start, end, record.getId());
  }
}
