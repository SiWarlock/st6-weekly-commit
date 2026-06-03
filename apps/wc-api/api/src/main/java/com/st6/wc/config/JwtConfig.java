package com.st6.wc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Real-mode Auth0 JWT decoder (§6). Builds a {@link NimbusJwtDecoder} pinned to {@code RS256} from
 * the issuer via {@code withIssuerLocation} — which performs <strong>eager</strong> OIDC discovery
 * (fetches {@code {issuer}/.well-known/openid-configuration} at bean build, a deliberate fail-fast
 * on issuer misconfig; only the JWKS keyset is fetched lazily on first decode) — and combines the
 * default issuer/{@code exp}/{@code nbf} validators (bounded clock skew) with the custom {@link
 * AudienceValidator} via {@code DelegatingOAuth2TokenValidator}.
 *
 * <p>Active <strong>only when not in demo mode</strong> ({@code demo-auth.enabled=false}/absent —
 * the canonical cross-slice gate shared with 2.3 {@code DemoAuthFilter} + 2.6 {@code
 * SecurityConfig}). In real mode the issuer <strong>and</strong> audience are mandatory: a blank
 * value fails fast at startup naming the key (never a silent insecure default — §6). The {@code
 * SecurityFilterChain} that wires this decoder into request processing is task 2.6.
 */
@Configuration
@EnableConfigurationProperties(Auth0Properties.class)
public class JwtConfig {

  @Bean
  @ConditionalOnProperty(name = "demo-auth.enabled", havingValue = "false", matchIfMissing = true)
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
      Auth0Properties auth0) {
    String audience = auth0.audience();
    requireRealModeConfig(issuerUri, "spring.security.oauth2.resourceserver.jwt.issuer-uri");
    requireRealModeConfig(audience, "auth0.audience");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withIssuerLocation(issuerUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(jwtValidator(issuerUri, audience));
    return decoder;
  }

  /**
   * The combined token validator: default issuer/{@code exp}/{@code nbf} (bounded skew) + the
   * mandatory custom audience check. Package-static so tests exercise the SAME validation path with
   * a {@code withPublicKey} decoder (no live JWKS).
   */
  static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String audience) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience));
  }

  private static void requireRealModeConfig(String value, String key) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(
          "Real-mode Auth0 config '"
              + key
              + "' is required but missing/blank. Set it, or run demo mode (demo-auth.enabled=true).");
    }
  }
}
