package com.st6.wc.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS allow-list (task 2.7, §12 / Appendix D.2 / §16 RISK-008). An <strong>exact-origin</strong>
 * allow-list from {@code app.cors.allowed-origins} ({@code CORS_ALLOWED_ORIGINS}) — never a
 * wildcard; {@code allowCredentials=false} (bearer transport, no cookies); the configured
 * methods/headers (incl. {@code X-Demo-Employee-Id} for the demo path). The bean's ONLY input is
 * the origin list — it never reads {@code demo-auth.enabled}, so <strong>demo mode cannot widen the
 * allow-list</strong> (RISK-008 corollary). Wired into both 2.6 chains via {@code http.cors(...)};
 * the chain's CORS filter answers preflight {@code OPTIONS} before authentication.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins)); // exact origins — never a wildcard
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Demo-Employee-Id"));
    config.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
