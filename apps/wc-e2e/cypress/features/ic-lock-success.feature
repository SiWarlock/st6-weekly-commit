@REQ-F-007
# Seed fixture: R6 Sam Carter — DRAFT plan (continues ic-lock-blocked-unlinked: link the
# previously-unlinked planned commitment to a Supporting Outcome, then lock).
# Pins ARCHITECTURE §3 lifecycle DRAFT->LOCKED + §5 lock success; F.3 review-SLA (next
# business day 17:00 CT); safety rule #2 (locked baseline immutable / read-only after lock).
Feature: IC locks a fully-linked weekly plan and the baseline freezes
  As an IC who has linked every planned commitment to a Supporting Outcome
  I want to lock my weekly plan
  So that my committed baseline is frozen and the manager review SLA starts

  Background:
    Given the deterministic demo seed is loaded
    And I am logged in as persona "Sam"

  Scenario: Linking the last unlinked commitment then locking succeeds and freezes the baseline
    Given I open my current weekly plan
    And the plan is in state "DRAFT"
    And the plan has a planned commitment with no Supporting Outcome
    When I link that commitment to Supporting Outcome "SO-1.2"
    And I attempt to lock the plan
    Then the lock succeeds
    And the plan is in state "LOCKED"
    And I see the review-due date
    And the locked baseline is read-only
