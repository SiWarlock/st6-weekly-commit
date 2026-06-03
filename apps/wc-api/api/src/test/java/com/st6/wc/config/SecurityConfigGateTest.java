package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st6.wc.web.ProblemDetailsAccessDeniedHandler;
import com.st6.wc.web.ProblemDetailsAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code SecurityConfig} startup fail-fast on a non-boolean {@code demo-auth.enabled} gate
 * (task 2.6, security-reviewer HIGH). The per-mode chains are {@code @ConditionalOnProperty}
 * matched against {@code "true"}/{@code "false"}; a malformed value matches NEITHER, silently
 * dropping the rule-#5 backdoor rejector + Auth0 validation + RFC-7807 rendering (Boot's default
 * chain would take over, unsignaled). The context must refuse to start instead.
 */
class SecurityConfigGateTest {

  private static final ProblemDetailsAuthenticationEntryPoint ENTRY =
      new ProblemDetailsAuthenticationEntryPoint();
  private static final ProblemDetailsAccessDeniedHandler DENIED =
      new ProblemDetailsAccessDeniedHandler();

  @Test
  void wellFormedBooleanGate_constructsCleanly() {
    assertThatCode(() -> new SecurityConfig(ENTRY, DENIED, "true")).doesNotThrowAnyException();
    assertThatCode(() -> new SecurityConfig(ENTRY, DENIED, "false")).doesNotThrowAnyException();
    assertThatCode(() -> new SecurityConfig(ENTRY, DENIED, "TRUE")).doesNotThrowAnyException();
  }

  @Test
  void malformedGate_failsFast() {
    for (String bad : new String[] {"yes", "1", "on", "ture", ""}) {
      assertThatThrownBy(() -> new SecurityConfig(ENTRY, DENIED, bad))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("demo-auth.enabled");
    }
  }
}
