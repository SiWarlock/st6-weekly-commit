package com.st6.wc.me.dto;

import com.st6.wc.enums.RoleType;
import java.util.UUID;

/**
 * The current authenticated identity (task 2.7, §5 E1 response / Appendix B.3). The <strong>first
 * DTO across the API boundary</strong> — a record mirroring B.3 verbatim, never a JPA entity
 * (forbidden-pattern #3). {@code role} is the authoritative {@code Employee.role}; {@code
 * isManager} is relationship-driven (≥1 active direct report, 2.4 — gates manager surfaces); {@code
 * persona} is the active persona key ({@code email} in both modes for MVP); {@code timezone} is
 * nullable.
 */
public record MeDto(
    UUID employeeId,
    String email,
    String displayName,
    RoleType role,
    String persona,
    boolean isManager,
    String timezone) {}
