/**
 * Step definitions: Outlook sync FAILED warning + manual retry.
 * Feature: outlook-failed-retry (REQ-E-004/I-005).
 *
 * Pins safety rule #4 (Outlook sync failure NEVER blocks the core lifecycle) and safety rule #7
 * (no token/secret leak in the surfaced warning). The retry is non-blocking (FAILED → RETRY_REQUESTED).
 *
 * Authored-not-green: targets the documented `data-cy` vocabulary; live binding reconciles later.
 */
import { Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { cySel } from '../selectors';

/**
 * Substrings that must NEVER appear in a user-facing sync warning (safety rule #7 — no token /
 * secret / PII leak in safe_message, logs, or audit metadata). Lower-cased substring match.
 */
const SECRET_MARKERS: readonly string[] = [
  'token',
  'access_token',
  'refresh_token',
  'secret',
  'client_secret',
  'bearer',
  'authorization',
  'jwt',
  'eyj', // base64url JWT header prefix ("eyJ...")
  'password',
  'api_key',
  'apikey',
  'private_key',
];

Then('I see a calendar sync warning', () => {
  cy.get(cySel('syncWarning')).should('be.visible');
});

Then('the warning shows the safe message {string}', (message: string) => {
  cy.get(cySel('syncWarning')).should('contain.text', message);
});

Then('the warning contains no token or secret detail', () => {
  cy.get(cySel('syncWarning')).invoke('text').then((raw) => {
    const text = raw.toLowerCase();
    SECRET_MARKERS.forEach((marker) => {
      expect(text, `sync warning must not leak "${marker}" (safety rule #7)`).to.not.include(marker);
    });
  });
});

Then('my plan lifecycle is unaffected by the sync failure', () => {
  // The plan/lifecycle proceeds independently of the FAILED sync (safety rule #4).
  cy.get(cySel('lifecycleStatus')).should('not.contain.text', 'BLOCKED');
});

When('I trigger a manual sync retry', () => {
  cy.get(cySel('syncRetryButton')).click();
});

Then('the sync retry is accepted', () => {
  cy.get(cySel('syncRetryButton')).should('be.disabled');
});

Then('my plan lifecycle remains successful', () => {
  // RECONCILE (origin 11.7): strengthen to a POSITIVE state assertion (e.g. the expected LOCKED
  // status for R2 Marco) once the live `lifecycleStatus` surface exists — a `not BLOCKED` negative
  // passes vacuously on an empty/absent element.
  cy.get(cySel('lifecycleStatus')).should('not.contain.text', 'BLOCKED');
});
