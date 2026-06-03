package com.st6.wc.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.me.dto.MeDto;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code MeService} unit proof (task 2.7, §5 E1 / Appendix B.3). Maps the resolved {@link
 * UserPrincipal} (authoritative {@code role} + relationship-driven {@code isManager}, from 2.4/2.6)
 * plus the reloaded {@link Employee} (display fields) into the {@link MeDto} record — never an
 * entity across the boundary (forbidden-pattern #3). {@code persona} = {@code email} (the stable
 * per-identity key; a persona switch yields a different email → a different MeDto, REQ-F-032).
 */
class MeServiceTest {

  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final MeService meService = new MeService(employees);

  private static Employee employee(
      UUID id, String email, String displayName, RoleType role, String tz) {
    Employee e = new Employee();
    e.setId(id);
    e.setEmail(email);
    e.setDisplayName(displayName);
    e.setRole(role);
    e.setActive(true);
    e.setTimezone(tz);
    return e;
  }

  @Test
  void toMeDto_ic_mapsAllFields() {
    UUID id = UUID.randomUUID();
    when(employees.findById(id))
        .thenReturn(
            Optional.of(employee(id, "ada@x.test", "Ada", RoleType.IC, "America/New_York")));

    MeDto dto = meService.toMeDto(new UserPrincipal(id, RoleType.IC, false));

    assertThat(dto.employeeId()).isEqualTo(id);
    assertThat(dto.email()).isEqualTo("ada@x.test");
    assertThat(dto.displayName()).isEqualTo("Ada");
    assertThat(dto.role()).isEqualTo(RoleType.IC); // authoritative role from the principal
    assertThat(dto.persona()).isEqualTo("ada@x.test"); // persona = email (stable per-identity key)
    assertThat(dto.isManager()).isFalse();
    assertThat(dto.timezone()).isEqualTo("America/New_York");
  }

  @Test
  void toMeDto_manager_isManagerTrueFromPrincipal() {
    UUID id = UUID.randomUUID();
    when(employees.findById(id))
        .thenReturn(Optional.of(employee(id, "boss@x.test", "Boss", RoleType.MANAGER, null)));

    // isManager is relationship-driven (the principal carries it, 2.4) — NOT re-derived here.
    MeDto dto = meService.toMeDto(new UserPrincipal(id, RoleType.MANAGER, true));

    assertThat(dto.role()).isEqualTo(RoleType.MANAGER);
    assertThat(dto.isManager()).isTrue();
    assertThat(dto.timezone()).isNull(); // nullable (B.3)
  }

  @Test
  void toMeDto_missingEmployee_throws404() {
    UUID id = UUID.randomUUID();
    when(employees.findById(id)).thenReturn(Optional.empty());

    // defensive: a resolved principal always has a row, but a vanished employee -> IDOR-safe 404.
    assertThatThrownBy(() -> meService.toMeDto(new UserPrincipal(id, RoleType.IC, false)))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
  }
}
