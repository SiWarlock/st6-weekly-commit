package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.audit.AuditService;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.PrincipalResolver;
import com.st6.wc.identity.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@code DemoAuthFilter} behavior proof (task 2.3, SAFETY-CRITICAL — rule #5; updated at 2.6). The
 * filter now resolves the demo id via {@link PrincipalResolver} (so the authenticated principal is
 * a {@link UserPrincipal}, and the Q-B inactive-employee guard applies), but the rule-#5 backdoor
 * control is unchanged: a demo header while demo auth is disabled is rejected {@code 403} + exactly
 * one safe-metadata audit (RISK-008) WITHOUT any resolution (presence-only).
 * Unknown/blank/malformed /inactive ids in demo mode are denied IDOR-safely ({@code 401}, no
 * existence leak, no audit-spam); demo + bearer never combine (demo wins, F.1).
 */
class DemoAuthFilterTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  // a recognizable untrusted value — must NEVER appear in the audit (rule #7 / log-injection
  // guard).
  private static final String SENTINEL = "evil|injection|<script>alert(1)</script>";

  private final PrincipalResolver resolver = mock(PrincipalResolver.class);
  private final AuditService audit = mock(AuditService.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // --- 1. demo enabled + existing id -> authenticate AS A UserPrincipal (resolved, not raw UUID)
  // --
  @Test
  void demo_enabled_valid_header_authenticates() throws Exception {
    UUID id = UUID.randomUUID();
    UserPrincipal principal = new UserPrincipal(id, RoleType.MANAGER, true);
    when(resolver.resolve(id)).thenReturn(Optional.of(principal));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, id.toString());
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new DemoAuthFilter(true, resolver, audit).doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isNotNull()
        .satisfies(
            a -> {
              assertThat(a.isAuthenticated()).isTrue();
              assertThat(a.getPrincipal()).isEqualTo(principal); // a UserPrincipal, not the raw id
              // the coarse role gate reads ROLE_<role> from the resolved principal
              assertThat(a.getAuthorities()).anyMatch(g -> "ROLE_MANAGER".equals(g.getAuthority()));
            });
    verify(chain).doFilter(request, response);
    verifyNoInteractions(audit);
  }

  // --- 2. demo DISABLED + header present -> 403 + ONE safe audit (the backdoor pin, presence-only)
  // -
  @Test
  void demo_disabled_header_rejected_403_with_audit() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, SENTINEL);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new DemoAuthFilter(false, resolver, audit).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain, never()).doFilter(request, response);
    verifyNoInteractions(resolver); // never resolve a backdoor attempt against the DB

    ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<UUID> actor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
    verify(audit)
        .record(
            action.capture(),
            entityType.capture(),
            entityId.capture(),
            actor.capture(),
            summary.capture(),
            metadata.capture());
    assertThat(action.getValue()).isEqualTo("DEMO_AUTH_REJECTED");
    assertThat(entityId.getValue()).as("never store the untrusted header value").isNull();
    assertThat(actor.getValue()).as("no authenticated principal -> SYSTEM").isNull();
    // rule #7: NONE of the recorded text may echo the raw untrusted header value.
    assertThat(nullToEmpty(summary.getValue())).doesNotContain(SENTINEL);
    assertThat(nullToEmpty(metadata.getValue())).doesNotContain(SENTINEL);
    assertThat(nullToEmpty(entityType.getValue())).doesNotContain(SENTINEL);
  }

  // --- 3. demo disabled + NO header -> no-op pass-through (real path handles it) ----
  @Test
  void demo_disabled_no_header_passes_through() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new DemoAuthFilter(false, resolver, audit).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
    verifyNoInteractions(audit);
  }

  // --- 4. demo enabled + unknown/inactive id -> IDOR-safe 401, no audit, no existence leak ----
  @Test
  void demo_enabled_unknown_or_inactive_id_idor_safe() throws Exception {
    UUID id = UUID.randomUUID();
    when(resolver.resolve(id)).thenReturn(Optional.empty()); // unknown OR inactive (Q-B)

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, id.toString());
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new DemoAuthFilter(true, resolver, audit).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain, never()).doFilter(request, response);
    verifyNoInteractions(audit); // a normal failed auth is not a backdoor attempt — no audit-spam
    assertThat(nullToEmpty(response.getErrorMessage())).doesNotContainIgnoringCase("not found");
    assertThat(response.getContentAsString()).isEmpty();
  }

  // --- 5. demo enabled + blank/malformed id -> denied 401, no NPE, no resolve, no audit ----
  @Test
  void demo_enabled_blank_or_malformed_id_denied() throws Exception {
    for (String bad : new String[] {"   ", "not-a-uuid"}) {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader(HEADER, bad);
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      new DemoAuthFilter(true, resolver, audit).doFilter(request, response, chain);

      assertThat(response.getStatus())
          .as("bad id '%s'", bad)
          .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      verify(chain, never()).doFilter(request, response);
      SecurityContextHolder.clearContext();
    }
    verifyNoInteractions(resolver, audit); // a malformed id never reaches resolution
  }

  // --- 6. demo + bearer both present (demo mode) -> demo branch wins (single source) ----
  @Test
  void demo_and_bearer_both_present_single_source() throws Exception {
    UUID id = UUID.randomUUID();
    UserPrincipal principal = new UserPrincipal(id, RoleType.IC, false);
    when(resolver.resolve(id)).thenReturn(Optional.of(principal));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, id.toString());
    request.addHeader("Authorization", "Bearer some.jwt.token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    new DemoAuthFilter(true, resolver, audit).doFilter(request, response, chain);

    // demo branch wins: authenticated as the demo UserPrincipal; the bearer is not combined.
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(principal);
    verify(chain).doFilter(request, response);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
