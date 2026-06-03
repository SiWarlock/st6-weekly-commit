package com.st6.wc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code auth0.*} configuration (Appendix D.2). Carries {@code audience} (the
 * custom {@link AudienceValidator} target) for task 2.1; task 2.2 extends this with a {@code
 * claims} sub-structure for the configurable Auth0 claim names (deferred here — 2.2 owns that
 * shape).
 */
@ConfigurationProperties(prefix = "auth0")
public record Auth0Properties(String audience) {}
