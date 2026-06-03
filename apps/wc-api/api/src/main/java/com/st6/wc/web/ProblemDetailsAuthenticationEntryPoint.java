package com.st6.wc.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Renders a chain-level authentication failure (no/invalid credentials on a protected {@code
 * /api/**}) as an IDOR-safe {@code 401} {@code application/problem+json} (task 2.6, §5/§16). The
 * {@link AuthenticationException} detail is <strong>never</strong> echoed — only a generic safe
 * message + {@code traceId}.
 */
@Component
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ProblemDetailFactory.write(
        response, HttpStatus.UNAUTHORIZED, "Authentication is required.", null);
  }
}
