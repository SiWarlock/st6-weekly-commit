package com.st6.wc.worker.sync;

import com.st6.wc.sync.OutlookCalendarSyncRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The {@code GRAPH_MODE=demo-success} {@link GraphCalendarPort} stub (Wave-2 s8) — simulates a
 * successful calendar create without a live Graph call, returning a deterministic synthetic event
 * id derived only from the record id (rule #7 — no calendar body / secret / token / PII). Lets the
 * worker pipeline run end-to-end in the demo. The real MS Graph adapter swaps in behind the same
 * port at s9 (property-selected like the SNS gateway, LESSONS §43).
 *
 * <p>Active by default + when {@code app.graph.mode=demo-success}; s9 adds the real adapter gated
 * on a distinct mode and tightens this condition.
 */
@Component
@ConditionalOnProperty(name = "app.graph.mode", havingValue = "demo-success", matchIfMissing = true)
public class DemoSuccessGraphCalendarPort implements GraphCalendarPort {

  @Override
  public String createEvent(OutlookCalendarSyncRecord record) {
    return "demo-graph-" + record.getId();
  }
}
