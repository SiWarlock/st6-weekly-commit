package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Bean-selection + fail-safe proof for the s9 Graph adapter (mirrors {@code
 * LifecycleSnsGatewaySelectionTest}, §43). EXACTLY ONE {@link GraphCalendarPort} is active per
 * {@code app.graph.mode}: the real {@link GraphCalendarAdapter} in {@code real} mode with complete
 * creds; the demo stub for {@code demo-success}/absent; and — the load-bearing §10 / REQ-I-004 /
 * REQ-E-007 FAIL-SAFE — a degraded port in {@code real} mode with any blank cred (degrade at
 * startup; the worker never crashes; no live Graph client is built; no secret is logged). The
 * selection is property-driven (so no test/env accidentally builds a live Graph client) and
 * order-independent.
 */
class GraphRealModeSelectionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withBean(EmployeeRepository.class, () -> mock(EmployeeRepository.class))
          .withBean(
              Clock.class, () -> Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC))
          .withUserConfiguration(GraphRealModeConfig.class, DemoSuccessGraphCalendarPort.class);

  @Test
  void realModeCompleteCreds_selectsRealAdapter() {
    runner
        .withPropertyValues(
            "app.graph.mode=real",
            "GRAPH_TENANT_ID=11111111-1111-1111-1111-111111111111",
            "GRAPH_CLIENT_ID=22222222-2222-2222-2222-222222222222",
            "GRAPH_CLIENT_SECRET=test-secret-not-real")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBeansOfType(GraphCalendarPort.class)).hasSize(1);
              assertThat(ctx.getBean(GraphCalendarPort.class))
                  .isInstanceOf(GraphCalendarAdapter.class);
            });
  }

  // --- THE fail-safe (§10 / REQ-I-004 / REQ-E-007): real mode + a blank cred → degrade at startup
  // --
  @Test
  void realModeBlankCred_degradesToSafeFailurePort_neverCrashes() {
    runner
        .withPropertyValues(
            "app.graph.mode=real",
            "GRAPH_TENANT_ID=11111111-1111-1111-1111-111111111111",
            "GRAPH_CLIENT_ID=22222222-2222-2222-2222-222222222222",
            "GRAPH_CLIENT_SECRET=") // the client secret is present-but-blank
        .run(
            ctx -> {
              // the context LOADS (the worker never crashes) and selects the degraded port — NO
              // live
              // GraphServiceClient is built without creds.
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBeansOfType(GraphCalendarPort.class)).hasSize(1);
              GraphCalendarPort port = ctx.getBean(GraphCalendarPort.class);
              assertThat(port).isInstanceOf(DegradedGraphCalendarPort.class);
              // per D.6: the degrade RECORDS A SAFE FAILURE — createEvent throws a clean,
              // cause-less
              // exception → s8's listener records FAILED + safe_message + manual-retry (never
              // blocks
              // the lifecycle, never leaks the secret).
              OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
              r.setId(UUID.randomUUID());
              r.setEventKind(EventKind.IC_PLANNING);
              r.setStatus(SyncStatus.SYNCING);
              assertThatThrownBy(() -> port.createEvent(r))
                  .isInstanceOf(GraphCalendarException.class)
                  .hasNoCause();
            });
  }

  @Test
  void demoSuccessMode_selectsStub() {
    runner
        .withPropertyValues("app.graph.mode=demo-success")
        .run(
            ctx -> {
              assertThat(ctx.getBeansOfType(GraphCalendarPort.class)).hasSize(1);
              assertThat(ctx.getBean(GraphCalendarPort.class))
                  .isInstanceOf(DemoSuccessGraphCalendarPort.class);
            });
  }

  @Test
  void absentMode_selectsStub() {
    runner.run(
        ctx -> {
          assertThat(ctx.getBeansOfType(GraphCalendarPort.class)).hasSize(1);
          assertThat(ctx.getBean(GraphCalendarPort.class))
              .isInstanceOf(DemoSuccessGraphCalendarPort.class);
        });
  }
}
