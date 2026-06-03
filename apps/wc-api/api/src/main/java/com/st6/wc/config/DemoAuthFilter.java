package com.st6.wc.config;

import com.st6.wc.audit.AuditService;
import com.st6.wc.identity.PrincipalResolver;
import com.st6.wc.identity.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Env-gated demo identity filter (task 2.3, SAFETY-CRITICAL — rule #5; §6/§16). <strong>Always in
 * the chain</strong> (a plain class registered by 2.6's {@code SecurityFilterChain} — not a
 * {@code @Component}, to avoid the servlet double-registration of {@code Filter} beans). It
 * branches on the canonical {@code demo-auth.enabled} flag (2.6 supplies it) <em>at request
 * time</em>:
 *
 * <ul>
 *   <li><b>Demo disabled + {@code X-Demo-Employee-Id} present</b> → reject {@code 403} + exactly
 *       one safe-metadata audit (the production-backdoor control, RISK-008). The untrusted header
 *       value is NEVER resolved against the DB and NEVER echoed into the audit (rule #7).
 *   <li><b>Demo enabled + existing id</b> → authenticate as that employee (the header value IS the
 *       principal — no JWT/claim mapping, F.1).
 *   <li><b>Demo enabled + blank/malformed/unknown id</b> → IDOR-safe {@code 401} (generic, no
 *       existence disclosure, no audit-spam — §5).
 *   <li><b>No demo header</b> → no-op pass-through (the bearer/real path is handled elsewhere).
 * </ul>
 *
 * Demo and bearer are never combined: in demo mode a present demo header wins (single source, F.1).
 */
public class DemoAuthFilter extends OncePerRequestFilter {

  static final String DEMO_HEADER = "X-Demo-Employee-Id";
  static final String AUDIT_ACTION = "DEMO_AUTH_REJECTED";

  private final boolean demoEnabled;
  private final PrincipalResolver principalResolver;
  private final AuditService auditService;

  public DemoAuthFilter(
      boolean demoEnabled, PrincipalResolver principalResolver, AuditService auditService) {
    this.demoEnabled = demoEnabled;
    this.principalResolver = principalResolver;
    this.auditService = auditService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    String demoHeader = request.getHeader(DEMO_HEADER);

    if (!demoEnabled) {
      if (demoHeader != null) {
        // rule #5: a demo header while demo auth is disabled is a production-backdoor attempt.
        // Only its PRESENCE is read here — the untrusted value is never resolved against the DB and
        // never echoed into the audit (rule #7).
        auditService.record(
            AUDIT_ACTION,
            "Authentication",
            null,
            null,
            "Demo identity header rejected: demo auth disabled",
            "{\"reason\":\"demo_auth_disabled\"}");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      chain.doFilter(request, response);
      return;
    }

    // demo mode enabled
    if (demoHeader == null) {
      chain.doFilter(request, response); // no demo identity attempted
      return;
    }
    UUID employeeId = parseUuid(demoHeader);
    Optional<UserPrincipal> principal =
        (employeeId == null) ? Optional.empty() : principalResolver.resolve(employeeId);
    if (principal.isEmpty()) {
      // IDOR-safe: blank/malformed/unknown/inactive (Q-B) -> generic 401, no existence disclosure,
      // no audit-spam.
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    // the resolved UserPrincipal IS the principal (F.1); demo branch wins over any bearer (single
    // source). The coarse role gate reads the ROLE_<role> authority off the principal.
    UserPrincipal up = principal.get();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new PreAuthenticatedAuthenticationToken(
                up, null, List.of(new SimpleGrantedAuthority("ROLE_" + up.role().name()))));
    chain.doFilter(request, response);
  }

  private static UUID parseUuid(String raw) {
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
