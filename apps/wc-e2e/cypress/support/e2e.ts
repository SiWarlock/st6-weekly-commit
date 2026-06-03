/**
 * Global support file — loaded before every spec (Cypress default `supportFile`).
 * Registers the custom commands and the per-scenario deterministic-seed reset hook.
 */
import './commands';

// Restore the deterministic Appendix-E fixture before every scenario so scenario ordering never
// matters. `resetSeed()` is a no-op seam until the Compose stack exists (authored-not-green).
beforeEach(() => {
  cy.resetSeed();
});
