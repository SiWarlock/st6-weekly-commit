package com.st6.wc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code auth0.*} configuration (Appendix D.2). Carries {@code audience} (the
 * custom {@link AudienceValidator} target, task 2.1) and the configurable {@code claims} names the
 * {@link Auth0ClaimMapper} reads (task 2.2, Appendix F.1). Bound in <em>all</em> profiles (via
 * {@link JwtConfig}'s {@code @EnableConfigurationProperties}), so the {@code application.yml} claim
 * defaults use a resolvable {@code ${ROOT_DOMAIN:localhost}} placeholder (an unresolvable one would
 * break the demo/local context boots at binding time).
 */
@ConfigurationProperties(prefix = "auth0")
public record Auth0Properties(String audience, Claims claims) {

  /**
   * Configurable Auth0 claim <em>names</em> (Appendix F.1), not values — the mapper resolves the
   * JWT claims by these names so nothing is hardcoded (REQ-S-009). Bound from {@code
   * auth0.claims.{employee-id,role,email}}.
   */
  public record Claims(String employeeId, String role, String email) {}
}
