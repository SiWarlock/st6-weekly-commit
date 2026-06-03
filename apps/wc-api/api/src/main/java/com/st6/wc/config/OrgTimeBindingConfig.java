package com.st6.wc.config;

import com.st6.wc.common.OrgTimeConfig;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code app.org.timezone} ({@code ORG_TIMEZONE}) into the {@code :shared} {@link
 * OrgTimeConfig} bean, applying the D.6 fail-safe. Lives in {@code :api}; the static {@link
 * #resolveZone(String)} is intentionally context-free so it can be unit-tested in isolation and
 * reused by the D.4 generation CronJob without duplicating the rule.
 */
@Configuration
public class OrgTimeBindingConfig {

  private static final Logger log = LoggerFactory.getLogger(OrgTimeBindingConfig.class);

  @Bean
  public OrgTimeConfig orgTimeConfig(@Value("${app.org.timezone:}") String configuredZone) {
    return new OrgTimeConfig(resolveZone(configuredZone));
  }

  /**
   * Fail-safe org-timezone resolution (D.6): unset/blank/unparseable ⇒ {@link
   * OrgTimeConfig#DEFAULT_ZONE} ({@code America/Chicago}) + a WARN — never UTC, never throws. A
   * valid IANA {@link ZoneId} (including an explicit {@code UTC}) is honored.
   *
   * @param configuredZone the configured zone id, possibly {@code null}/blank/invalid.
   * @return a non-null {@link ZoneId}.
   */
  public static ZoneId resolveZone(String configuredZone) {
    if (configuredZone == null || configuredZone.isBlank()) {
      log.warn("ORG_TIMEZONE unset/blank; falling back to {}", OrgTimeConfig.DEFAULT_ZONE);
      return OrgTimeConfig.DEFAULT_ZONE;
    }
    try {
      return ZoneId.of(configuredZone.trim());
    } catch (DateTimeException e) {
      log.warn(
          "ORG_TIMEZONE '{}' is not a valid IANA zone; falling back to {}",
          configuredZone,
          OrgTimeConfig.DEFAULT_ZONE);
      return OrgTimeConfig.DEFAULT_ZONE;
    }
  }
}
