package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.support.SharedPostgres;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real-mode {@code SecurityFilterChain} integration proof (task 2.6, SAFETY-touching — rule #5
 * operational half) against the real chain + real PG16. With {@code demo-auth.enabled=false} the
 * chain runs the OAuth2 resource server (a real {@code withIssuerLocation} decoder, issuer/JWKS
 * served by a {@link MockWebServer}) PLUS the {@code DemoAuthFilter} as a
 * backdoor-<em>rejector</em>. Proves: (rule #5) a demo header in prod is rejected 403 + one audit;
 * a valid RS256 token authenticates end-to-end as a {@link UserPrincipal} (rule #3 positive path);
 * and an unauthenticated {@code /api/**} request → 401 problem+json.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityChainRealModeTest {

  private static final String AUDIENCE = "https://api.wc.test";
  private static final String KID = "real-key";
  private static final String SUBJECT = "auth0|realuser";

  private static final MockWebServer ISSUER = new MockWebServer();
  private static final RSAPublicKey PUBLIC_KEY;
  private static final RSAPrivateKey PRIVATE_KEY;

  static {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair kp = gen.generateKeyPair();
      PUBLIC_KEY = (RSAPublicKey) kp.getPublic();
      PRIVATE_KEY = (RSAPrivateKey) kp.getPrivate();
      ISSUER.start(); // must start before url() is valid
      ISSUER.setDispatcher(dispatcher(ISSUER.url("/").toString()));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    // real mode + the MockWebServer issuer (eager discovery at decoder build) + audience
    registry.add("demo-auth.enabled", () -> "false");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER.url("/").toString());
    registry.add("auth0.audience", () -> AUDIENCE);
    // real PG16 (the chain + audit + resolver are DB-backed)
    registry.add("spring.datasource.url", SharedPostgres.INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", SharedPostgres.INSTANCE::getUsername);
    registry.add("spring.datasource.password", SharedPostgres.INSTANCE::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @TestConfiguration
  static class Endpoints {
    @Bean
    PingController pingController() {
      return new PingController();
    }
  }

  @RestController
  static class PingController {
    @GetMapping("/api/test/ping")
    String ping(@AuthenticationPrincipal UserPrincipal principal) {
      return principal == null ? "anonymous" : principal.getClass().getSimpleName();
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(String externalSubject, RoleType role, boolean active) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setExternalSubject(externalSubject);
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(role);
    e.setActive(active);
    return employees.saveAndFlush(e);
  }

  // --- 6. (rule #5 operational) a demo header in REAL mode -> 403 + one audit, never authenticates
  // -
  @Test
  void demoHeaderInRealMode_rejected403_audit() throws Exception {
    assertThat(auditEvents.count()).isZero();
    mvc.perform(get("/api/test/ping").header("X-Demo-Employee-Id", UUID.randomUUID().toString()))
        .andExpect(status().isForbidden());
    assertThat(auditEvents.count()).isEqualTo(1); // the production-backdoor control fires in prod
  }

  // --- ADD. (rule #3 positive) a valid RS256 token authenticates as a UserPrincipal ----
  @Test
  void realMode_validJwtAuthenticatesAsUserPrincipal_reachesEndpoint() throws Exception {
    saveEmployee(SUBJECT, RoleType.MANAGER, true);
    String token = rs256(ISSUER.url("/").toString(), List.of(AUDIENCE), SUBJECT);

    mvc.perform(get("/api/test/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("UserPrincipal"));
  }

  // --- 8. unauthenticated /api/** in real mode -> 401 problem+json ----
  @Test
  void unauthenticatedRealModeApiRequest_401() throws Exception {
    mvc.perform(get("/api/test/ping")).andExpect(status().isUnauthorized());
  }

  // ===== JWKS/discovery stub + token signing =====

  private static Dispatcher dispatcher(String issuer) throws Exception {
    String jwks =
        "{\"keys\":["
            + new RSAKey.Builder(PUBLIC_KEY).keyID(KID).build().toPublicJWK().toJSONString()
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

  private static String rs256(String issuer, List<String> aud, String subject) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .audience(aud)
            .issueTime(Date.from(Instant.now().minusSeconds(30)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    SignedJWT jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims);
    jwt.sign(new RSASSASigner(PRIVATE_KEY));
    return jwt.serialize();
  }
}
