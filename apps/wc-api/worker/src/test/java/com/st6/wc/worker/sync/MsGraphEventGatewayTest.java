package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.Event;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Idempotency-key proof for {@link MsGraphEventGateway} (brief 104b). §45 keeps the SDK-touching
 * gateway "live-only" for the actual {@code .post(…)} round-trip, but the {@code transactionId} is
 * a safety-critical idempotency key (it's what makes a reaper re-fire NOT duplicate the calendar
 * event), so it gets a focused unit test: a {@code RETURNS_DEEP_STUBS} {@link GraphServiceClient}
 * captures the posted {@link Event} and asserts {@code transactionId == syncRecordId}. No live
 * Graph call — only the locally-built {@code Event} is inspected.
 */
class MsGraphEventGatewayTest {

  @Test
  void createEvent_setsTransactionIdToSyncRecordId() {
    UUID recordId = UUID.randomUUID();
    String email = "owner@contoso.com";

    GraphServiceClient graphClient = mock(GraphServiceClient.class, RETURNS_DEEP_STUBS);
    Event created = new Event();
    created.setId("AAMkAGI-evt-1");
    ArgumentCaptor<Event> posted = ArgumentCaptor.forClass(Event.class);
    when(graphClient.users().byUserId(email).events().post(posted.capture())).thenReturn(created);

    MsGraphEventGateway gateway = new MsGraphEventGateway(graphClient);
    CalendarEventSpec spec =
        new CalendarEventSpec(
            "IC Planning — week of 2026-06-08",
            Instant.parse("2026-06-08T13:00:00Z"),
            Instant.parse("2026-06-08T13:30:00Z"),
            recordId);

    String id = gateway.createEvent(email, spec);

    assertThat(id).isEqualTo("AAMkAGI-evt-1");
    // the Graph server-side idempotency key == the syncRecordId → a redundant create is deduped.
    assertThat(posted.getValue().getTransactionId()).isEqualTo(recordId.toString());
  }
}
