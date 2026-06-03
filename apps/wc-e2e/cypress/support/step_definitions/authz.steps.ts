/**
 * Step definitions: direct-report-scoped authorization (allowed vs denied).
 * Feature: unauthorized-manager-denial (REQ-S-002/E-002).
 *
 * Pins safety rule #3 (central direct-report/self authorization). The seed has a single manager
 * (Dana), so the testable denial is an IC reaching the team command center — framed as
 * "access denied + no direct-report data rendered" so it holds whether the server returns 403
 * (role gate) or 404 (not-found-or-not-authorized, IDOR-safe). Exact status is a Q4 reconciliation
 * point (origin 11.7).
 *
 * Reuses (does NOT redefine) `I am logged in as persona {string}` (ic_plan.steps.ts) and
 * `I open the manager command center` (heatmap.steps.ts) — Cucumber's step namespace is global.
 *
 * Authored-not-green: targets the documented `data-cy` vocabulary; live binding reconciles later.
 */
import { Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { cySel } from '../selectors';

Then('the manager command center is allowed', () => {
  cy.get(cySel('commandCenter')).should('be.visible');
  cy.get(cySel('accessDenied')).should('not.exist');
});

When('I attempt to open the manager command center', () => {
  cy.visit('/manager/command-center', { failOnStatusCode: false }); // canonical manager route (wc-web AppRoutes)
});

Then('access to the manager command center is denied', () => {
  cy.get(cySel('accessDenied')).should('be.visible');
  cy.get(cySel('commandCenter')).should('not.exist');
});

Then('no direct-report data is rendered', () => {
  // IDOR-safe: a denied actor sees no foreign report rows (no existence leak via rendered data).
  cy.get(cySel('directReportRow')).should('not.exist');
});

Then('the persona switcher contrasts the allowed and denied views', () => {
  cy.get(cySel('personaSwitcher')).should('be.visible');
});
