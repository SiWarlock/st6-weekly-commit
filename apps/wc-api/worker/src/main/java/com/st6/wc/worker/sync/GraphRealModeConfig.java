package com.st6.wc.worker.sync;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.st6.wc.employee.repo.EmployeeRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Selects the {@code GRAPH_MODE=real} {@link GraphCalendarPort} (Wave-2 s9, §10 / §43 / rules #4 +
 * #7). Active only when {@code app.graph.mode=real} — mutually exclusive with the s8 {@link
 * DemoSuccessGraphCalendarPort} stub ({@code havingValue=demo-success, matchIfMissing=true}), so
 * exactly one port bean exists per {@code app.graph.mode}.
 *
 * <p><strong>Fail-safe (Appendix D.6 / REQ-I-004 / REQ-E-007) — degrade at startup:</strong> if any
 * {@code GRAPH_*} credential is missing/blank, this builds NO live Graph client (a blank secret
 * would fail credential construction and crash the worker) and wires the {@link
 * DegradedGraphCalendarPort} instead — which records a safe failure per message rather than masking
 * the misconfiguration as a fake {@code SYNCED} event. One safe warning is logged (presence flags
 * only — NEVER a secret value, rule #7). With complete creds it builds an app-only {@link
 * ClientSecretCredential} + {@link GraphServiceClient} and wires the real {@link
 * GraphCalendarAdapter} behind the {@link MsGraphEventGateway} seam. The {@code GRAPH_*} keys bind
 * from the {@code configtree:/mnt/secrets/} mount in the deployed {@code aws} profile (LESSONS
 * §42).
 */
@Configuration
@ConditionalOnProperty(name = "app.graph.mode", havingValue = "real")
public class GraphRealModeConfig {

  private static final Logger log = LoggerFactory.getLogger(GraphRealModeConfig.class);

  /** App-only Graph scope — the app-registration's admin-consented application permissions. */
  private static final String GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

  @Bean
  GraphCalendarPort graphCalendarPort(
      EmployeeRepository employees,
      Clock clock,
      @Value("${GRAPH_TENANT_ID:}") String tenantId,
      @Value("${GRAPH_CLIENT_ID:}") String clientId,
      @Value("${GRAPH_CLIENT_SECRET:}") String clientSecret,
      @Value("${app.outlook.frontend-base-url:https://wc.${ROOT_DOMAIN:localhost}}")
          String frontendBaseUrl) {

    if (!StringUtils.hasText(tenantId)
        || !StringUtils.hasText(clientId)
        || !StringUtils.hasText(clientSecret)) {
      // Fail-safe (D.6) — log presence flags ONLY (never a credential value, rule #7).
      log.warn(
          "GRAPH_MODE=real but Graph credentials are incomplete"
              + " (tenantId/clientId/clientSecret present={}/{}/{}) — degrading to the safe-failure"
              + " calendar port; calendar sync records a safe failure until the credentials are"
              + " populated.",
          StringUtils.hasText(tenantId),
          StringUtils.hasText(clientId),
          StringUtils.hasText(clientSecret));
      return new DegradedGraphCalendarPort();
    }

    ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .tenantId(tenantId)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    GraphServiceClient graphClient = new GraphServiceClient(credential, GRAPH_DEFAULT_SCOPE);
    return new GraphCalendarAdapter(
        new MsGraphEventGateway(graphClient), employees, clock, frontendBaseUrl);
  }
}
