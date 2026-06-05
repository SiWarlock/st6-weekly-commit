package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.support.AbstractAppBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Batch-job boot proof (deploy-fix #9, SAFETY-ADJACENT). The non-serving batch jobs (migration /
 * rebuild-projections / cronjob) run the api image with {@code web-application-type=none} (infra
 * #8), in REAL mode ({@code demo-auth.enabled} unset → {@code matchIfMissing}) with NO Auth0 {@code
 * issuer-uri}/{@code audience} (Jobs aren't given those — they don't serve HTTP).
 *
 * <p>{@code @EnableWebSecurity} forces {@code SecurityConfig}'s {@code SecurityFilterChain} beans +
 * the eager {@code JwtConfig.jwtDecoder} singleton to build <strong>regardless of {@code
 * web-application-type}</strong>, so before the #9 web-gate this context fail-boots on the
 * decoder's {@code requireRealModeConfig(issuer-uri)} (the live migration crash). After the
 * {@code @ConditionalOnWebApplication(SERVLET)} gate on BOTH configs, they are excluded in {@code
 * web=none} → no {@code JwtDecoder}, no {@code SecurityFilterChain} → clean boot → Flyway runs.
 *
 * <p>RED today: the context fails to load (the eager jwtDecoder throws). Reuses the {@link
 * AbstractAppBootTest} {@code SharedPostgres} datasource (the batch jobs are DB-bound; the
 * datasource still loads under {@code web=none}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BatchContextWebGateTest extends AbstractAppBootTest {

  @Autowired private ApplicationContext ctx;

  @Test
  void batchContext_webNone_realMode_noAuth0_loadsClean_noSecurityBeans() {
    // Reaching here proves the non-web context LOADED — no IllegalStateException from the eager
    // jwtDecoder's real-mode config requirement (the batch jobs no longer require Auth0).
    assertThat(ctx.getBeanNamesForType(JwtDecoder.class))
        .as("no Auth0 JwtDecoder in a non-serving (web=none) batch context")
        .isEmpty();
    assertThat(ctx.getBeanNamesForType(SecurityFilterChain.class))
        .as("no SecurityFilterChain in a non-serving (web=none) batch context")
        .isEmpty();
  }
}
