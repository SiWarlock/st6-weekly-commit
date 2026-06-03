package com.st6.wc.review.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.enums.ReviewStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Shape proof for the B.7 {@link ManagerReviewDto} record (task 3.3a). The DTO is introduced now
 * only as the type {@code WeeklyPlanDto.managerReview} needs (mapped null while DRAFT; 3.5 wires
 * the entity→DTO {@code ReviewMapper} + {@code isOverdue}). This pins the B.7 field set + the §22
 * defensive-copy: {@code allowedActions} is copied to an immutable list at construction.
 */
class ManagerReviewDtoTest {

  @Test
  void holdsB7Fields_andAllowedActionsIsImmutableCopy() {
    UUID id = UUID.randomUUID();
    List<AllowedAction> mutable = new ArrayList<>(List.of(AllowedAction.MARK_REVIEWED));
    ManagerReviewDto dto =
        new ManagerReviewDto(
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            ReviewStatus.NOT_REVIEWED,
            Instant.parse("2026-06-02T22:00:00Z"),
            false,
            null,
            null,
            0,
            mutable,
            0L);

    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.status()).isEqualTo(ReviewStatus.NOT_REVIEWED);
    assertThat(dto.isOverdue()).isFalse();
    assertThat(dto.allowedActions()).containsExactly(AllowedAction.MARK_REVIEWED);
    // defensive copy (§22): mutating the source list does not leak into the record
    mutable.clear();
    assertThat(dto.allowedActions()).containsExactly(AllowedAction.MARK_REVIEWED);
    assertThatThrownBy(() -> dto.allowedActions().add(AllowedAction.LOCK))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
