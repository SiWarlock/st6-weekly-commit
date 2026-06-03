/**
 * Step definitions: IC weekly-plan lifecycle — shared foundation + lock-blocked + lock-success.
 * Features: ic-lock-blocked-unlinked (REQ-E-001/T-010), ic-lock-success (REQ-F-007).
 *
 * Holds the suite's FOUNDATIONAL shared steps (seed-loaded, persona login, open plan, plan state)
 * which other features reuse — Cucumber's step namespace is global, so these are defined here ONCE.
 *
 * Authored-not-green: steps target the documented `data-cy` vocabulary (selectors.ts); the live DOM
 * binding reconciles with wc-web when it is runnable (origin 11.7).
 */
import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';
import { cySel } from '../selectors';
import type { PersonaKey } from '../../fixtures/personas';

// ── Shared foundation (reused across features) ──────────────────────────────
Given('the deterministic demo seed is loaded', () => {
  // The deterministic Appendix-E seed is restored by the global beforeEach `resetSeed()` seam.
  cy.resetSeed();
});

Given('I am logged in as persona {string}', (persona: string) => {
  cy.loginAs(persona as PersonaKey);
});

Given('I open my current weekly plan', () => {
  cy.visit('/weekly-commit'); // canonical IC weekly-plan route (wc-web AppRoutes; `/` redirects here for an IC)
  cy.get(cySel('planView')).should('be.visible');
});

Given('the plan is in state {string}', (state: string) => {
  cy.get(cySel('planState')).should('contain.text', state);
});

// ── Lock-blocked (ic-lock-blocked-unlinked) ─────────────────────────────────
Given('the plan has a planned commitment with no Supporting Outcome', () => {
  cy.get(cySel('commitmentRow')).filter('[data-linked="false"]').should('exist');
});

When('I attempt to lock the plan', () => {
  cy.get(cySel('lockButton')).click();
});

Then('the lock is rejected', () => {
  cy.get(cySel('errorBanner')).should('be.visible');
});

Then('I see the error code {string}', (code: string) => {
  cy.get(cySel('errorCode')).should('contain.text', code);
});

Then('I see the safe message {string}', (message: string) => {
  cy.get(cySel('safeMessage')).should('contain.text', message);
});

Then('the plan remains in state {string}', (state: string) => {
  cy.get(cySel('planState')).should('contain.text', state);
});

// ── Lock-success (ic-lock-success) ──────────────────────────────────────────
When('I link that commitment to Supporting Outcome {string}', (supportingOutcome: string) => {
  cy.get(cySel('commitmentRow'))
    .filter('[data-linked="false"]')
    .first()
    .find(cySel('supportingOutcomePicker'))
    .click();
  // The option list is a distinct element from the picker trigger — target it explicitly.
  cy.get(cySel('supportingOutcomeOption')).contains(supportingOutcome).click();
});

Then('the lock succeeds', () => {
  cy.get(cySel('errorBanner')).should('not.exist');
});

Then('I see the review-due date', () => {
  cy.get(cySel('reviewDueDate')).should('be.visible').and('not.be.empty');
});

Then('the locked baseline is read-only', () => {
  // The planned-baseline edit affordance is gone/disabled after lock (safety rule #2).
  cy.get(cySel('baselineEdit')).should('not.exist');
});
