@REQ-E-004 @REQ-I-005
# Seed fixture: R2 Marco Bellini — one outlook_calendar_sync_record event_kind=IC_PLANNING,
# related_type=WEEKLY_PLAN, status=FAILED, failure_code=GRAPH_FORBIDDEN, retry_count=1,
# graph_event_id NULL, safe_message="Calendar sync failed; you can retry." (no token/secret/PII).
# Pins safety rule #4 (Outlook sync failure NEVER blocks the core lifecycle) + safety rule #7
# (no secret leak) + ARCHITECTURE §10; manual retry POST /api/outlook-sync/{id}/retry (FAILED->RETRY_REQUESTED).
Feature: Outlook sync failure shows a safe retryable warning — core workflow unaffected
  As an IC whose calendar sync failed
  I want a safe, non-leaking warning and a manual retry affordance
  So that I can retry without the sync failure ever blocking my plan lifecycle (safety rule #4)

  Background:
    Given the deterministic demo seed is loaded
    And I am logged in as persona "Marco"

  Scenario: A FAILED sync surfaces a safe retryable warning and the lifecycle stays successful
    Given I open my current weekly plan
    Then I see a calendar sync warning
    And the warning shows the safe message "Calendar sync failed; you can retry."
    And the warning contains no token or secret detail
    And my plan lifecycle is unaffected by the sync failure
    When I trigger a manual sync retry
    Then the sync retry is accepted
    And my plan lifecycle remains successful
