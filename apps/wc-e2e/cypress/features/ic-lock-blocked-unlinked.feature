@REQ-E-001 @REQ-T-010
# Seed fixture: R6 Sam Carter (sam.carter@st6demo.com) — DRAFT plan, 2 planned commitments,
# 1 deliberately UNLINKED (supporting_outcome_id NULL). Appendix E state matrix.
# Pins safety rule #1 (required Supporting Outcome at lock) + ARCHITECTURE §5 lock preconditions,
# Appendix B.21 error code UNLINKED_PLANNED_COMMITMENT (409, IDOR-safe RFC-7807).
Feature: Lock is blocked when a planned commitment has no Supporting Outcome
  As an IC whose weekly plan still has an unlinked planned commitment
  I want the lock to be rejected with a clear, strategy-anchored reason
  So that every committed week maps to the RCDO hierarchy (safety rule #1)

  Background:
    Given the deterministic demo seed is loaded
    And I am logged in as persona "Sam"

  Scenario: Locking a DRAFT plan that has an unlinked planned commitment is rejected
    Given I open my current weekly plan
    And the plan is in state "DRAFT"
    And the plan has a planned commitment with no Supporting Outcome
    When I attempt to lock the plan
    Then the lock is rejected
    And I see the error code "UNLINKED_PLANNED_COMMITMENT"
    And I see the safe message "Every planned commitment must link to a Supporting Outcome before you can lock this plan."
    And the plan remains in state "DRAFT"
