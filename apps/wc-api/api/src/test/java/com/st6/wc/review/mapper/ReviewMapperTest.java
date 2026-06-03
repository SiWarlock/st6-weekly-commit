package com.st6.wc.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.dto.ManagerReviewDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code ReviewMapper} unit proof (task 3.5, Appendix B.7 / §3 rule #6 / §17) — entity → DTO with a
 * <strong>derived {@code isOverdue}</strong> computed at read time over an injectable {@link Clock}
 * ({@code now > reviewDueAt AND status=NOT_REVIEWED}); never stored (no {@code
 * ReviewStatus.OVERDUE}). {@code REVIEWED}/{@code REVIEWED_WITH_DISPUTES} are never overdue even
 * past the due date.
 */
class ReviewMapperTest {

  private static final Instant DUE = Instant.parse("2026-06-08T22:00:00Z"); // 17:00 CT

  private static Clock at(String iso) {
    return Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
  }

  private static ManagerReview review(ReviewStatus status) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(UUID.randomUUID());
    r.setManagerEmployeeId(UUID.randomUUID());
    r.setStatus(status);
    r.setReviewDueAt(DUE);
    r.setVersion(0L);
    return r;
  }

  // --- NOT_REVIEWED + now AFTER due → isOverdue true ----
  @Test
  void notReviewed_pastDue_isOverdue() {
    ReviewMapper mapper = new ReviewMapper(at("2026-06-09T00:00:00Z"));
    assertThat(mapper.toDto(review(ReviewStatus.NOT_REVIEWED), 0).isOverdue()).isTrue();
  }

  // --- NOT_REVIEWED + now BEFORE/AT due → not overdue ----
  @Test
  void notReviewed_beforeDue_notOverdue() {
    ReviewMapper mapper = new ReviewMapper(at("2026-06-08T12:00:00Z"));
    assertThat(mapper.toDto(review(ReviewStatus.NOT_REVIEWED), 0).isOverdue()).isFalse();
  }

  // --- REVIEWED_WITH_DISPUTES is NEVER overdue, even past due (rule #6 / §9) ----
  @Test
  void reviewedWithDisputes_pastDue_notOverdue() {
    ReviewMapper mapper = new ReviewMapper(at("2026-06-09T00:00:00Z"));
    assertThat(mapper.toDto(review(ReviewStatus.REVIEWED_WITH_DISPUTES), 2).isOverdue()).isFalse();
  }

  // --- REVIEWED is never overdue ----
  @Test
  void reviewed_pastDue_notOverdue() {
    ReviewMapper mapper = new ReviewMapper(at("2026-06-09T00:00:00Z"));
    assertThat(mapper.toDto(review(ReviewStatus.REVIEWED), 0).isOverdue()).isFalse();
  }

  // --- maps the entity fields verbatim + empty allowedActions (no IC affordance at 3.5) ----
  @Test
  void mapsFields_emptyAllowedActions() {
    ReviewMapper mapper = new ReviewMapper(at("2026-06-08T12:00:00Z"));
    ManagerReview r = review(ReviewStatus.NOT_REVIEWED);
    r.setSummaryNote("note");

    ManagerReviewDto dto = mapper.toDto(r, 3);

    assertThat(dto.id()).isEqualTo(r.getId());
    assertThat(dto.weeklyPlanId()).isEqualTo(r.getWeeklyPlanId());
    assertThat(dto.managerEmployeeId()).isEqualTo(r.getManagerEmployeeId());
    assertThat(dto.status()).isEqualTo(ReviewStatus.NOT_REVIEWED);
    assertThat(dto.reviewDueAt()).isEqualTo(DUE);
    assertThat(dto.summaryNote()).isEqualTo("note");
    assertThat(dto.unresolvedDisputeCount()).isEqualTo(3);
    assertThat(dto.allowedActions()).isEmpty();
    assertThat(dto.version()).isZero();
  }
}
