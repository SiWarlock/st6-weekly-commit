/**
 * Custom Cypress commands shared across the BDD step definitions.
 *
 *  - `cy.loginAs(persona)` — establish the demo identity for a persona (ARCHITECTURE F.1).
 *  - `cy.resetSeed()`      — restore the deterministic Appendix-E fixture state (seam).
 *
 * Authored-not-green: both are real seams whose live wiring reconciles when the Compose stack +
 * the standalone PersonaSwitcher/DemoIdentityProvider are runnable (Carry-forward, origin 11.7).
 */
import { personaByKey, type PersonaKey } from '../fixtures/personas';

/** localStorage key the standalone DemoIdentityProvider reads (provider storage seam). */
const DEMO_ID_STORAGE_KEY = 'wc.demoEmployeeId';

Cypress.Commands.add('loginAs', (persona: PersonaKey) => {
  const { employeeId } = personaByKey(persona);
  const apiBaseUrl = String(Cypress.env('apiBaseUrl'));

  // Primary path: seed the DemoIdentityProvider storage so the standalone shell selects this
  // persona (most faithful to the PersonaSwitcher flow, ARCHITECTURE §7).
  // RECONCILE (origin 11.7): drive the real PersonaSwitcher control + confirm the provider storage
  // key once wc-web is runnable.
  cy.window().then((win) => {
    win.localStorage.setItem(DEMO_ID_STORAGE_KEY, employeeId);
  });

  // Fallback path: attach the X-Demo-Employee-Id header to every API call so requests authenticate
  // as this persona even before the provider wiring lands (F.1 demo-identity contract).
  cy.intercept({ url: `${apiBaseUrl}/**` }, (req) => {
    req.headers['X-Demo-Employee-Id'] = employeeId;
  });
});

Cypress.Commands.add('resetSeed', () => {
  // Seed-reset seam — restores the deterministic Appendix-E fixture between scenarios so ordering is
  // irrelevant. No-op now (authored-not-green): the real reset is backend-owned and lands with the
  // Compose stack.
  // RECONCILE (origin 11.7): wire to `cy.task('db:reset')` or a guarded test-only reset endpoint.
  cy.log('resetSeed(): no-op seam — deterministic Appendix-E seed assumed (RECONCILE when Compose exists)');
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Establish the demo identity for a persona via the X-Demo-Employee-Id contract (F.1). */
      loginAs(persona: PersonaKey): Chainable<void>;
      /** Restore the deterministic Appendix-E seed fixture (seam — no-op until Compose exists). */
      resetSeed(): Chainable<void>;
    }
  }
}

export {};
