# LESSONS.md — ST6 Weekly Commit Module (E2E / `apps/wc-e2e`)

> Full prose for every lesson logged during work in `apps/wc-e2e/`. The compact index lives in `apps/wc-e2e/CLAUDE.md` "Lessons logged" table.
>
> **Lesson numbers are stable IDs.** New lessons get the next sequential number. Numbers may be referenced from code comments, commit messages, and cross-references between lessons. **Don't reorder; don't reuse a deleted number's slot.**
>
> **Lessons start at §1.** Each code area has its own lesson sequence — lessons don't carry across code areas.

---

## Lesson format

```markdown
## <a id="N"></a>N. <Short topic> — <one-line rule>

**Date:** YYYY-MM-DD.
**Source slice:** <slice-id or commit hash>.

<prose>
```

---

## <a id="1"></a>1. Cypress+Cucumber BDD harness pattern (authored-not-green) — typed `data-cy` seams, single-registration steps, esbuild-cucumber wiring

**Date:** 2026-06-02.
**Source slice:** 11.7 (brief `005`).

The acceptance suite is authored **before** a runnable app exists in this worktree, so the harness is built around *reconciliation seams* and a *well-formed-not-green* acceptance bar. The pattern, banked for the 11.9 smoke subset + any future e2e:

- **Toolchain:** `@badeball/cypress-cucumber-preprocessor` (the maintained Cucumber-for-Cypress path) + `@bahmutov/cypress-esbuild-preprocessor` + esbuild bundler, wired in `cypress.config.ts` via `addCucumberPreprocessorPlugin(on, config)` then `on("file:preprocessor", createBundler({ plugins: [createEsbuildPlugin(config)] }))` — **return the (mutated) config**. Step-def discovery is the `cypress-cucumber-preprocessor.stepDefinitions` glob in `package.json` (`cypress/support/step_definitions/**/*.{js,ts}`), not a `cypress.config` field.

- **Keyword-agnostic single-registration rule (the gotcha):** a Cucumber step *expression* registers **once**, regardless of the `Given`/`When`/`Then` keyword that introduces it. Registering the same step text under two keywords (or in two modules) = an **ambiguous-step** failure at match time, even though the `.feature` reads naturally. Author each unique step text exactly once (put shared steps like `the dispute is in state {string}` in the most natural module and reuse). Verify with the real `@cucumber/cucumber-expressions` matcher — 0 undefined / 0 ambiguous / 0 duplicate is the parse gate.

- **Reconciliation seams (don't block authoring on the real stack):**
  - **`loginAs(persona)`** — drives the standalone `PersonaSwitcher` UI and falls back to attaching the demo-identity header `X-Demo-Employee-Id` = the employee UUID (Appendix F.1). Persona keys are Appendix-E first names (Dana, Priya, Marco, Aisha, Tomas, Grace, Sam), mapped to UUIDs in `cypress/fixtures/personas.ts`.
  - **`resetSeed()`** — a `cy.task('db:reset')` **no-op seam** with a `// RECONCILE` marker; the real DB-reset is backend-owned and wires when the local Compose stack exists.
  - **`personas.ts` UUIDs** are **placeholder-shaped** until reconciled with the Appendix-E seed `V5` constants.

- **Typed selector vocabulary:** target the UI through a single typed `SEL` const map in `cypress/support/selectors.ts` using a `data-cy="<kebab-case>"` convention (ARCHITECTURE.md is silent on a selector convention — this is documented in-harness, **not** invented as a binding the frontend must chase). The vocabulary reconciles with wc-web's real attributes when the frontend is runnable (Carry-forward, origin 11.7); whether to pin it as an Appendix C.5 addendum is a deferred decision.

- **Assertion posture:** assert the **UI-rendered `safeMessage`** (the Cypress-assertable RFC-7807 contract, §7) where Appendix B/E pins the literal (e.g. B.21 `UNLINKED_PLANNED_COMMITMENT` message; the Appendix-E Outlook "Calendar sync failed; you can retry."), and a `data-cy="error-code"` region for codes without a pinned UI string. Assert at the UI, **not** raw HTTP status — that sidesteps status ambiguity (e.g. the 409-vs-422 doc-drift on the unlinked-lock; the code + safeMessage are unambiguous and consistent across sources).

- **Acceptance = well-formed, not green:** `tsc --noEmit` clean (TS-strict, no `any`), all features parse, 0 undefined/ambiguous/duplicate steps, 1:1 REQ tags. Green runs defer to 11.9 (local Compose) + Phase-12 (deployed smoke). Do **not** run `yarn install` mid-round from a single area — it rewrites the shared root `yarn.lock` (cross-area); verify deps via an isolated install, leave lock materialization to the workspace install (11.9).
