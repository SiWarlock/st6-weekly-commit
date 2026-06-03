package com.st6.wc;

import com.st6.wc.support.AbstractAppBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves {@code WcApiApplication} also boots under the {@code demo} profile (acceptance 0.4)
 * against the shared Testcontainers PG16 ({@link AbstractAppBootTest}). As of Phase 2 demo mode is
 * DB-backed (DemoAuthFilter resolves the demo id + AuditService writes against PG), so this boots
 * with a real DB — only the security autoconfig is excluded (the real SecurityFilterChain is 2.6).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,"
            + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@ActiveProfiles("demo")
class WcApiDemoBootTest extends AbstractAppBootTest {

  @Test
  void contextLoadsUnderDemoProfile() {
    // Empty body: a green @SpringBootTest proves the demo-profile context assembles.
  }
}
