package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.BodyType;
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
 * captures the posted {@link Event} and asserts {@code transactionId == syncRecordId} (104b) plus
 * the {@code subject}/{@code body} passthrough (brief 106 — the spec's HTML body becomes the Graph
 * event body). No live Graph call — only the locally-built {@code Event} is inspected.
 */
class MsGraphEventGatewayTest {

  private static final String BODY_HTML =
      "<p>Open your weekly plan: <a href=\"https://wc.test/weekly-commit/history/p-1\">"
          + "https://wc.test/weekly-commit/history/p-1</a></p>";

  @Test
  void createEvent_setsTransactionIdAndSubjectAndHtmlBody() {
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
            "Weekly Commit — Week of Jun 8–14",
            BODY_HTML,
            Instant.parse("2026-06-08T13:00:00Z"),
            Instant.parse("2026-06-08T13:30:00Z"),
            recordId);

    String id = gateway.createEvent(email, spec);

    assertThat(id).isEqualTo("AAMkAGI-evt-1");
    Event sent = posted.getValue();
    // the Graph server-side idempotency key == the syncRecordId → a redundant create is deduped.
    assertThat(sent.getTransactionId()).isEqualTo(recordId.toString());
    // brief 106 — the spec's subject + HTML body are passed through to the Graph event.
    assertThat(sent.getSubject()).isEqualTo("Weekly Commit — Week of Jun 8–14");
    assertThat(sent.getBody()).isNotNull();
    assertThat(sent.getBody().getContentType()).isEqualTo(BodyType.Html);
    assertThat(sent.getBody().getContent()).isEqualTo(BODY_HTML);
  }
}
