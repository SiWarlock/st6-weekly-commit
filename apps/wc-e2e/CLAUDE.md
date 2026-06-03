# ST6 Weekly Commit Module `apps/wc-e2e/` — Build Guide

> Area conventions for the Cypress + Cucumber/Gherkin acceptance suite. The suite is **spec-driven / authored-not-green**: features + step defs are authored against the contract (ARCHITECTURE.md) before a runnable app exists; they go green only when the local Compose stack (11.9) and the deployed domains (Phase 12) are up. Shared comm/team rules live in root `CLAUDE.md`.

## Scope boundary (load-bearing)

This area (`apps/wc-e2e/`) is the **test track**. Touch ONLY `apps/wc-e2e/**` + `docs/` + root deliverable files. Do **NOT** edit `apps/wc-api/**` or `apps/wc-web/**` (other teams' areas, parallel branches). Orchestrator-territory files (`MVP_TASKS.md`, `ARCHITECTURE.md`, this `CLAUDE.md`, `LESSONS.md`) are flagged at TDD Step 9, never edited by the implementer. Stage with **explicit `git add <path>`** — never `-A`/`.`, never `git add apps/wc-e2e/` wholesale (it would sweep up orchestrator-territory `CLAUDE.md`/`LESSONS.md`).

## Lookup table — where to find canonical info

| Topic | Canonical source |
|---|---|
| E2E file tree (features, step defs, support, smoke) | `ARCHITECTURE.md` Appendix C.5 — file names are the contract |
| The 7 BDD features ↔ REQ IDs | Appendix C.5 + `MVP_TASKS.md` §11.7 |
| Deterministic demo seed (personas M/R1–R6, fixture state matrix, carry-forward chain, FAILED-sync) | Appendix E Part 2 |
| Validation rules (caps, XSS/emoji probes stored verbatim) | Appendix E Part 1 |
| Error model + named `code`s + RFC-7807 `safeMessage` example | Appendix B.21 + §5 |
| Demo identity (`X-Demo-Employee-Id`) | Appendix F.1 |
| `allowedAction` → endpoint map | Appendix F.4 |
| Routes (`/`, `/weekly-commit`, `/manager/command-center`, `/manager/heatmap`) | §11 (decomposition) |
| Local ports (Vite 5173 / API 8080 / PG 5432) | Appendix F.7 |
| Local/CI runtime (Compose, profile-switched in-process worker) | §13 |
| Safety invariants the suite asserts | root `CLAUDE.md` "Key safety rules" §1–#7 |

> Don't load `ARCHITECTURE.md` whole — use `/check-arch <topic>` or targeted reads.

## Stack

| Layer | Choice |
|---|---|
| Runtime / pkg | Node 20 LTS · Yarn Workspaces (root lock) |
| E2E runner | Cypress 13.x |
| BDD | `@badeball/cypress-cucumber-preprocessor` + `@bahmutov/cypress-esbuild-preprocessor` + esbuild |
| Language | TypeScript **strict** (no `any`) |
| Lint (interim) | TS-strict + no-`any`; ESLint 9 + Prettier wiring belongs to 11.9 (CI lint stage) |

## Standard commands

```bash
# (from apps/wc-e2e/)
yarn cypress open                 # interactive (needs the app + Compose stack up)
yarn cypress run                  # headless full run (green-run; 11.9 / deployed)
npx tsc --noEmit                  # the authored-not-green type gate (must be clean)
# Gherkin parse + step-resolution check via the cucumber matcher (0 undefined/ambiguous/duplicate)
```

> **Do NOT run `yarn install` from this area mid-round** — it rewrites the shared root `yarn.lock` (cross-area). Verify deps via an isolated install; leave lock materialization to the workspace install at 11.9 (LESSONS §1).

## TDD protocol (spec-driven / authored-not-green)

`/tdd` applies, but the RED→GREEN loop is **deferred** when no runnable app exists: Step 2 (RED) = author the Gherkin specs + step-def skeletons (they reference not-yet-existent surfaces); Steps 3–7 (GREEN) = make the suite **well-formed** (features parse, `tsc --noEmit` clean, 0 undefined/ambiguous/duplicate steps, 1:1 REQ tags). Step 7.5 reachability is **N/A in-worktree** — the suite becomes reachable via the 11.9 CI E2E stage + the `smoke/` subset, not a production entry point. Acceptance is "authored + well-formed", **not** a green run.

## Forbidden patterns

- **No green-run assumption.** Never claim a feature "passes" — assert it is authored + well-formed; green runs land at 11.9 / Phase 12.
- **No cross-area edits.** Never touch `apps/wc-api/**` or `apps/wc-web/**`.
- **No `-A` / wholesale-dir staging.** Explicit paths only.
- **No `any`.** TS-strict; step defs are typed.
- **No raw-HTTP-status assertions as the primary check.** Assert the UI-rendered `safeMessage` + `data-cy="error-code"` region (status codes can drift; the code + safeMessage are the stable contract).
- **No duplicate step-text registration** across keywords/modules (ambiguous-step failure — LESSONS §1).
- **No invented selector binding the frontend must chase** — document the `data-cy` convention in-harness; reconcile with wc-web when runnable.

## Cross-doc invariants — schema/docs mirroring

**None.** This area authors **tests**, not contract models — the suite *asserts* existing lifecycle/dispute/sync invariants + the already-pinned Appendix-A/B named error codes. A slice here never changes a contract field. (If a feature ever needs a behavior the anchors don't cover, that's a Step-9 cross-doc flag — the anchor is missing or the impl drifted — not a silent test change.)

## Lessons logged

> Compact index. Full prose in `apps/wc-e2e/LESSONS.md`. Lesson numbers are stable IDs — never reorder, never reuse a deleted slot.

| N | date | topic | one-line rule |
|---|---|---|---|
| 1 | 2026-06-02 | [Cypress+Cucumber authored-not-green harness](LESSONS.md#1) | Wire `@badeball`+`@bahmutov`+esbuild (return the mutated config; `stepDefinitions` glob in package.json); register each step text **once** (keyword-agnostic — dupes = ambiguous); build `loginAs`/`resetSeed`/typed `data-cy SEL` reconciliation seams; assert UI `safeMessage`+code not raw status; acceptance = well-formed (`tsc`+parse clean), green defers to 11.9/Phase-12; never `yarn install` mid-round (rewrites shared lock). |
