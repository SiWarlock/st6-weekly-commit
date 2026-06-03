/**
 * Step definitions: manager command center + heatmap drilldown.
 * Feature: manager-heatmap-drilldown (REQ-E-003/F-022).
 *
 * Pins §9 projections (direct-report-only roll-up) + E15 drilldown (report × Defining Objective →
 * Supporting-Outcome breakdown). Risk badges are asserted as members of the enumerated vocabulary.
 *
 * NOTE: `I open the manager command center` is defined HERE once and reused (keyword-agnostic) by
 * the unauthorized-manager-denial feature — do not redefine it in authz.steps.ts.
 *
 * Authored-not-green: targets the documented `data-cy` vocabulary; live binding reconciles later.
 */
import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';
import { cySel } from '../selectors';

/** Risk-badge vocabulary (ARCHITECTURE Appendix A `RiskBadge`, mirrored in Appendix E coverage). */
const RISK_BADGE_VOCAB: readonly string[] = [
  'MISALIGNED',
  'OVERDUE_REVIEW',
  'UNREVIEWED',
  'BLOCKED',
  'CARRY_FORWARD',
  'NEEDS_REVIEW',
];

Given('I open the manager command center', () => {
  cy.visit('/manager/command-center'); // canonical manager route (wc-web AppRoutes)
  cy.get(cySel('commandCenter')).should('be.visible');
});

Then('I see only my direct reports', () => {
  cy.get(cySel('directReportRow')).should('have.length.greaterThan', 0);
});

When('I open the heatmap', () => {
  // RECONCILE (origin 11.7): the heatmap is assumed rendered on the command-center route; if the
  // live surface requires an explicit open action (tab/toggle), add the click here at reconcile.
  cy.get(cySel('heatmap')).should('be.visible');
});

Then('I see heatmap cells with risk badges from the enumerated vocabulary', () => {
  cy.get(cySel('riskBadge')).each((badge) => {
    const text = badge.text().trim();
    expect(RISK_BADGE_VOCAB, `risk badge "${text}" is in the enumerated vocabulary`).to.include(text);
  });
});

When('I drill into the heatmap cell for direct report {string}', (directReport: string) => {
  cy.get(cySel('heatmapCell')).filter(`[data-report="${directReport}"]`).first().click();
});

Then('I see the Supporting-Outcome breakdown for that report and Defining Objective', () => {
  cy.get(cySel('drilldownSoBreakdown')).should('be.visible');
});
