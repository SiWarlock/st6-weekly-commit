package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.PrincipalResolver;
import com.st6.wc.identity.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@code PrincipalJwtAuthenticationConverter} unit proof (task 2.6, §6). The real-mode resource
 * server uses this to turn a <strong>validated</strong> {@link Jwt} into an {@code Authentication}
 * carrying a resolved {@link UserPrincipal} (so {@code DomainAuthorizationService} +
 * {@code @AuthenticationPrincipal} see the same principal type as the demo path): {@code
 * Auth0ClaimMapper} → {@code Auth0Identity} → {@code PrincipalResolver.resolve} → principal +
 * {@code ROLE_<role>} authority. A token that resolves to no active employee (unknown OR inactive,
 * Q-B) → {@link OAuth2AuthenticationException} → the chain renders 401 IDOR-safe.
 */
class PrincipalJwtAuthenticationConverterTest {

  private final Auth0ClaimMapper claimMapper = mock(Auth0ClaimMapper.class);
  private final PrincipalResolver resolver = mock(PrincipalResolver.class);
  private final PrincipalJwtAuthenticationConverter converter =
      new PrincipalJwtAuthenticationConverter(claimMapper, resolver);

  private static Jwt jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("auth0|user")
        .claim("scope", "read")
        .build();
  }

  @Test
  void validToken_resolvesToUserPrincipalWithRoleAuthority() {
    UUID id = UUID.randomUUID();
    Auth0Identity identity = new Auth0Identity("auth0|user", RoleType.IC, "e@x.test");
    UserPrincipal principal = new UserPrincipal(id, RoleType.MANAGER, true);
    Jwt jwt = jwt();
    when(claimMapper.map(jwt)).thenReturn(identity);
    when(resolver.resolve(identity)).thenReturn(Optional.of(principal));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.isAuthenticated()).isTrue();
    assertThat(token.getPrincipal()).isEqualTo(principal); // a UserPrincipal, NOT the raw Jwt
    // authority reflects the AUTHORITATIVE Employee.role (manager surfaces gate on ROLE_MANAGER)
    assertThat(token.getAuthorities()).anyMatch(g -> "ROLE_MANAGER".equals(g.getAuthority()));
  }

  @Test
  void unresolvedOrInactiveToken_throwsOAuth2AuthenticationException() {
    Auth0Identity identity = new Auth0Identity("auth0|ghost", RoleType.IC, null);
    Jwt jwt = jwt();
    when(claimMapper.map(jwt)).thenReturn(identity);
    when(resolver.resolve(identity)).thenReturn(Optional.empty()); // no active employee (Q-B)

    // empty resolution -> an authentication exception the chain maps to 401 (no existence leak).
    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }
}
