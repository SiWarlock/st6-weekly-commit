package com.st6.wc.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.auth.AuthorizationDeniedException;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RFC-7807 rendering proof for all three Phase-2 render points (task 2.6, §5/Appendix B.21 / §16
 * RISK-016). Chain-level authn/access-denied exceptions never reach {@code @RestControllerAdvice},
 * so the security cases are split: {@link ProblemDetailsAuthenticationEntryPoint} (401), {@link
 * ProblemDetailsAccessDeniedHandler} (coarse 403), and {@link ProblemDetailsExceptionHandler} (the
 * advice — 404 {@code ResourceNotFoundOrUnauthorizedException}, 403 {@code
 * AuthorizationDeniedException}+code, 500 fallback). All emit {@code application/problem+json} with
 * a {@code safeMessage} + a {@code traceId} and <strong>never</strong> a stack trace or the raw
 * exception detail (RISK-016).
 */
class ProblemDetailsExceptionHandlerTest {

  @RestController
  static class ThrowingController {
    @GetMapping("/notfound")
    void notfound() {
      throw new ResourceNotFoundOrUnauthorizedException();
    }

    @GetMapping("/denied")
    void denied() {
      throw new AuthorizationDeniedException("IC_CANNOT_RESOLVE_DISPUTE");
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("internal-secret-detail-xyz"); // must NEVER leak to the body
    }
  }

  private MockMvc mockMvc() {
    return MockMvcBuilders.standaloneSetup(new ThrowingController())
        .setControllerAdvice(new ProblemDetailsExceptionHandler())
        .build();
  }

  // --- advice: 404 IDOR-safe, problem+json, safeMessage + traceId, no stack ----
  @Test
  void resourceNotFound_renders404ProblemJson() throws Exception {
    mockMvc()
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/notfound"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.safeMessage").isNotEmpty())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  // --- advice: 403 capability denial carries the named code ----
  @Test
  void authorizationDenied_renders403WithCode() throws Exception {
    mockMvc()
        .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/denied"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("IC_CANNOT_RESOLVE_DISPUTE"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  // --- advice: 500 fallback never leaks the exception detail/stack (RISK-016) ----
  @Test
  void unexpectedException_renders500WithoutLeak() throws Exception {
    String body =
        mockMvc()
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.safeMessage").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("internal-secret-detail-xyz");
    assertThat(body).doesNotContain("IllegalStateException");
    assertThat(body).doesNotContain("com.st6.wc"); // no stack frames
  }

  // --- entry point: 401 problem+json (chain authn failure) ----
  @Test
  void authenticationEntryPoint_writes401ProblemJson() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    new ProblemDetailsAuthenticationEntryPoint()
        .commence(new MockHttpServletRequest(), response, new BadCredentialsException("bad"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).contains("application/problem+json");
    assertThat(response.getContentAsString()).contains("safeMessage").contains("traceId");
    assertThat(response.getContentAsString()).doesNotContain("bad"); // no echo of the auth detail
  }

  // --- access-denied handler: coarse 403 problem+json (chain authz failure) ----
  @Test
  void accessDeniedHandler_writes403ProblemJson() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    new ProblemDetailsAccessDeniedHandler()
        .handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).contains("application/problem+json");
    assertThat(response.getContentAsString()).contains("safeMessage").contains("traceId");
  }
}
