# Session 005 — Phase 11.7 Cypress+Cucumber BDD acceptance suite (7 features + harness)

- **Date:** 2026-06-02
- **Phase:** 11 (Testing, BDD acceptance & quality gates) — task **11.7**
- **Role:** `st6-test-e2e-implementer` (test track — `apps/wc-e2e/`)
- **Predecessor session:** [004 — Phase 2 Auth0 claim mapper + demo-auth filter](004-2026-06-02-phase2-claim-mapper-and-demo-auth-filter.md) _(chronological; different arc — 004 is the backend track. This is the **test track's first** session.)_
- **Successor session:** _(TBD)_
- **Commit this session (1 slice, amended once for audit remediation):** `c79f9f2` (11.7 BDD suite — 7 features + harness; supersedes the pre-audit `63abdcd`).

## Why this session existed

Phase 11.7 — author the spec-anchored Cypress+Cucumber BDD acceptance suite in `apps/wc-e2e` (greenfield: only a 64B `package.json`). The suite is the project's executable acceptance layer mapping 1:1 to REQ IDs (ARCHITECTURE §17, Appendix C.5). This is an **authored-not-green** slice (standing user directive): no runnable/deployed app exists in this worktree, so the RED→GREEN loop is deferred — "done" = the suite is **authored, well-formed, type-clean, parse-clean, with 0 undefined/ambiguous steps and 1:1 REQ mapping**. Real green runs land at 11.9 (CI local-Compose) and Phase 12 (deployed smoke).

## What was built

### Files created (19)
**Features (`cypress/features/`)** — 7 Gherkin files, each REQ-tagged + seed-fixture comment header:
- `ic-lock-blocked-unlinked.feature` (REQ-E-001/T-010) — lock rejected on an unlinked planned commitment; `UNLINKED_PLANNED_COMMITMENT` + B.21 safeMessage; plan stays DRAFT (R6 Sam).
- `ic-lock-success.feature` (REQ-F-007) — link last unlinked → lock → LOCKED + review-due + read-only baseline (R6 Sam).
- `ic-reconcile-carry-forward.feature` (REQ-E-005/F-028) — outcomes + UNPLANNED + carry-forward; **prior-week baseline unchanged** (R5 Grace).
- `manager-dispute-loop.feature` (REQ-T-011/F-017) — OPEN→IC_RESPONDED→RESOLVED; `IC_CANNOT_RESOLVE_DISPUTE` denial; review re-derivation (R3 Aisha + Dana).
- `manager-heatmap-drilldown.feature` (REQ-E-003/F-022) — command center → heatmap → drill into a report cell → SO breakdown (Dana).
- `outlook-failed-retry.feature` (REQ-E-004/I-005) — FAILED sync safe warning + no-secret-leak + manual retry + non-blocking lifecycle (R2 Marco).
- `unauthorized-manager-denial.feature` (REQ-S-002/E-002) — IDOR-safe manager-surface denial (Dana allowed, R1 Priya denied).

**Step definitions (`cypress/support/step_definitions/`)** — 6 typed modules: `ic_plan` (shared foundation + lock), `reconcile`, `dispute`, `heatmap`, `sync`, `authz`. 48 step defs covering all 75 feature steps.

**Support / fixtures / config:**
- `cypress/support/commands.ts` — `loginAs(persona)` (X-Demo-Employee-Id, F.1) + `resetSeed()` seam + `Cypress.Chainable` augmentation.
- `cypress/support/e2e.ts` — global support file (imports commands; `beforeEach` seed-reset hook).
- `cypress/support/selectors.ts` — typed `data-cy` `SEL` vocabulary + `cySel()` helper (single source of selector truth).
- `cypress/fixtures/personas.ts` — first-name→identity map (Dana/R1–R6), placeholder-shaped UUIDs, throwing resolver.
- `cypress.config.ts` — cucumber esbuild preprocessor wiring; baseUrl 5173 / env.apiBaseUrl 8080 / specPattern / supportFile.
- `tsconfig.json` — TS-strict (wc-web parity: `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, etc.).

### Files modified (1)
- `apps/wc-e2e/package.json` — devDeps (cypress ^13.17.0, @badeball/cypress-cucumber-preprocessor ^24.0.1, @bahmutov/cypress-esbuild-preprocessor ^2.2.8, esbuild ^0.28.0, typescript ^5.6.3, @types/node ^20.16.13), `cypress:open`/`cypress:run`/`typecheck` scripts, `cypress-cucumber-preprocessor.stepDefinitions` block.

### Audit remediation folded into the amend (7 files, +24 lines)
- Route alignment: `cy.visit` → canonical wc-web routes (`/weekly-commit`, `/manager/command-center`) per `AppRoutes.tsx` + the area route table.
- `SECRET_MARKERS` hardened 5→13 (rule #7) — now catches `refresh_token`, raw `eyJ…` JWTs, `client_secret`, etc.
- Added distinct `supportingOutcomeOption` selector key (option-list vs picker-trigger disambiguation).
- `Appendix B.0`→`B.21` comment accuracy + RECONCILE markers on the heatmap open-action + sync positive-assertion seams.

## Decisions made

- **Toolchain (Step-2.5 Q1):** `@badeball/cypress-cucumber-preprocessor` + `@bahmutov/cypress-esbuild-preprocessor` + esbuild + TS. Cypress pinned **13.x** (orch default) with the **latest** preprocessor `^24.0.1` (peer-supports cypress 13–15). Carets in `package.json` + lockfile-as-determinism (Yarn idiom).
- **Selector convention (Q2):** `data-cy="<kebab-case>"` documented in a typed `selectors.ts` `SEL` map; step defs target via `cySel()` only — never inlined selectors.
- **Seed-reset (Q3):** `resetSeed()` is a documented **no-op seam** now (RECONCILE marker); real reset is backend-owned, lands with Compose.
- **safeMessage vs code (Q4):** assert the literal safeMessage where pinned (B.21 UNLINKED + the Outlook literal); for codes without a pinned UI string, assert a `data-cy` error region carrying the code.
- **Persona login (Q5):** `loginAs` drives the demo-identity seam (X-Demo-Employee-Id); first-name persona keys (1:1 to Appendix E); placeholder-shaped UUIDs with RECONCILE marker.
- **Route alignment (audit):** target the real wc-web routes from `AppRoutes.tsx`, not `/`/`/command-center` — surfaced by the code-quality reviewer; a real correctness catch.
- **Verification without breaching scope:** ran `tsc` + a `@cucumber/cucumber-expressions` step-matcher + Gherkin parse in an **isolated `/tmp` install** — never rewrote the shared root `yarn.lock`.

## Decisions explicitly NOT made (deferred)

- **No live green run** — deferred to 11.9 (CI local-Compose) + Phase 12 (deployed smoke). No app exists here.
- **No ESLint flat-config in this slice** — ESLint 9 + Prettier wiring belongs to 11.9 (CI lint stage); TS-strict + no-`any` is the interim gate.
- **No workspace `yarn install`** — devDeps declared but not materialized into the root `yarn.lock` (would be a cross-area lockfile edit mid-round); materialization deferred to 11.9 CI.
- **No `apps/wc-e2e/smoke/` subset** — separate future slice (REQ-T-016, Phase 12).
- **Assertion-strength on 2 seams** — the baseline-unchanged (rule #2) + sync non-blocking (rule #4) pins use weak negative/self-reported assertions; strengthening to positive/diff assertions needs the live surface (RECONCILE-marked).
- **Appendix C.5 `data-cy` addendum** — orchestrator deferred pending wc-web's real attributes.

## TDD compliance

**Clean** (within the authored-not-green discipline). The BDD feature files ARE the test designs (RED): all 7 authored in Step 2, presented at Step 2.5, and approved by the orchestrator (`APPROVED.`) **before** the harness + step-definitions (the GREEN glue) were written. No implementation-before-test inversion. One self-caught defect during GREEN: a duplicate step registration (`the dispute is in state {string}` as both Given and Then → ambiguous under Cucumber's keyword-agnostic matching) was collapsed to a single registration; the static matcher then confirmed 0 ambiguous. The green-run itself is deferred per the standing user directive (no runnable app) — not a TDD violation.

## Reachability

**N/A from a production entry point in this worktree** (no running app). The suite becomes reachable via:
- the **11.9 CI E2E stage** (consumes `cypress.config.ts` `specPattern`) — an already-planned phase task;
- the **`apps/wc-e2e/smoke/`** deployed subset (Phase 12).

Internal wiring fully verified by the close-out `reachability-auditor`: 48 step defs ↔ 75 feature steps with **0 undefined / 0 ambiguous / 0 duplicate / 0 orphan**; `specPattern`, the `stepDefinitions` glob, `supportFile`, and the `commands` import all resolve. No tested-but-unwired gap beyond the already-planned 11.9 CI stage.

## Open follow-ups (Step-9 categorized list — for the orchestrator to verify routed)

- **Convention candidate → `apps/wc-e2e/LESSONS.md` §1** _(orch already created)_ — the e2e harness pattern: `loginAs`/`resetSeed` seams, the typed `data-cy` `SEL` vocabulary, the esbuild-cucumber wiring, and the **keyword-agnostic single-registration rule**.
- **Future TODO — Carry-forward (origin 11.7):**
  - (a) reconcile the `data-cy` selector vocabulary with wc-web's real attributes when the frontend is runnable;
  - (b) wire `resetSeed()` + the persona→UUID map + the `safeMessage` literals to the real Compose stack;
  - (c) **assertion-strength** — strengthen the baseline-unchanged (rule #2) + sync non-blocking (rule #4) pins to positive/diff assertions at the live-stack pass (both RECONCILE-marked);
  - (d) materialize the `yarn.lock` devDeps + wire ESLint 9 / Prettier for `apps/wc-e2e` → **11.9 CI**.
- **Architecture-doc note candidate** — whether Appendix C.5 gains a one-line `data-cy` convention addendum (orchestrator deferred pending wc-web attrs).
- **Cross-doc invariant change: NONE** — the suite asserts existing invariants + already-pinned Appendix-A/B codes; no contract model touched (confirmed against `apps/wc-e2e/CLAUDE.md` "Cross-doc invariants — None").
- **`MVP_TASKS.md` §11.7 checkbox tick + Log entry** → orchestrator at `/orchestrate-end`.

## How to use what was built

- `yarn workspace wc-e2e cypress:open` / `cypress:run` — once the Compose stack (web 5173 / api 8080) is up and the seed is loaded (11.9+).
- `yarn workspace wc-e2e typecheck` — `tsc --noEmit` over the harness (after a workspace install).
- The suite is the CI E2E stage's input at 11.9 and the source of the `smoke/` deployed subset at Phase 12.
