package com.st6.wc.config;

import com.st6.wc.audit.AuditService;
import com.st6.wc.identity.PrincipalResolver;
import com.st6.wc.web.ProblemDetailsAccessDeniedHandler;
import com.st6.wc.web.ProblemDetailsAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Wires the Phase-2 identity spine into the request path (task 2.6, SAFETY-touching — operational
 * half of rule #5 + rule #3). Exactly <strong>one chain per mode</strong>, keyed on the canonical
 * {@code demo-auth.enabled} gate (LESSONS §12), so bearer and demo never both authenticate one
 * request:
 *
 * <ul>
 *   <li><b>demo</b> ({@code demo-auth.enabled=true}): the {@code DemoAuthFilter} authenticates an
 *       {@code X-Demo-Employee-Id} to a {@code UserPrincipal} (no JWT path).
 *   <li><b>real</b> ({@code false}/absent): the OAuth2 resource server validates the Auth0 JWT and
 *       (via {@link PrincipalJwtAuthenticationConverter}) resolves it to a {@code UserPrincipal};
 *       the {@code DemoAuthFilter} runs <em>disabled</em> as the production-backdoor rejector (a
 *       demo header in prod → {@code 403} + audit — rule #5 operational half).
 * </ul>
 *
 * Both chains: stateless, csrf-off, {@code /actuator/health/**} public, every {@code /api/**}
 * authenticated; coarse role gating is {@code @PreAuthorize} via {@link EnableMethodSecurity}
 * (resource ownership is delegated to {@code DomainAuthorizationService}); authn/authz failures
 * render RFC-7807 via the shared entry point / access-denied handler.
 */
@Configuration(proxyBeanMethods = false) // the @Bean methods don't call each other (lite mode)
@EnableWebSecurity
@EnableMethodSecurity
public final class SecurityConfig {

  private final ProblemDetailsAuthenticationEntryPoint entryPoint;
  private final ProblemDetailsAccessDeniedHandler accessDeniedHandler;

  public SecurityConfig(
      ProblemDetailsAuthenticationEntryPoint entryPoint,
      ProblemDetailsAccessDeniedHandler accessDeniedHandler,
      @Value("${demo-auth.enabled:false}") String demoAuthEnabled) {
    this.entryPoint = entryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
    requireBooleanGate(demoAuthEnabled);
  }

  /**
   * Fail fast on a non-boolean {@code demo-auth.enabled} (security mode gate). The per-mode chains
   * are {@code @ConditionalOnProperty} matched against {@code "true"}/{@code "false"}; a malformed
   * value (e.g. {@code yes}/{@code 1}/a typo) would match NEITHER chain, silently dropping the
   * rule-#5 backdoor rejector + Auth0 validation + RFC-7807 rendering (Boot's default chain would
   * take over — locked-down but with the intended controls absent and no signal). Refuse to start
   * with an ambiguous mode (mirrors {@code JwtConfig}'s fail-fast-on-missing-config).
   */
  private static void requireBooleanGate(String demoAuthEnabled) {
    if (!"true".equalsIgnoreCase(demoAuthEnabled) && !"false".equalsIgnoreCase(demoAuthEnabled)) {
      throw new IllegalStateException(
          "'demo-auth.enabled' must be exactly 'true' or 'false' (was '"
              + demoAuthEnabled
              + "') — refusing to start with an ambiguous security mode.");
    }
  }

  @Bean
  @ConditionalOnProperty(name = "demo-auth.enabled", havingValue = "true")
  SecurityFilterChain demoModeChain(
      HttpSecurity http, PrincipalResolver resolver, AuditService audit) throws Exception {
    commonRules(http);
    http.addFilterBefore(new DemoAuthFilter(true, resolver, audit), AuthorizationFilter.class);
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(name = "demo-auth.enabled", havingValue = "false", matchIfMissing = true)
  SecurityFilterChain realModeChain(
      HttpSecurity http,
      PrincipalResolver resolver,
      AuditService audit,
      Auth0ClaimMapper claimMapper)
      throws Exception {
    commonRules(http);
    http.oauth2ResourceServer(
        oauth2 ->
            oauth2
                .authenticationEntryPoint(entryPoint)
                .jwt(
                    jwt ->
                        jwt.jwtAuthenticationConverter(
                            new PrincipalJwtAuthenticationConverter(claimMapper, resolver))));
    // a demo header in real mode is a production-backdoor attempt — the disabled DemoAuthFilter
    // rejects it (403 + audit) BEFORE any bearer processing (rule #5 operational half).
    http.addFilterBefore(
        new DemoAuthFilter(false, resolver, audit), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  private void commonRules(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults()) // exact-origin allow-list (CorsConfig) on both chains;
        // preflight OPTIONS is answered before auth. The CorsConfigurationSource is
        // mode-independent
        // so demo cannot widen it (§16 RISK-008).
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));
  }
}
