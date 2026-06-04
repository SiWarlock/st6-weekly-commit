package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof for the {@code GRAPH_MODE=demo-success} stub (Wave-2 s8) — the no-op port that
 * simulates a successful calendar create without a live Graph call (the real MS Graph adapter swaps
 * in behind the {@link GraphCalendarPort} at s9, §43). Returns a deterministic synthetic {@code
 * graphEventId} derived only from the record id (rule #7 — no PII/secret/calendar body).
 */
class DemoSuccessGraphCalendarPortTest {

  private final GraphCalendarPort port = new DemoSuccessGraphCalendarPort();

  @Test
  void createEvent_returnsSyntheticDeterministicId() {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.SYNCING);

    String id = port.createEvent(r);

    assertThat(id).isNotBlank().startsWith("demo-graph-");
    assertThat(port.createEvent(r)).as("deterministic for the same record").isEqualTo(id);
  }
}
