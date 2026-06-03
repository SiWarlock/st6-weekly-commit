package com.st6.wc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Proves {@code WcApiApplication} also boots under the {@code demo} profile (acceptance 0.4). */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // task 1.5/2.1: boot DB-less + security-less despite the data-jpa + oauth2-resource-server
    // starters being on the classpath — this test only proves the demo-profile context assembles,
    // not persistence or auth. (Demo profile is demo mode, so the real JwtDecoder is gated off
    // too.)
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,"
            + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@ActiveProfiles("demo")
class WcApiDemoBootTest {

  @Test
  void contextLoadsUnderDemoProfile() {
    // Empty body: a green @SpringBootTest proves the demo-profile context assembles.
  }
}
