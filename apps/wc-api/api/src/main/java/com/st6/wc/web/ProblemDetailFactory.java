package com.st6.wc.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Stateless builder for RFC-7807 {@code application/problem+json} bodies, shared by all three
 * Phase-2 render points (task 2.6, §5 / Appendix B.21 / §16 RISK-016). Each body carries a {@code
 * safeMessage} (a generic, caller-safe string — never an exception detail or stack) + a {@code
 * traceId} (a generated UUID until a propagated trace context is wired) + an optional {@code code}
 * (the §5 named codes). A pure static utility (no state) so the render components depend on it
 * without holding a reference.
 */
public final class ProblemDetailFactory {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProblemDetailFactory() {}

  /**
   * Build a {@link ProblemDetail} with the safe message, a fresh {@code traceId}, + optional code.
   */
  public static ProblemDetail of(HttpStatus status, String safeMessage, String code) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, safeMessage);
    pd.setProperty("safeMessage", safeMessage);
    pd.setProperty("traceId", UUID.randomUUID().toString());
    if (code != null) {
      pd.setProperty("code", code);
    }
    return pd;
  }

  /**
   * Write a problem+json body directly to the servlet response — for the chain-filter exceptions
   * (authn entry point / access-denied handler) that never reach {@code @RestControllerAdvice}.
   * Flattens the {@link ProblemDetail} to top-level fields (the advice path gets this from Spring's
   * configured {@code ObjectMapper}, so the shapes stay identical).
   */
  public static void write(
      HttpServletResponse response, HttpStatus status, String safeMessage, String code)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    MAPPER.writeValue(response.getWriter(), body(of(status, safeMessage, code)));
  }

  private static Map<String, Object> body(ProblemDetail pd) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("type", pd.getType().toString()); // ProblemDetail.getType() defaults to a non-null URI
    out.put("title", pd.getTitle());
    out.put("status", pd.getStatus());
    out.put("detail", pd.getDetail());
    // safeMessage / traceId / code — flattened to top-level (always present via of()).
    Optional.ofNullable(pd.getProperties()).ifPresent(out::putAll);
    return out;
  }
}
