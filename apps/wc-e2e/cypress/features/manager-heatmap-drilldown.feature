@REQ-E-003 @REQ-F-022
# Seed fixture: manager Dana Okafor + seeded manager_heatmap_cell rows spanning all three
# Defining Objectives with risk_badges drawn ONLY from the enumerated vocabulary:
# MISALIGNED + NEEDS_REVIEW (R3 Aisha), OVERDUE_REVIEW + UNREVIEWED (R2 Marco),
# BLOCKED + CARRY_FORWARD (R5 Grace). Pins ARCHITECTURE §9 manager projections + E15 drilldown
# (GET /api/manager/heatmap/{cellId}/drilldown); direct-report-only scoping.
Feature: Manager opens the command center, heatmap, and drills into a cell
  As a manager
  I want to open my command center, view the heatmap, and drill into a cell
  So that I see direct-report alignment risk and the Supporting-Outcome breakdown behind it

  Background:
    Given the deterministic demo seed is loaded
    And I am logged in as persona "Dana"

  Scenario: Drilling into a heatmap cell shows the Supporting-Outcome breakdown for that report
    Given I open the manager command center
    Then I see only my direct reports
    When I open the heatmap
    Then I see heatmap cells with risk badges from the enumerated vocabulary
    When I drill into the heatmap cell for direct report "Aisha"
    Then I see the Supporting-Outcome breakdown for that report and Defining Objective
