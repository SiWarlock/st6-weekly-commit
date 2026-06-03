package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * {@code CorsConfig} allow-list proof (task 2.7, §12 / Appendix D.2 / §16 RISK-008). The {@code
 * CorsConfigurationSource} is an <strong>exact-origin</strong> allow-list — no wildcard, {@code
 * allowCredentials=false} (bearer transport, no cookies), the configured methods/headers, and it
 * takes ONLY the origin list (no {@code demo-auth.enabled} input) so demo mode cannot widen it. A
 * disallowed origin is not matched (never reflected). Preflight-bypasses-auth is proven end-to-end
 * in {@code MeEndpointTest}.
 */
class CorsConfigTest {

  private static final String PROD = "https://wc.example.test";
  private static final String DEV = "http://localhost:5173";

  private static CorsConfiguration configFor(String... origins) {
    CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(origins);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
    return source.getCorsConfiguration(request);
  }

  @Test
  void exactOriginAllowList_noWildcard_credentialsFalse_configuredMethodsAndHeaders() {
    CorsConfiguration cfg = configFor(PROD, DEV);

    assertThat(cfg).isNotNull();
    assertThat(cfg.getAllowedOrigins()).containsExactlyInAnyOrder(PROD, DEV);
    assertThat(cfg.getAllowedOrigins()).doesNotContain("*"); // no wildcard
    assertThat(cfg.getAllowedOriginPatterns()).isNullOrEmpty(); // not via patterns either
    assertThat(cfg.getAllowCredentials()).isFalse(); // bearer transport, no cookies
    assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    assertThat(cfg.getAllowedHeaders())
        .contains("Authorization", "Content-Type", "X-Demo-Employee-Id");
  }

  @Test
  void devOriginLocalhost5173_allowed() {
    assertThat(configFor(PROD, DEV).checkOrigin(DEV)).isEqualTo(DEV); // F.7 dev port
  }

  @Test
  void disallowedOrigin_notMatched() {
    // a disallowed origin resolves to null -> never reflected into Access-Control-Allow-Origin.
    assertThat(configFor(PROD).checkOrigin("https://evil.example.test")).isNull();
  }

  @Test
  void allowList_modeIndependent_demoCannotWiden() {
    // the bean's ONLY input is the origin list (no demo-auth.enabled) — the list is identical in
    // any mode by construction (RISK-008: demo must not relax CORS).
    CorsConfiguration cfg = configFor(PROD, DEV);
    assertThat(cfg.getAllowedOrigins()).containsExactlyInAnyOrder(PROD, DEV);
    assertThat(cfg.checkOrigin("https://evil.example.test")).isNull();
  }
}
