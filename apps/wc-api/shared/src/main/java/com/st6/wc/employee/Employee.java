package com.st6.wc.employee;

import com.st6.wc.common.AbstractAuditingEntity;
import com.st6.wc.enums.RoleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Employee — IC or manager identity (Appendix A / §3 / §4), maps the {@code employee} V1 table.
 * Audited but NOT versioned (no optimistic-lock {@code @Version}): extends {@link
 * AbstractAuditingEntity} (audit quartet) with an inline app-assigned UUID {@code @Id} (no
 * {@code @GeneratedValue}).
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
public class Employee extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  private String externalSubject;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RoleType role;

  @Column(nullable = false)
  private boolean active;

  private String timezone;
}
