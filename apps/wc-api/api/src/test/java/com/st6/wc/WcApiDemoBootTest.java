package com.st6.wc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Proves {@code WcApiApplication} also boots under the {@code demo} profile (acceptance 0.4). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("demo")
class WcApiDemoBootTest {

  @Test
  void contextLoadsUnderDemoProfile() {
    // Empty body: a green @SpringBootTest proves the demo-profile context assembles.
  }
}
