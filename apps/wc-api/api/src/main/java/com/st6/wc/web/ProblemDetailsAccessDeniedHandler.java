package com.st6.wc.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders a chain-level authorization failure (a coarse filter-level access denial on an
 * authenticated request) as a {@code 403} {@code application/problem+json} (task 2.6, §5/§16). The
 * {@link AccessDeniedException} detail is <strong>never</strong> echoed — only a generic safe
 * message + {@code traceId}.
 */
@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    ProblemDetailFactory.write(response, HttpStatus.FORBIDDEN, "Access denied.", null);
  }
}
