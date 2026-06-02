package com.st6.wc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Proves {@code WcApiApplication} also boots under the {@code demo} profile (acceptance 0.4). */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // task 1.5: boot DB-less despite spring-boot-starter-data-jpa now being on the classpath —
    // this test only proves the demo-profile context assembles, not persistence.
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ActiveProfiles("demo")
class WcApiDemoBootTest {

  @Test
  void contextLoadsUnderDemoProfile() {
    // Empty body: a green @SpringBootTest proves the demo-profile context assembles.
  }
}
