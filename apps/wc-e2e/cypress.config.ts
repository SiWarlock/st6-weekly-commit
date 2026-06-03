import { defineConfig } from 'cypress';
import createBundler from '@bahmutov/cypress-esbuild-preprocessor';
import { addCucumberPreprocessorPlugin } from '@badeball/cypress-cucumber-preprocessor';
import { createEsbuildPlugin } from '@badeball/cypress-cucumber-preprocessor/esbuild';

/**
 * Cypress + Cucumber (Gherkin) config for the WC acceptance suite.
 *  - baseUrl 5173 = the Vite dev server / Compose web surface (ARCHITECTURE F.7).
 *  - env.apiBaseUrl 8080 = the local API (F.7); the demo-identity header attaches against it.
 *  - specPattern resolves the `.feature` tree; step definitions are discovered via the
 *    `cypress-cucumber-preprocessor.stepDefinitions` glob in package.json.
 *
 * Authored-not-green: there is no running app in this worktree, so `cypress run` is deferred to the
 * 11.9 CI stage (local Compose) and the deployed smoke subset (Phase 12). This config makes the
 * suite well-formed and compilable now.
 */
export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/features/**/*.feature',
    supportFile: 'cypress/support/e2e.ts',
    env: {
      apiBaseUrl: 'http://localhost:8080',
    },
    async setupNodeEvents(on, config) {
      await addCucumberPreprocessorPlugin(on, config);
      on('file:preprocessor', createBundler({ plugins: [createEsbuildPlugin(config)] }));
      return config;
    },
  },
});
