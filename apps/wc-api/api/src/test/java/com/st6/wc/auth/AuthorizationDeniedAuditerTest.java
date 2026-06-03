package com.st6.wc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.audit.AuditEvent;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.SystemPrincipal;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.support.AbstractAppBootTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The {@code @Transactional(REQUIRES_NEW)} denial-audit guarantee (task 2.5, consumes the 2.3
 * carry-forward) proven against a real Spring proxy + real PG16 ({@link AbstractAppBootTest}). A
 * denial audit MUST survive when the surrounding (denied) mutation's transaction rolls back —
 * otherwise the rule-#3 denial record is lost with the rolled-back mutation. Also pins the actor
 * resolution: {@link UserPrincipal} → the employee id; {@link SystemPrincipal} → {@code null}.
 *
 * <p>Full context (DB-backed, security autoconfig excluded — the real {@code SecurityFilterChain}
 * is 2.6), so the auditer is a genuine proxied bean: its {@code REQUIRES_NEW} actually suspends the
 * outer transaction and commits independently. Writes commit, so each test cleans up.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,"
            + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@ActiveProfiles("demo")
class AuthorizationDeniedAuditerTest extends AbstractAppBootTest {

  @Autowired private AuthorizationDeniedAuditer auditer;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private EmployeeRepository employees;
  @Autowired private PlatformTransactionManager txManager;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll(); // only this boot-test context commits audit rows
    employees.deleteAll();
  }

  // --- 1. the denial audit survives a rolled-back outer (mutation) transaction (REQUIRES_NEW) ----
  @Test
  void denialAuditSurvivesRolledBackMutation() {
    UUID entityId = UUID.randomUUID();
    TransactionTemplate tx = new TransactionTemplate(txManager);

    tx.executeWithoutResult(
        status -> {
          // SYSTEM actor -> actor_employee_id is null (no employee FK needed)
          auditer.recordDenial(SystemPrincipal.INSTANCE, "Plan", entityId, "cross_owner");
          status.setRollbackOnly(); // the surrounding mutation rolls back
        });

    List<AuditEvent> survived =
        auditEvents.findAll().stream().filter(a -> entityId.equals(a.getEntityId())).toList();
    assertThat(survived).hasSize(1); // committed in its own REQUIRES_NEW tx — NOT rolled back
    assertThat(survived.get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
    assertThat(survived.get(0).getActorEmployeeId()).isNull();
  }

  // --- 2. actor resolution: UserPrincipal -> employeeId; SystemPrincipal -> null ----
  @Test
  void denialAuditWrittenUnderActingPrincipal_actorNullableForSystem() {
    Employee actor = employees.saveAndFlush(employee()); // FK: actor_employee_id -> employee(id)
    UUID userEntityId = UUID.randomUUID();
    UUID systemEntityId = UUID.randomUUID();

    auditer.recordDenial(
        new UserPrincipal(actor.getId(), RoleType.IC, false), "Commitment", userEntityId, "denied");
    auditer.recordDenial(SystemPrincipal.INSTANCE, "SyncRecord", systemEntityId, "denied");

    assertThat(findByEntityId(userEntityId).getActorEmployeeId()).isEqualTo(actor.getId());
    assertThat(findByEntityId(systemEntityId).getActorEmployeeId()).isNull();
  }

  private AuditEvent findByEntityId(UUID entityId) {
    return auditEvents.findAll().stream()
        .filter(a -> entityId.equals(a.getEntityId()))
        .findFirst()
        .orElseThrow();
  }

  private Employee employee() {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(RoleType.IC);
    e.setActive(true);
    return e;
  }
}
