package com.st6.wc.worker.sync;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.EventKind;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
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

  /** Human "MMM d" week-range formatter (e.g. "Jun 1"); en-locale, deterministic. */
  private static final DateTimeFormatter WEEK_DAY =
      DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

  private final GraphEventGateway gateway;
  private final EmployeeRepository employees;
  private final Clock clock;

  /**
   * The frontend deep-link base (brief 106 / ARCH:1012 — {@code app.outlook.frontend-base-url} /
   * {@code WC_FRONTEND_BASE_URL}, e.g. {@code https://wc.<root-domain>}). Config-trusted; combined
   * with a fixed path + the planId UUID into the event body. No PII (rule #7).
   */
  private final String frontendBaseUrl;

  public GraphCalendarAdapter(
      GraphEventGateway gateway,
      EmployeeRepository employees,
      Clock clock,
      String frontendBaseUrl) {
    this.gateway = gateway;
    this.employees = employees;
    this.clock = clock;
    this.frontendBaseUrl = frontendBaseUrl;
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
    // rule #7 — subject + body derive from eventKind + week + relatedId (the planId UUID) + the
    // config base-url ONLY (no owner name / email / OKR / commitment text); the sync record carries
    // no free text, so nothing PII can reach Graph.
    String subject = humanSubject(record.getEventKind(), record.getWeekStartDate());
    String body = deepLinkBody(record.getEventKind(), record.getRelatedId());
    return new CalendarEventSpec(subject, body, start, end, record.getId());
  }

  /** Human, presentable subject per {@code eventKind} (brief 106) — generic label + week range. */
  private static String humanSubject(EventKind kind, LocalDate weekStart) {
    String range = weekRange(weekStart);
    return switch (kind) {
      case IC_PLANNING -> "Weekly Commit — Week of " + range;
      case IC_RECONCILIATION -> "Weekly Commit — Reconciliation — Week of " + range;
      case MANAGER_REVIEW_BLOCK -> "Manager Review — Week of " + range;
    };
  }

  /** "Jun 1–7" (same month) or "Jun 29 – Jul 5" (cross-month) for the Mon..Sun week. */
  private static String weekRange(LocalDate weekStart) {
    LocalDate weekEnd = weekStart.plusDays(6);
    if (weekStart.getMonth() == weekEnd.getMonth()) {
      return WEEK_DAY.format(weekStart) + "–" + weekEnd.getDayOfMonth();
    }
    return WEEK_DAY.format(weekStart) + " – " + WEEK_DAY.format(weekEnd);
  }

  /**
   * The §10 deep-link body (brief 106 / ARCH:1012): a generic line + the frontend URL. IC → the
   * plan-history view ({@code /weekly-commit/history/{planId}}, relatedId = planId); review-block →
   * the manager command center. rule #7 — generic label + a {@code {base}}-config URL + the planId
   * UUID ONLY (no owner/commitment/OKR text); the URL is HTML-safe (config base + a UUID path).
   */
  private String deepLinkBody(EventKind kind, UUID relatedId) {
    String link;
    String lead;
    if (kind == EventKind.MANAGER_REVIEW_BLOCK) {
      link = frontendBaseUrl + "/manager/command-center";
      lead = "Open the manager command center";
    } else {
      link = frontendBaseUrl + "/weekly-commit/history/" + relatedId;
      lead = "Open your weekly plan";
    }
    return "<p>" + lead + ": <a href=\"" + link + "\">" + link + "</a></p>";
  }
}
