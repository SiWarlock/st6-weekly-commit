package com.st6.wc.commitment.dto;

import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.WorkType;
import java.util.UUID;
import lombok.Getter;

/**
 * The E6 {@code PATCH /api/commitments/{id}} request (task 3.4b, Appendix B.6 / Appendix E Part 1)
 * — a <strong>partial</strong> update with three-way presence semantics: absent / present-null /
 * present-value must be distinguishable so the service can leave a field untouched, clear/unlink a
 * nullable field, or set it.
 *
 * <p>A plain mutable bean (not a {@code record} + {@code Optional}) <em>by design</em>: Jackson
 * collapses an absent {@code Optional} creator parameter into {@code Optional.empty()} — identical
 * to a present JSON {@code null} — so an {@code Optional}-record cannot tell "absent" from
 * "explicit null", and Hibernate Validator's {@code OptionalValueExtractor} feeds {@code null} from
 * an empty {@code Optional} into {@code @NotBlank}, spuriously rejecting absent fields. Instead
 * each setter records that its key was present in the JSON (Jackson only calls a setter for a
 * property that appears in the body): {@code xxxProvided()} == true ⇒ the client sent the key
 * (value or {@code null}); the value is read via the getter. Validation + normalization live in
 * {@link com.st6.wc.commitment.CommitmentService} (reusing {@link
 * com.st6.wc.commitment.TextNormalizer}'s normalize/code-point/control helpers on the present,
 * normalized value — uniform with E5); unknown enum values fail JSON deserialization → 400 via the
 * {@code HttpMessageNotReadable} handler. User text is stored RAW (React-escapes downstream, §16).
 */
public class PatchCommitmentRequest {

  @Getter private String title;
  @Getter private String description;
  @Getter private UUID supportingOutcomeId;
  @Getter private Priority priority;
  @Getter private WorkType workType;
  @Getter private Confidence confidence;
  @Getter private AlignmentStatus alignmentStatus;
  @Getter private ReconciliationOutcome reconciliationOutcome;
  @Getter private String outcomeNote;

  private boolean titleProvided;
  private boolean descriptionProvided;
  private boolean supportingOutcomeIdProvided;
  private boolean priorityProvided;
  private boolean workTypeProvided;
  private boolean confidenceProvided;
  private boolean alignmentStatusProvided;
  private boolean reconciliationOutcomeProvided;
  private boolean outcomeNoteProvided;

  public void setTitle(String title) {
    this.title = title;
    this.titleProvided = true;
  }

  public void setDescription(String description) {
    this.description = description;
    this.descriptionProvided = true;
  }

  public void setSupportingOutcomeId(UUID supportingOutcomeId) {
    this.supportingOutcomeId = supportingOutcomeId;
    this.supportingOutcomeIdProvided = true;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
    this.priorityProvided = true;
  }

  public void setWorkType(WorkType workType) {
    this.workType = workType;
    this.workTypeProvided = true;
  }

  public void setConfidence(Confidence confidence) {
    this.confidence = confidence;
    this.confidenceProvided = true;
  }

  public void setAlignmentStatus(AlignmentStatus alignmentStatus) {
    this.alignmentStatus = alignmentStatus;
    this.alignmentStatusProvided = true;
  }

  public void setReconciliationOutcome(ReconciliationOutcome reconciliationOutcome) {
    this.reconciliationOutcome = reconciliationOutcome;
    this.reconciliationOutcomeProvided = true;
  }

  public void setOutcomeNote(String outcomeNote) {
    this.outcomeNote = outcomeNote;
    this.outcomeNoteProvided = true;
  }

  // Non-bean-style accessors (not getX/isX) so Jackson never treats presence as a JSON property.

  public boolean titleProvided() {
    return titleProvided;
  }

  public boolean descriptionProvided() {
    return descriptionProvided;
  }

  public boolean supportingOutcomeIdProvided() {
    return supportingOutcomeIdProvided;
  }

  public boolean priorityProvided() {
    return priorityProvided;
  }

  public boolean workTypeProvided() {
    return workTypeProvided;
  }

  public boolean confidenceProvided() {
    return confidenceProvided;
  }

  public boolean alignmentStatusProvided() {
    return alignmentStatusProvided;
  }

  public boolean reconciliationOutcomeProvided() {
    return reconciliationOutcomeProvided;
  }

  public boolean outcomeNoteProvided() {
    return outcomeNoteProvided;
  }
}
