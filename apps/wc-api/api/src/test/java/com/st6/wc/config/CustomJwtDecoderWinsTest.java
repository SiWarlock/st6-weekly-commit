package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * No-collision proof for the custom {@code JwtDecoder} (task 2.6, folds the 2.1 carry-forward).
 * Registers Boot's {@link OAuth2ResourceServerAutoConfiguration} (whose decoder is
 * {@code @ConditionalOnMissingBean}) alongside our {@link JwtConfig}: with an {@code issuer-uri}
 * set, the autoconfig WOULD build a decoder, but ours (an unconditional {@code @Bean}) must win.
 * Proven two ways: (1) exactly one {@code JwtDecoder} bean (no ambiguity), and (2) it is OURS — it
 * rejects a valid-issuer token whose {@code aud} is wrong (our {@link AudienceValidator}), which
 * the autoconfig's issuer-only default decoder would accept. A {@link MockWebServer} serves the
 * OIDC discovery doc + JWKS so the real {@code withIssuerLocation} decoder builds + decodes without
 * a live Auth0 tenant (LESSONS §12).
 */
class CustomJwtDecoderWinsTest {

  private static final String AUDIENCE = "https://api.wc.test";
  private static final String OTHER_AUDIENCE = "https://evil.example.test";
  private static final String KID = "test-key";

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;

  @BeforeAll
  static void keys() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair kp = gen.generateKeyPair();
    publicKey = (RSAPublicKey) kp.getPublic();
    privateKey = (RSAPrivateKey) kp.getPrivate();
  }

  @Test
  void customDecoderWins_noCollision_andAudienceValidatorApplies() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      String issuer = server.url("/").toString();
      server.setDispatcher(discoveryAndJwksDispatcher(issuer));

      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OAuth2ResourceServerAutoConfiguration.class))
          .withUserConfiguration(JwtConfig.class)
          .withPropertyValues(
              "demo-auth.enabled=false",
              "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
              "auth0.audience=" + AUDIENCE)
          .run(
              ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(JwtDecoder.class); // no collision
                JwtDecoder decoder = ctx.getBean(JwtDecoder.class);
                // ours accepts the correct audience...
                assertThat(decoder.decode(rs256(issuer, List.of(AUDIENCE)))).isNotNull();
                // ...and rejects a wrong audience — proving OUR AudienceValidator is wired (the
                // autoconfig's issuer-only default decoder would have accepted this).
                assertThatThrownBy(() -> decoder.decode(rs256(issuer, List.of(OTHER_AUDIENCE))))
                    .isInstanceOf(JwtException.class);
              });
    }
  }

  private Dispatcher discoveryAndJwksDispatcher(String issuer) throws Exception {
    String jwks =
        "{\"keys\":["
            + new RSAKey.Builder(publicKey).keyID(KID).build().toPublicJWK().toJSONString()
            + "]}";
    String discovery =
        "{\"issuer\":\""
            + issuer
            + "\",\"jwks_uri\":\""
            + issuer
            + ".well-known/jwks.json\",\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
    return new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        String rawPath = request.getPath();
        String path = rawPath == null ? "" : rawPath;
        if (path.contains("/.well-known/jwks.json")) {
          return json(jwks);
        }
        if (path.contains("/.well-known/")) {
          return json(discovery);
        }
        return new MockResponse().setResponseCode(404);
      }
    };
  }

  private static MockResponse json(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private String rs256(String issuer, List<String> aud) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("auth0|user")
            .audience(aud)
            .issueTime(Date.from(Instant.now().minusSeconds(30)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    SignedJWT jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims);
    jwt.sign(new RSASSASigner(privateKey));
    return jwt.serialize();
  }
}
