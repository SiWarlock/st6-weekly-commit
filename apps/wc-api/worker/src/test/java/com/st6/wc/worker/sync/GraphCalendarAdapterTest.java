package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof for the real-mode {@link GraphCalendarAdapter} (Wave-2 s9, §10 / rules #4 + #7). The
 * adapter resolves the owner's email ({@link EmployeeRepository}), builds a
 * <strong>non-PII</strong> {@link CalendarEventSpec} (a human subject + the §10 deep-link body from
 * {@code eventKind} + week + planId + the frontend-base-url config — never a calendar body / OKR /
 * commitment / owner name / email), delegates the actual create to the mockable {@link
 * GraphEventGateway} seam (the §43 {@code *Operations}-injection pattern → <strong>no live Graph
 * call in tests</strong>), and on ANY gateway error throws a clean, cause-less, ids-only {@link
 * GraphCalendarException} (so s8's {@code SyncMessageListener} sanitizes → {@code FAILED}, never
 * leaking the raw Graph error — the §44 teeth).
 */
class GraphCalendarAdapterTest {

  private static final String BASE_URL = "https://wc.test.example";
  private static final UUID PLAN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  private final GraphEventGateway gateway = mock(GraphEventGateway.class);
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final ZoneId zone = ZoneId.of("America/Chicago");
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), zone);
  private final GraphCalendarAdapter adapter =
      new GraphCalendarAdapter(gateway, employees, clock, BASE_URL);

  private static OutlookCalendarSyncRecord record(UUID ownerId) {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(ownerId);
    r.setEventKind(EventKind.IC_PLANNING);
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(PLAN_ID); // = planId for IC events (the deep-link target)
    r.setStatus(SyncStatus.SYNCING);
    r.setWeekStartDate(LocalDate.of(2026, 6, 8)); // a Monday → week "Jun 8–14"
    return r;
  }

  private static Employee employee(UUID id, String email) {
    return employee(id, email, "Test Owner");
  }

  private static Employee employee(UUID id, String email, String displayName) {
    Employee e = new Employee();
    e.setId(id);
    e.setEmail(email);
    e.setDisplayName(displayName);
    e.setRole(RoleType.IC);
    e.setActive(true);
    return e;
  }

  // --- RED #1 + #2: resolves owner email, builds a non-PII spec, returns the real graph id
  // --------
  @Test
  void realPath_resolvesOwnerEmail_buildsNonPiiSpec_returnsGraphId() {
    UUID ownerId = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(ownerId);
    when(employees.findById(ownerId))
        .thenReturn(Optional.of(employee(ownerId, "owner@contoso.com")));
    when(gateway.createEvent(eq("owner@contoso.com"), any(CalendarEventSpec.class)))
        .thenReturn("AAMkAGI-evt-123");

    String id = adapter.createEvent(r);

    assertThat(id).isEqualTo("AAMkAGI-evt-123");
    ArgumentCaptor<CalendarEventSpec> specCaptor = ArgumentCaptor.forClass(CalendarEventSpec.class);
    // RED #2 — the Graph call is addressed to the OWNER's mailbox (resolved from Employee.email).
    verify(gateway).createEvent(eq("owner@contoso.com"), specCaptor.capture());
    CalendarEventSpec spec = specCaptor.getValue();
    // brief 106 — a human subject + the §10 deep-link body to the plan-history view; rule #7 —
    // neither carries the owner name/email (the email is only the mailbox ADDRESS above), OKR, or
    // commitment text.
    assertThat(spec.subject())
        .isEqualTo("Weekly Commit — Week of Jun 8–14")
        .doesNotContain("owner@contoso.com", "Test Owner");
    assertThat(spec.body())
        .contains(BASE_URL + "/weekly-commit/history/" + PLAN_ID)
        .doesNotContain("owner@contoso.com", "Test Owner");
    assertThat(spec.recordId()).isEqualTo(r.getId());
    // the event lands on the record's TARGET week (not "now") resolved in the org zone.
    assertThat(spec.start()).isNotNull();
    assertThat(spec.start().atZone(zone).toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 8));
    assertThat(spec.end()).isAfter(spec.start());
  }

  // --- edge: a vanished/unknown owner is a SAFE failure (clean throw), never a PII leak / crash
  // ---
  @Test
  void missingEmployee_throwsCleanNoLeak() {
    UUID ownerId = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(ownerId);
    when(employees.findById(ownerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.createEvent(r))
        .isInstanceOf(GraphCalendarException.class)
        .hasNoCause()
        .hasMessageContaining(r.getId().toString());
    verifyNoInteractions(gateway); // never addressed a Graph call without a resolved mailbox
  }

  // --- RED #4: a Graph error carrying PII is sanitized — adapter throws clean, cause-less, no PII
  // --
  @Test
  void graphError_isSanitizedToCleanNonPiiException() {
    UUID ownerId = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(ownerId);
    when(employees.findById(ownerId))
        .thenReturn(Optional.of(employee(ownerId, "john.doe@acme.com")));
    // the raw Graph error carries an attendee email + OKR/commitment text + a status code (rule
    // #7).
    when(gateway.createEvent(eq("john.doe@acme.com"), any(CalendarEventSpec.class)))
        .thenThrow(new RuntimeException("Graph 403: john.doe@acme.com event 'Q3 OKRs' rejected"));

    // mirror the §44 teeth: clean type, NO cause-chain (so s8's redrive logging can't surface the
    // raw error), and a message with NONE of the PII tokens.
    assertThatThrownBy(() -> adapter.createEvent(r))
        .isInstanceOf(GraphCalendarException.class)
        .hasNoCause()
        // Exact-message (deploy-fix flaky #102): asserting the WHOLE ids-only message is
        // strictly stronger than the three token-absence checks — it proves the message carries
        // nothing from the raw Graph error (no email, OKR text, or status code) and is
        // deterministic for ANY id. notContaining("403") flaked ~0.5% of runs when a random
        // record-id UUID's hex contained "403"; exact-message is id-agnostic, so the seed stays
        // UUID.randomUUID().
        .hasMessage("Graph calendar create failed for syncRecord=" + r.getId());
  }

  // --- brief-106 rule-#7 leak teeth: SENTINEL owner name+email NEVER reach the subject/body
  // -------
  @Test
  void subjectAndBody_neverCarryOwnerPii() {
    UUID ownerId = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(ownerId);
    // the owner is seeded with sentinel PII — the email is only the mailbox ADDRESS, and the worker
    // loads NO commitment/SO/OKR text; none of it may appear in the event subject or body (rule
    // #7).
    when(employees.findById(ownerId))
        .thenReturn(
            Optional.of(employee(ownerId, "sentinel.owner@pii.example", "Sentinel Lastname")));
    when(gateway.createEvent(eq("sentinel.owner@pii.example"), any(CalendarEventSpec.class)))
        .thenReturn("evt-1");

    adapter.createEvent(r);

    ArgumentCaptor<CalendarEventSpec> cap = ArgumentCaptor.forClass(CalendarEventSpec.class);
    verify(gateway).createEvent(eq("sentinel.owner@pii.example"), cap.capture());
    CalendarEventSpec spec = cap.getValue();
    assertThat(spec.subject() + " " + spec.body())
        .doesNotContain("sentinel.owner@pii.example", "Sentinel Lastname")
        .contains("Weekly Commit", BASE_URL + "/weekly-commit/history/" + PLAN_ID);
  }

  // --- brief-106 review-block: "Manager Review" subject + the command-center deep-link -----------
  @Test
  void reviewBlock_humanSubjectAndCommandCenterLink() {
    UUID ownerId = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(ownerId);
    r.setEventKind(EventKind.MANAGER_REVIEW_BLOCK);
    r.setRelatedType(SyncRelatedType.MANAGER_REVIEW_WEEK);
    when(employees.findById(ownerId)).thenReturn(Optional.of(employee(ownerId, "mgr@contoso.com")));
    when(gateway.createEvent(eq("mgr@contoso.com"), any(CalendarEventSpec.class)))
        .thenReturn("evt-r");

    adapter.createEvent(r);

    ArgumentCaptor<CalendarEventSpec> cap = ArgumentCaptor.forClass(CalendarEventSpec.class);
    verify(gateway).createEvent(eq("mgr@contoso.com"), cap.capture());
    CalendarEventSpec spec = cap.getValue();
    assertThat(spec.subject()).isEqualTo("Manager Review — Week of Jun 8–14");
    assertThat(spec.body()).contains(BASE_URL + "/manager/command-center");
  }
}
