package com.st6.wc.projection;

import com.st6.wc.enums.RiskBadge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Manager heatmap cell — synchronous projection read-model at manager × report × week × Defining
 * Objective granularity (Appendix A / §9), maps {@code manager_heatmap_cell}. Recomputed wholesale:
 * NO audit quartet, NO {@code @Version} — owns a single non-null {@code updatedAt}. {@code
 * riskBadges} maps the {@code risk_badges text[]} column to a typed {@code List<RiskBadge>} via
 * Hibernate's {@code SqlTypes.ARRAY} + {@code @Enumerated(STRING)} (string elements); the DB {@code
 * <@} containment CHECK stays the vocabulary guard.
 */
@Entity
@Table(name = "manager_heatmap_cell")
@Getter
@Setter
public class ManagerHeatmapCell {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID managerEmployeeId;

  @Column(nullable = false)
  private UUID employeeId;

  @Column(nullable = false)
  private LocalDate weekStartDate;

  @Column(nullable = false)
  private UUID definingObjectiveId;

  @Column(nullable = false)
  private int commitmentCount;

  @Column(nullable = false)
  private int plannedCount;

  @Column(nullable = false)
  private int unplannedCount;

  @Column(nullable = false)
  private int misalignedCount;

  @Column(nullable = false)
  private int needsReviewCount;

  @Column(nullable = false)
  private int blockedCount;

  @Column(nullable = false)
  private int carryForwardCount;

  @Column(nullable = false)
  private int unresolvedDisputeCount;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Enumerated(EnumType.STRING)
  @Column(name = "risk_badges", nullable = false, columnDefinition = "text[]")
  private List<RiskBadge> riskBadges = new ArrayList<>();

  @Column(nullable = false)
  private Instant updatedAt;

  // Explicit defensive-copy accessors for the one mutable-typed field (the others are immutable
  // UUID/String/Instant/enum/primitive, so Lombok's @Getter/@Setter are fine there). Hand-written
  // here to avoid SpotBugs EI/EI2 (exposing internal representation) on the collection — Lombok
  // skips
  // generating accessors that already exist. Safe: this entity uses field access (@Id is on the
  // field), so Hibernate reads/writes the field directly and never routes through these.
  public List<RiskBadge> getRiskBadges() {
    return new ArrayList<>(riskBadges);
  }

  public void setRiskBadges(List<RiskBadge> riskBadges) {
    this.riskBadges = new ArrayList<>(Objects.requireNonNullElseGet(riskBadges, ArrayList::new));
  }
}
