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
 * <strong>non-PII</strong> {@link CalendarEventSpec} (subject from {@code eventKind} + week only —
 * never a calendar body / OKR / commitment text), delegates the actual create to the mockable
 * {@link GraphEventGateway} seam (the §43 {@code *Operations}-injection pattern → <strong>no live
 * Graph call in tests</strong>), and on ANY gateway error throws a clean, cause-less, ids-only
 * {@link GraphCalendarException} (so s8's {@code SyncMessageListener} sanitizes → {@code FAILED},
 * never leaking the raw Graph error — the §44 teeth).
 */
class GraphCalendarAdapterTest {

  private final GraphEventGateway gateway = mock(GraphEventGateway.class);
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final ZoneId zone = ZoneId.of("America/Chicago");
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), zone);
  private final GraphCalendarAdapter adapter = new GraphCalendarAdapter(gateway, employees, clock);

  private static OutlookCalendarSyncRecord record(UUID ownerId) {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(ownerId);
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.SYNCING);
    r.setWeekStartDate(LocalDate.of(2026, 6, 8)); // a Monday
    return r;
  }

  private static Employee employee(UUID id, String email) {
    Employee e = new Employee();
    e.setId(id);
    e.setEmail(email);
    e.setDisplayName("Test Owner");
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
    // rule #7 — the spec subject is derived from eventKind + week ONLY (no name / email / OKR
    // text).
    assertThat(spec.subject())
        .contains("2026-06-08")
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
        .hasMessageNotContaining("john.doe@acme.com")
        .hasMessageNotContaining("Q3 OKRs")
        .hasMessageNotContaining("403");
  }
}
