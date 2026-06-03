@REQ-E-005 @REQ-F-028
# Seed fixture: R5 Grace Liu — current-week RECONCILING plan + prior-week RECONCILED plan
# carrying C_src ("Draft the activation-onboarding runbook", SO-1.2, reconciliation_outcome
# CARRIED_FORWARD) -> current-week successor C_next (carry_forward_source_commitment_id = C_src.id).
# Pins ARCHITECTURE §3 reconciliation + §5 close preconditions; the load-bearing REQ-E-005
# baseline-unchanged invariant (locked prior-week planned fields are immutable — safety rule #2).
Feature: IC reconciles outcomes, adds unplanned work, carries forward — locked baseline unchanged
  As an IC reconciling the current week
  I want to record outcomes, add unplanned work, and carry an unfinished commitment forward
  So that next week is seeded without ever mutating my locked prior-week baseline

  Background:
    Given the deterministic demo seed is loaded
    And I am logged in as persona "Grace"

  Scenario: Recording outcomes, adding an unplanned commitment, and carrying forward preserves the locked baseline
    Given I open my current weekly plan
    And the plan is in state "RECONCILING"
    When I record the reconciliation outcome "COMPLETED" on a planned commitment
    And I record the reconciliation outcome "BLOCKED" on a planned commitment
    And I add an UNPLANNED commitment linked to Supporting Outcome "SO-2.2"
    And I carry forward an unfinished commitment to next week
    Then the reconciliation outcomes are persisted
    And the unplanned commitment is labeled "UNPLANNED" and linked to "SO-2.2"
    And the carried-forward successor links back to its source commitment
    And the locked prior-week baseline is unchanged
