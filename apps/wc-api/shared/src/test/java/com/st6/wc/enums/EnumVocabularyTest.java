package com.st6.wc.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Executable enforcement of the cross-doc enum vocabulary (ARCHITECTURE.md Appendix A / B.1,
 * REQ-D-010). Each enum is the Java mirror of a VARCHAR+CHECK column vocabulary; drift here means
 * the code and the DB/contract silently disagree.
 */
class EnumVocabularyTest {

  static Stream<Arguments> enumVocabulary() {
    return Stream.of(
        Arguments.of(PlanState.class, Set.of("DRAFT", "LOCKED", "RECONCILING", "RECONCILED")),
        Arguments.of(RoleType.class, Set.of("IC", "MANAGER")),
        Arguments.of(CommitmentKind.class, Set.of("PLANNED", "UNPLANNED")),
        Arguments.of(Priority.class, Set.of("P0", "P1", "P2")),
        Arguments.of(WorkType.class, Set.of("STRATEGIC", "MAINTENANCE", "BLOCKER", "UNPLANNED")),
        Arguments.of(Confidence.class, Set.of("HIGH", "MEDIUM", "LOW")),
        Arguments.of(AlignmentStatus.class, Set.of("ALIGNED", "NEEDS_REVIEW", "MISALIGNED")),
        Arguments.of(
            ReviewStatus.class, Set.of("NOT_REVIEWED", "REVIEWED_WITH_DISPUTES", "REVIEWED")),
        Arguments.of(DisputeStatus.class, Set.of("OPEN", "IC_RESPONDED", "RESOLVED")),
        Arguments.of(
            ReconciliationOutcome.class,
            Set.of("COMPLETED", "PARTIALLY_COMPLETED", "BLOCKED", "CANCELED", "CARRIED_FORWARD")),
        Arguments.of(FlagType.class, Set.of("NEEDS_REVISION", "MISALIGNED")),
        Arguments.of(CommentTargetType.class, Set.of("PLAN", "COMMITMENT")),
        Arguments.of(SyncRelatedType.class, Set.of("WEEKLY_PLAN", "MANAGER_REVIEW_WEEK")),
        Arguments.of(
            EventKind.class, Set.of("IC_PLANNING", "IC_RECONCILIATION", "MANAGER_REVIEW_BLOCK")),
        Arguments.of(
            SyncStatus.class,
            Set.of("PENDING_PUBLISH", "QUEUED", "SYNCING", "SYNCED", "FAILED", "RETRY_REQUESTED")),
        Arguments.of(
            RiskBadge.class,
            Set.of(
                "MISALIGNED",
                "NEEDS_REVIEW",
                "BLOCKED",
                "CARRY_FORWARD",
                "UNREVIEWED",
                "OVERDUE_REVIEW")));
  }

  @ParameterizedTest(name = "{0} value set matches Appendix B.1")
  @MethodSource("enumVocabulary")
  void enum_value_sets_match_appendixB1(Class<? extends Enum<?>> enumType, Set<String> expected) {
    Set<String> actual =
        Arrays.stream(enumType.getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(
        expected, actual, enumType.getSimpleName() + " value set drifted from Appendix B.1");
  }

  @Test
  void reviewstatus_has_no_overdue() {
    // §3: OVERDUE is derived at read time (now > reviewDueAt AND NOT_REVIEWED), never stored/wired.
    assertThrows(IllegalArgumentException.class, () -> ReviewStatus.valueOf("OVERDUE"));
  }

  @Test
  void syncrelatedtype_uses_manager_review_week_not_manager_review() {
    // §10: the per-manager/week review block keys on MANAGER_REVIEW_WEEK, not MANAGER_REVIEW.
    assertThrows(IllegalArgumentException.class, () -> SyncRelatedType.valueOf("MANAGER_REVIEW"));
    assertEquals("MANAGER_REVIEW_WEEK", SyncRelatedType.MANAGER_REVIEW_WEEK.name());
  }

  @Test
  void commenttargettype_is_exactly_two() {
    // §11: flat MVP comments target only PLAN or COMMITMENT (not RallyCry/DefiningObjective/etc.).
    assertEquals(2, CommentTargetType.values().length);
    assertThrows(IllegalArgumentException.class, () -> CommentTargetType.valueOf("RALLY_CRY"));
  }

  @Test
  void riskbadge_is_exactly_six() {
    assertEquals(6, RiskBadge.values().length);
  }
}
