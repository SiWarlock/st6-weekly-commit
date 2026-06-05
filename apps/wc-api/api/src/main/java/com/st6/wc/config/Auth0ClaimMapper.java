package com.st6.wc.config;

import com.st6.wc.enums.RoleType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps a <strong>validated</strong> Auth0 {@link Jwt} to a typed {@link Auth0Identity} using
 * config-bound claim names (Appendix F.1; task 2.2). Pure extraction — <strong>no DB
 * access</strong> (2.4's {@code PrincipalResolver} resolves {@code externalSubject} → the {@code
 * Employee} row).
 *
 * <p>Semantics (§6 / REQ-S-007/009): {@code externalSubject} = the configured employee-id claim,
 * falling back to the standard {@code sub} claim when absent/blank; {@code role} parses the
 * configured role claim — <em>validate-when-present</em> (a non-blank value not in {@code
 * {IC,MANAGER}} is rejected, no silent default) but <em>tolerate-absent/blank</em> ({@code null}, a
 * "no hint" — the authoritative role is relationship-derived in 2.4); {@code email} is the
 * configured email claim or {@code null}. No claim name is hardcoded.
 */
// Web-gate (deploy-fix #9): the JWT claim mapper is part of the serving (real-mode chain) JWT path
// —
// it consumes Auth0Properties (registered by the now-web-gated JwtConfig) and is used only by the
// HTTP request authz path (PrincipalJwtAuthenticationConverter). Gate it to servlet-web so a
// non-serving batch job (web=none) doesn't eagerly instantiate it (and require Auth0Properties).
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class Auth0ClaimMapper {

  private final Auth0Properties.Claims claims;

  public Auth0ClaimMapper(Auth0Properties properties) {
    this.claims = properties.claims();
  }

  /** Extract the stable identity fields from a validated token. */
  public Auth0Identity map(Jwt jwt) {
    String employeeIdClaim = jwt.getClaimAsString(claims.employeeId());
    String externalSubject =
        StringUtils.hasText(employeeIdClaim) ? employeeIdClaim : jwt.getSubject();
    RoleType role = parseRole(jwt.getClaimAsString(claims.role()));
    String emailClaim = jwt.getClaimAsString(claims.email());
    String email = StringUtils.hasText(emailClaim) ? emailClaim : null;
    return new Auth0Identity(externalSubject, role, email);
  }

  private static RoleType parseRole(String raw) {
    if (!StringUtils.hasText(raw)) {
      // absent/blank role = "no hint" -> null (the authoritative role is the Employee row, 2.4).
      return null;
    }
    try {
      return RoleType.valueOf(raw);
    } catch (IllegalArgumentException ex) {
      // present-but-invalid role -> reject (no silent default, REQ-S-007). Generic message: never
      // echo the bad claim value (rule #7). 2.6's BearerTokenAuthenticationEntryPoint renders 401.
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_token", "Token role claim is not a recognized role", null));
    }
  }
}
