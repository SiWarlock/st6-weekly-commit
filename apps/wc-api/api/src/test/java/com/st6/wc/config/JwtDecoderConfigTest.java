package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Auth0 JWT decoder + validator proof (task 2.1, §6/§16/§17). The {@link AudienceValidator} is a
 * pure unit; the decoder's algorithm/issuer/audience/expiry behavior is proven with a generated RSA
 * keypair + Nimbus-signed tokens (no live Auth0/JWKS — the decoder under test is built {@code
 * withPublicKey} but wired with the SAME validators {@link JwtConfig} uses in production, so the
 * security-critical validation path is exercised). Fail-fast-on-missing-config uses {@link
 * ApplicationContextRunner} (LESSONS §4). The 401-problem+json filter-chain behavior is task 2.6.
 */
class JwtDecoderConfigTest {

  private static final String ISSUER = "https://tenant.us.auth0.test/";
  private static final String AUDIENCE = "https://api.wc.test";
  private static final String OTHER_AUDIENCE = "https://evil.example.test";
  // HS256 needs a >=256-bit (32-byte) secret.
  private static final String HS_SECRET = "0123456789abcdef0123456789abcdef";

  private static RSAPublicKey publicKey;
  private static RSAPrivateKey privateKey;

  @BeforeAll
  static void generateKeys() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair kp = gen.generateKeyPair();
    publicKey = (RSAPublicKey) kp.getPublic();
    privateKey = (RSAPrivateKey) kp.getPrivate();
  }

  // ===== AudienceValidator (pure unit) =====

  @Test
  void audience_validator_accepts_matching_aud() {
    Jwt jwt = jwtWithAudience(List.of(AUDIENCE));
    OAuth2TokenValidatorResult result = new AudienceValidator(AUDIENCE).validate(jwt);
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void audience_validator_rejects_missing_or_wrong_aud() {
    AudienceValidator validator = new AudienceValidator(AUDIENCE);
    assertThat(validator.validate(jwtWithAudience(List.of(OTHER_AUDIENCE))).hasErrors()).isTrue();
    assertThat(validator.validate(jwtWithAudience(null)).hasErrors()).isTrue();
  }

  @Test
  void audience_validator_accepts_when_required_aud_among_multiple() {
    // Auth0 tokens routinely carry multiple audiences — `aud` CONTAINS the required one is success
    // (pins the `.contains` semantic against an accidental `.equals` regression).
    Jwt jwt = jwtWithAudience(List.of(AUDIENCE, OTHER_AUDIENCE));
    assertThat(new AudienceValidator(AUDIENCE).validate(jwt).hasErrors()).isFalse();
  }

  // ===== Decoder behavior (generated RSA key + Nimbus-signed tokens) =====

  @Test
  void decoder_accepts_valid_rs256_token() throws Exception {
    String token = rs256(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300));
    Jwt jwt = decoder().decode(token);
    assertThat(jwt.getSubject()).isEqualTo("auth0|user");
    assertThat(jwt.getAudience()).contains(AUDIENCE);
  }

  @Test
  void decoder_rejects_alg_none() {
    String token = none(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoder_rejects_symmetric_hs256() throws Exception {
    String token = hs256(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoder_rejects_wrong_issuer() throws Exception {
    String token =
        rs256("https://attacker.example.test/", List.of(AUDIENCE), Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoder_rejects_wrong_or_missing_audience() throws Exception {
    String wrong = rs256(ISSUER, List.of(OTHER_AUDIENCE), Instant.now().plusSeconds(300));
    String missing = rs256(ISSUER, null, Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> decoder().decode(wrong)).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder().decode(missing)).isInstanceOf(JwtException.class);
  }

  @Test
  void expired_token_rejected_beyond_skew() throws Exception {
    // default JwtTimestampValidator skew is 60s: 30s-past is accepted, 5min-past is rejected.
    String withinSkew = rs256(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(30));
    String beyondSkew = rs256(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(300));
    assertThat(decoder().decode(withinSkew)).isNotNull();
    assertThatThrownBy(() -> decoder().decode(beyondSkew)).isInstanceOf(JwtException.class);
  }

  // ===== Fail-fast on missing real-mode config =====

  @Test
  void missing_issuer_or_audience_config_fails_fast() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(JwtConfig.class);

    // blank issuer in real mode -> fail fast naming the issuer key (the IllegalStateException is
    // wrapped in a BeanCreationException, so assert on the full stack trace, not the top message).
    runner
        .withPropertyValues(
            "demo-auth.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
            "auth0.audience=" + AUDIENCE)
        .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("issuer-uri"));

    // blank audience in real mode -> fail fast naming the audience key
    runner
        .withPropertyValues(
            "demo-auth.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER,
            "auth0.audience=")
        .run(
            ctx ->
                assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("auth0.audience"));
  }

  @Test
  void real_mode_builds_decoder_bean() throws Exception {
    // real mode -> the JwtDecoder bean assembles via EAGER OIDC discovery (withIssuerLocation
    // fetches {issuer}/.well-known/openid-configuration at build). A MockWebServer stubs that
    // discovery doc (its issuer field must EXACTLY match the configured issuer) so the real
    // construction + setJwtValidator wiring runs without a live Auth0 tenant. JWKS is fetched
    // lazily
    // on first decode (not exercised here), so only the discovery endpoint needs serving.
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      String issuer = server.url("/").toString();
      String discoveryDoc =
          "{\"issuer\":\""
              + issuer
              + "\",\"authorization_endpoint\":\""
              + issuer
              + "authorize\",\"token_endpoint\":\""
              + issuer
              + "oauth/token\",\"jwks_uri\":\""
              + issuer
              + ".well-known/jwks.json\",\"response_types_supported\":[\"code\"],"
              + "\"subject_types_supported\":[\"public\"],"
              + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
      server.setDispatcher(
          new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
              String path = request.getPath();
              if (path != null && path.contains("/.well-known/")) {
                return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(discoveryDoc);
              }
              return new MockResponse().setResponseCode(404);
            }
          });

      new ApplicationContextRunner()
          .withUserConfiguration(JwtConfig.class)
          .withPropertyValues(
              "demo-auth.enabled=false",
              "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
              "auth0.audience=" + AUDIENCE)
          .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }
  }

  @Test
  void demo_mode_gates_decoder_off() {
    // demo-auth.enabled=true gates the @ConditionalOnProperty JwtDecoder OFF — context starts
    // cleanly even with blank Auth0 config, and no decoder bean exists. This is the cross-slice
    // gate
    // 2.3 (DemoAuthFilter) + 2.6 (SecurityConfig) depend on (and the demo-mode skeleton boot path).
    new ApplicationContextRunner()
        .withUserConfiguration(JwtConfig.class)
        .withPropertyValues(
            "demo-auth.enabled=true",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
            "auth0.audience=")
        .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(JwtDecoder.class));
  }

  // ===== helpers =====

  /** A decoder built with the test public key but wired with JwtConfig's production validators. */
  private static NimbusJwtDecoder decoder() {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(JwtConfig.jwtValidator(ISSUER, AUDIENCE));
    return decoder;
  }

  private static Jwt jwtWithAudience(List<String> aud) {
    Jwt.Builder b =
        Jwt.withTokenValue("token").header("alg", "none").subject("auth0|user").issuer(ISSUER);
    if (aud != null) {
      b.claim(JwtClaimNames.AUD, aud);
    }
    return b.build();
  }

  private static JWTClaimsSet claims(String issuer, List<String> aud, Instant exp) {
    JWTClaimsSet.Builder b =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("auth0|user")
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(exp));
    if (aud != null) {
      b.audience(aud);
    }
    return b.build();
  }

  private static String rs256(String issuer, List<String> aud, Instant exp) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims(issuer, aud, exp));
    jwt.sign(new RSASSASigner(privateKey));
    return jwt.serialize();
  }

  private static String hs256(String issuer, List<String> aud, Instant exp) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(issuer, aud, exp));
    jwt.sign(new MACSigner(HS_SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  private static String none(String issuer, List<String> aud, Instant exp) {
    return new PlainJWT(claims(issuer, aud, exp)).serialize();
  }
}
