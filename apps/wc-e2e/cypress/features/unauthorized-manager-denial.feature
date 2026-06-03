@REQ-S-002 @REQ-E-002
# Negative-auth fixture: no manager_relationship makes any report manage another — Dana is the
# sole manager. An IC reaching the team command center / heatmap is denied IDOR-safe.
# Pins safety rule #3 (central direct-report/self authorization; unauthorized access never reveals
# existence) + ARCHITECTURE §6 + Appendix B.21 (404 not-found-or-not-authorized, 403 authz denial).
Feature: A non-manager is denied the team command center (IDOR-safe)
  As the platform enforcing direct-report-scoped authorization
  I want a non-manager's team command-center access denied without leaking existence
  So that no IC can enumerate or reach another report's manager surfaces (safety rule #3)

  Scenario: The manager is allowed the command center but an IC is denied without revealing existence
    Given the deterministic demo seed is loaded
    And I am logged in as persona "Dana"
    When I open the manager command center
    Then the manager command center is allowed
    When I am logged in as persona "Priya"
    And I attempt to open the manager command center
    Then access to the manager command center is denied
    And no direct-report data is rendered
    And the persona switcher contrasts the allowed and denied views
