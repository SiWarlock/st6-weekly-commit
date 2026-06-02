package com.st6.wc.commitment;

import com.st6.wc.common.PersistableUuidEntity;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.WorkType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Weekly commitment — mutable lifecycle entity (Appendix A / §3 / §4), maps the {@code
 * weekly_commitment} V1 table. Carries the four contract deltas: {@code managerAlignmentNote}
 * PRESENT, NO {@code progressStatus} field, the {@code carryForwardSourceCommitmentId} self-FK, and
 * a nullable {@code supportingOutcomeId} (nullable-until-lock). {@code @Version} via {@link
 * PersistableUuidEntity}.
 */
@Entity
@Table(name = "weekly_commitment")
@Getter
@Setter
public class WeeklyCommitment extends PersistableUuidEntity {

  @Column(nullable = false)
  private UUID weeklyPlanId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CommitmentKind commitmentKind;

  @Column(nullable = false)
  private String title;

  private String description;

  private UUID supportingOutcomeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkType workType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Confidence confidence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AlignmentStatus alignmentStatus;

  private String managerAlignmentNote;

  @Enumerated(EnumType.STRING)
  private ReconciliationOutcome reconciliationOutcome;

  private String outcomeNote;

  private UUID carryForwardSourceCommitmentId;
}
