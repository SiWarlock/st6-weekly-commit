package com.st6.wc.identity;

import com.st6.wc.enums.RoleType;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The resolved IC/Manager user principal (task 2.4, §6 / REQ-S-007). Produced by {@link
 * PrincipalResolver} from a validated-JWT {@code Auth0Identity} or a demo employee id, and consumed
 * uniformly by 2.5 ({@code DomainAuthorizationService}), 2.6 (placed into the {@code
 * SecurityContext}), and 2.7 ({@code MeDto}). Named to pair with {@link SystemPrincipal} (the
 * no-HTTP system actor).
 *
 * <p>Load-bearing semantics: {@code role} is the <strong>authoritative</strong> {@code
 * Employee.role} (DB row), never the coarse nullable JWT role hint; {@code isManager} is
 * <strong>relationship-driven</strong> (an active {@code manager_relationship} as manager exists),
 * never the role claim — a {@code MANAGER}-role employee with no active managed report is {@code
 * isManager=false}. Implements Spring Security's {@link AuthenticatedPrincipal} so that, once 2.6
 * places it in the {@code Authentication}, {@code authentication.getName()} delegates to {@link
 * #getName()} = the stable employee id (read by the §15 audit actor + logging).
 */
public record UserPrincipal(UUID employeeId, RoleType role, boolean isManager)
    implements AuthenticatedPrincipal {

  @Override
  public String getName() {
    return employeeId.toString();
  }
}
