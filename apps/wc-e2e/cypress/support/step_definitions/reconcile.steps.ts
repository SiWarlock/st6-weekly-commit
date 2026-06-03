/**
 * Step definitions: IC reconciliation — outcomes, unplanned work, carry-forward.
 * Feature: ic-reconcile-carry-forward (REQ-E-005/F-028).
 *
 * The load-bearing assertion is REQ-E-005: the locked prior-week baseline is UNCHANGED after the
 * current-week reconciliation + carry-forward (safety rule #2).
 *
 * Authored-not-green: targets the documented `data-cy` vocabulary; live DOM binding reconciles with
 * wc-web later (origin 11.7).
 */
import { When, Then } from '@badeball/cypress-cucumber-preprocessor';
import { cySel } from '../selectors';

When('I record the reconciliation outcome {string} on a planned commitment', (outcome: string) => {
  cy.get(cySel('reconcileOutcome')).first().select(outcome);
});

When('I add an UNPLANNED commitment linked to Supporting Outcome {string}', (supportingOutcome: string) => {
  cy.get(cySel('unplannedAdd')).click();
  cy.get(cySel('supportingOutcomeLink')).contains(supportingOutcome).click();
});

When('I carry forward an unfinished commitment to next week', () => {
  cy.get(cySel('carryForwardButton')).first().click();
});

Then('the reconciliation outcomes are persisted', () => {
  cy.get(cySel('reconcileOutcome')).first().should('not.have.value', '');
});

Then('the unplanned commitment is labeled {string} and linked to {string}', (label: string, supportingOutcome: string) => {
  cy.get(cySel('unplannedBadge')).should('contain.text', label);
  cy.get(cySel('supportingOutcomeLink')).should('contain.text', supportingOutcome);
});

Then('the carried-forward successor links back to its source commitment', () => {
  // The successor carries `carry_forward_source_commitment_id` → surfaced as a back-link.
  cy.get(cySel('carryForwardSourceLink')).should('be.visible');
});

Then('the locked prior-week baseline is unchanged', () => {
  // REQ-E-005 pin: the source (prior-week, locked) commitment's planned fields are immutable.
  // RECONCILE (origin 11.7): assert the prior-week baseline snapshot equals its pre-reconcile state
  //   against the real surface (UI panel or API read) when Compose exists.
  cy.get(cySel('baselinePanel')).should('have.attr', 'data-baseline-mutated', 'false');
});
