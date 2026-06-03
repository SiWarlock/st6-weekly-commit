package com.st6.wc.config;

import com.st6.wc.identity.PrincipalResolver;
import com.st6.wc.identity.UserPrincipal;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Real-mode bridge from a <strong>validated</strong> Auth0 {@link Jwt} to an {@code Authentication}
 * carrying a resolved {@link UserPrincipal} (task 2.6, §6). Wired into the resource server's {@code
 * jwtAuthenticationConverter} so the principal type is identical to the demo path — {@code
 * DomainAuthorizationService} + {@code @AuthenticationPrincipal UserPrincipal} see the same object
 * in both modes.
 *
 * <p>{@code Auth0ClaimMapper} → {@code Auth0Identity} → {@code PrincipalResolver.resolve} → a
 * {@code PreAuthenticatedAuthenticationToken} whose principal is the {@code UserPrincipal} and
 * whose single authority is {@code ROLE_<authoritative role>} (coarse role gating reads this;
 * fine-grained ownership is {@code DomainAuthorizationService}). A token that resolves to no
 * <em>active</em> employee (unknown OR inactive, Q-B) → {@link OAuth2AuthenticationException},
 * which the chain renders as an IDOR-safe 401 (no existence disclosure).
 */
public class PrincipalJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final Auth0ClaimMapper claimMapper;
  private final PrincipalResolver principalResolver;

  public PrincipalJwtAuthenticationConverter(
      Auth0ClaimMapper claimMapper, PrincipalResolver principalResolver) {
    this.claimMapper = claimMapper;
    this.principalResolver = principalResolver;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Auth0Identity identity = claimMapper.map(jwt);
    UserPrincipal principal =
        principalResolver
            .resolve(identity)
            .orElseThrow(
                () ->
                    new OAuth2AuthenticationException(
                        new OAuth2Error(
                            "invalid_token",
                            "Token identity could not be resolved to an active user",
                            null)));
    return new PreAuthenticatedAuthenticationToken(
        principal, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
  }
}
