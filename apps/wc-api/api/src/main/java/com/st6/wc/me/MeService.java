package com.st6.wc.me;

import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.me.dto.MeDto;
import org.springframework.stereotype.Service;

/**
 * Builds the {@link MeDto} for the resolved {@link UserPrincipal} (task 2.7, §5 E1 / Appendix B.3).
 * The principal carries the authoritative {@code role} + relationship-driven {@code isManager}
 * (2.4/2.6); the display fields (email/displayName/timezone) are reloaded from the {@link Employee}
 * row with one {@code findById}. {@code persona} = {@code email} (the stable per-identity key; a
 * persona switch yields a different email → a different MeDto, REQ-F-032). Returns a DTO, never the
 * entity (forbidden-pattern #3).
 */
@Service
public class MeService {

  private final EmployeeRepository employees;

  public MeService(EmployeeRepository employees) {
    this.employees = employees;
  }

  public MeDto toMeDto(UserPrincipal principal) {
    Employee employee =
        employees
            .findById(principal.employeeId())
            // a resolved principal always has a row; a vanished one is IDOR-safe 404, never a 500.
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    return new MeDto(
        employee.getId(),
        employee.getEmail(),
        employee.getDisplayName(),
        principal.role(), // authoritative role (NOT re-derived)
        employee.getEmail(), // persona = email
        principal.isManager(), // relationship-driven (NOT re-derived)
        employee.getTimezone());
  }
}
