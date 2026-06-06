package com.st6.wc.worker.sync;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.DateTimeTimeZone;
import com.microsoft.graph.models.Event;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * The lone Microsoft Graph SDK-touching {@link GraphEventGateway} implementation (Wave-2 s9, §10).
 * Translates the non-PII {@link CalendarEventSpec} to a Graph {@code Event} and posts it to the
 * owner's calendar via {@code users().byUserId(email).events().post(event)}, returning the created
 * event's id. Constructed only by {@link GraphRealModeConfig} in {@code real} mode with complete
 * creds; isolating the SDK here keeps the Kiota types out of {@link GraphCalendarAdapter}, which is
 * unit-tested against the {@link GraphEventGateway} interface (no live Graph call in tests). The
 * {@code .post(…)} call is exercised only against a live Graph in a {@code GRAPH_MODE=real} deploy.
 */
public class MsGraphEventGateway implements GraphEventGateway {

  private final GraphServiceClient graphClient;

  public MsGraphEventGateway(GraphServiceClient graphClient) {
    this.graphClient = graphClient;
  }

  @Override
  public String createEvent(String userEmail, CalendarEventSpec spec) {
    Event event = new Event();
    event.setSubject(spec.subject());
    event.setStart(graphDateTime(spec.start()));
    event.setEnd(graphDateTime(spec.end()));
    // The §10 deep-link body (brief 106), HTML so the link is clickable — a non-PII generic line +
    // the frontend deep-link URL built by the adapter (rule #7).
    ItemBody body = new ItemBody();
    body.setContentType(BodyType.Html);
    body.setContent(spec.body());
    event.setBody(body);
    // Idempotency (brief 104b): the syncRecordId IS the Graph transactionId — Graph's server-side
    // idempotency key. A redundant create (e.g. the reaper re-firing a record whose prior
    // createEvent succeeded but whose SYNCED-save failed, leaving graphEventId unpersisted) is
    // deduped by Graph → it returns the EXISTING event (same id) instead of duplicating. The
    // recordId is a UUID → rule #7 (no PII in the key). The dedup retention is bounded; the
    // listener's early-persist of graphEventId covers the longer tail.
    event.setTransactionId(spec.recordId().toString());

    Event created = graphClient.users().byUserId(userEmail).events().post(event);
    return created.getId();
  }

  /** Renders an absolute {@link Instant} as a UTC Graph {@code DateTimeTimeZone}. */
  private static DateTimeTimeZone graphDateTime(Instant instant) {
    DateTimeTimeZone dt = new DateTimeTimeZone();
    dt.setDateTime(LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString());
    dt.setTimeZone("UTC");
    return dt;
  }
}
