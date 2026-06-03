package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st6.wc.enums.RoleType;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Auth0 claim mapper proof (task 2.2, §6 / Appendix F.1). Pure unit: builds {@link Jwt} fixtures
 * directly (no decoder/network) and maps them via config-bound claim names. Proves config-driven
 * resolution (no hardcoded claim names, REQ-S-009), the {@code sub} fallback, role
 * validate-when-present / tolerate-absent (REQ-S-007, no silent default), and that the mapper does
 * no DB access. The authoritative role + {@code isManager} are relationship-derived in 2.4 — the
 * JWT role here is a coarse hint.
 */
class Auth0ClaimMapperTest {

  private static final String EMP_CLAIM = "https://wc.test/employee_id";
  private static final String ROLE_CLAIM = "https://wc.test/role";
  private static final String EMAIL_CLAIM = "email";

  private static final Auth0Properties PROPS =
      new Auth0Properties(
          "https://api.wc.test", new Auth0Properties.Claims(EMP_CLAIM, ROLE_CLAIM, EMAIL_CLAIM));

  private final Auth0ClaimMapper mapper = new Auth0ClaimMapper(PROPS);

  // --- 1. namespaced F.1 claims -> identity ---------------------------------
  @Test
  void maps_namespaced_claims_to_identity() {
    Auth0Identity id =
        mapper.map(
            jwt(
                Map.of(
                    "sub",
                    "auth0|ignored",
                    EMP_CLAIM,
                    "auth0|emp-123",
                    ROLE_CLAIM,
                    "MANAGER",
                    EMAIL_CLAIM,
                    "mgr@x.test")));
    assertThat(id.externalSubject()).isEqualTo("auth0|emp-123");
    assertThat(id.role()).isEqualTo(RoleType.MANAGER);
    assertThat(id.email()).isEqualTo("mgr@x.test");
  }

  // --- 2. employee-id claim absent/blank -> sub fallback --------------------
  @Test
  void employee_id_claim_absent_falls_back_to_sub() {
    Auth0Identity absent =
        mapper.map(jwt(Map.of("sub", "auth0|sub-1", ROLE_CLAIM, "IC", EMAIL_CLAIM, "ic@x.test")));
    assertThat(absent.externalSubject()).isEqualTo("auth0|sub-1");
    assertThat(absent.role()).isEqualTo(RoleType.IC);

    Auth0Identity blank =
        mapper.map(jwt(Map.of("sub", "auth0|sub-2", EMP_CLAIM, "   ", ROLE_CLAIM, "IC")));
    assertThat(blank.externalSubject()).isEqualTo("auth0|sub-2");
  }

  // --- 3. custom claim names resolve (no hardcoding, REQ-S-009) -------------
  @Test
  void custom_claim_names_resolve() {
    Auth0Properties custom =
        new Auth0Properties(
            "https://api.wc.test", new Auth0Properties.Claims("uid", "rol", "mail"));
    Auth0ClaimMapper customMapper = new Auth0ClaimMapper(custom);
    Auth0Identity id =
        customMapper.map(jwt(Map.of("uid", "auth0|c-9", "rol", "MANAGER", "mail", "c@x.test")));
    assertThat(id.externalSubject()).isEqualTo("auth0|c-9");
    assertThat(id.role()).isEqualTo(RoleType.MANAGER);
    assertThat(id.email()).isEqualTo("c@x.test");
  }

  // --- 4. present-but-invalid role -> rejected (no silent default) ----------
  @Test
  void present_but_invalid_role_rejected() {
    assertThatThrownBy(
            () ->
                mapper.map(
                    jwt(Map.of("sub", "auth0|x", EMP_CLAIM, "auth0|x", ROLE_CLAIM, "ADMIN"))))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  // --- 5. absent role claim -> tolerated, role=null (no default) ------------
  @Test
  void absent_role_claim_tolerated_as_null() {
    Auth0Identity id =
        mapper.map(jwt(Map.of("sub", "auth0|y", EMP_CLAIM, "auth0|y", EMAIL_CLAIM, "y@x.test")));
    assertThat(id.role()).isNull();
    assertThat(id.externalSubject()).isEqualTo("auth0|y");
  }

  // --- 5b. blank role claim -> tolerated, role=null (not present-but-invalid)
  @Test
  void blank_role_claim_tolerated_as_null() {
    // a blank role = "no hint" → null (NOT rejected); symmetric with blank employee-id → sub.
    Auth0Identity id =
        mapper.map(jwt(Map.of("sub", "auth0|b", EMP_CLAIM, "auth0|b", ROLE_CLAIM, "   ")));
    assertThat(id.role()).isNull();
    assertThat(id.externalSubject()).isEqualTo("auth0|b");
  }

  // --- 6. email present -> mapped; absent -> null (tolerated) ---------------
  @Test
  void email_claim_extracted_or_null() {
    Auth0Identity withEmail =
        mapper.map(jwt(Map.of(EMP_CLAIM, "auth0|e", ROLE_CLAIM, "IC", EMAIL_CLAIM, "e@x.test")));
    assertThat(withEmail.email()).isEqualTo("e@x.test");

    Auth0Identity noEmail = mapper.map(jwt(Map.of(EMP_CLAIM, "auth0|e2", ROLE_CLAIM, "IC")));
    assertThat(noEmail.email()).isNull();
  }

  // --- 7. mapper does no DB access (constructor takes only Auth0Properties) -
  @Test
  void mapper_does_no_db_access() {
    Constructor<?>[] ctors = Auth0ClaimMapper.class.getDeclaredConstructors();
    assertThat(ctors).hasSize(1);
    assertThat(ctors[0].getParameterTypes()).containsExactly(Auth0Properties.class);
  }

  // --- helper ---------------------------------------------------------------
  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256");
    Map<String, Object> ordered = new LinkedHashMap<>(claims);
    ordered.forEach(builder::claim);
    return builder.build();
  }
}
