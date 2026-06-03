package com.st6.wc.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates that a decoded Auth0 JWT's {@code aud} claim <em>contains</em> the configured API
 * audience (§6/§16). Auth0 does <strong>not</strong> validate audience by default, so this is the
 * explicit control — combined with the default issuer/{@code exp}/{@code nbf} validators via {@code
 * DelegatingOAuth2TokenValidator} in {@link JwtConfig}. Uses {@code contains} (not {@code equals}):
 * Auth0 tokens routinely carry multiple audiences.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String audience;

  public AudienceValidator(String audience) {
    this.audience = audience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    if (token.getAudience() != null && token.getAudience().contains(audience)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            "invalid_token", "The required audience '" + audience + "' is missing", null));
  }
}
