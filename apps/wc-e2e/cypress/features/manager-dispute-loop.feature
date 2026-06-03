@REQ-T-011 @REQ-F-017
# Seed fixtures: R3 Aisha Khan (LOCKED, REVIEWED_WITH_DISPUTES derived, 1 OPEN dispute
# flag_type=MISALIGNED, manager_note set, ic_response NULL) + manager Dana Okafor.
# Pins ARCHITECTURE §3 dispute state machine OPEN->IC_RESPONDED->RESOLVED (IC response never
# resolves) + §5; safety rule #6 (one unresolved dispute per commitment); the IC-cannot-resolve
# authorization denial (IC_CANNOT_RESOLVE_DISPUTE, 403) and the review re-derivation.
Feature: Manager dispute loop — flag, IC responds (cannot resolve), manager resolves
  As a manager and a direct report working a structured-correction loop
  I want a flag to move through IC response and manager resolution
  So that misalignment is corrected without the IC being able to resolve their own dispute

  Background:
    Given the deterministic demo seed is loaded
    And manager "Dana" has an OPEN dispute on a commitment owned by direct report "Aisha"

  Scenario: A misalignment dispute moves OPEN -> IC_RESPONDED -> RESOLVED and the review re-derives to REVIEWED
    Given I am logged in as persona "Aisha"
    And I open the open dispute on my commitment
    And the dispute is in state "OPEN"
    When I respond to the dispute by revising the Supporting Outcome
    Then the dispute is in state "IC_RESPONDED"
    And the dispute is not resolved
    When I attempt to resolve the dispute as the IC
    Then the action is denied with error code "IC_CANNOT_RESOLVE_DISPUTE"
    And the dispute is in state "IC_RESPONDED"
    When I am logged in as persona "Dana"
    And I resolve the dispute
    Then the dispute is in state "RESOLVED"
    And the manager review re-derives from "REVIEWED_WITH_DISPUTES" to "REVIEWED"
