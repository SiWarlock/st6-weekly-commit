/**
 * Step definitions: manager dispute loop — flag → IC respond → IC-cannot-resolve → manager resolve.
 * Feature: manager-dispute-loop (REQ-T-011/F-017).
 *
 * Pins the §3 dispute state machine OPEN → IC_RESPONDED → RESOLVED (IC response never resolves), the
 * IC-cannot-resolve authorization denial (IC_CANNOT_RESOLVE_DISPUTE, 403), and the review
 * re-derivation REVIEWED_WITH_DISPUTES → REVIEWED.
 *
 * Authored-not-green: targets the documented `data-cy` vocabulary; live binding reconciles later.
 */
import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';
import { cySel } from '../selectors';

Given(
  'manager {string} has an OPEN dispute on a commitment owned by direct report {string}',
  (_manager: string, _directReport: string) => {
    // Provided by the deterministic seed (R3 Aisha has 1 OPEN dispute, flag_type=MISALIGNED).
    // Parameters document the relationship the seed encodes; no setup call in the no-op seam state.
    cy.resetSeed();
  },
);

Given('I open the open dispute on my commitment', () => {
  cy.visit('/weekly-commit'); // the dispute panel lives on the IC weekly-plan surface (wc-web AppRoutes)
  cy.get(cySel('disputePanel')).should('be.visible');
});

// Cucumber matches on the EXPRESSION, keyword-agnostic — this single registration serves both the
// Given precondition (`And the dispute is in state "OPEN"`) and the Then transition assertions.
Then('the dispute is in state {string}', (state: string) => {
  cy.get(cySel('disputeState')).should('contain.text', state);
});

When('I respond to the dispute by revising the Supporting Outcome', () => {
  cy.get(cySel('disputeRespondSo')).click();
});

Then('the dispute is not resolved', () => {
  cy.get(cySel('disputeState')).should('not.contain.text', 'RESOLVED');
});

When('I attempt to resolve the dispute as the IC', () => {
  // The IC has no resolve affordance; force the action to prove the server denies it (rule: IC
  // cannot resolve their own dispute). The denial surfaces in the error region.
  cy.get(cySel('disputeResolve')).click({ force: true });
});

Then('the action is denied with error code {string}', (code: string) => {
  cy.get(cySel('errorCode')).should('contain.text', code);
});

When('I resolve the dispute', () => {
  cy.get(cySel('disputeResolve')).click();
});

Then('the manager review re-derives from {string} to {string}', (from: string, to: string) => {
  // The review status is DERIVED, never stored — resolving the last open dispute flips it.
  cy.get(cySel('reviewState')).should('not.contain.text', from).and('contain.text', to);
});
