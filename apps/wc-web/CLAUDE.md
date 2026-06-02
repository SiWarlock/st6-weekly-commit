# ST6 Weekly Commit Module `apps/wc-web/` — Build Guide

> **You're in `apps/wc-web/`.** This file plus root `CLAUDE.md` both load. The root file covers global project conventions + shared comm rules (track-prefix, escalation taxonomy, messaging budget); this file owns code-area conventions for frontend.
>
> The `apps/wc-e2e/` Cypress + Cucumber/Gherkin acceptance suite rides with this area — it shares the frontend toolchain (Yarn Workspaces + Nx) and is driven via `yarn nx e2e wc-e2e`.

## Launch protocol

| Working on... | cwd | Loads |
|---|---|---|
| Planning / docs / commits | repo root (`ST6/`) | root `CLAUDE.md` only |
| frontend code | `apps/wc-web/` | this `CLAUDE.md` + root |

<!-- For a multi-area project, add a row per additional code area. -->

If you find yourself fighting the wrong conventions, check your cwd.

## Session start/end protocol

**At session start:**
1. Read `MVP_TASKS.md` (repo root) → "Currently in progress" section.
2. Confirm with the user what feature this session is targeting.
3. Read the relevant section of `ARCHITECTURE.md` from the lookup table below.

**At session end** (only when the user explicitly says we're done):

1. **Implementer runs `/session-end`.** Implementer writes ONLY:
   - `apps/wc-web/` code files (the slice's implementation)
   - test files (the slice's tests)
   - dependency manifest / lockfile (deps the slice adds)
   - `docs/sessions/<NNN>-<date>-<topic>.md` (session doc, created at `/session-end` Step 5)

   **Implementer must NOT touch (all orchestrator territory):**
   - `MVP_TASKS.md`
   - `apps/wc-web/LESSONS.md`
   - `apps/wc-web/CLAUDE.md` (entire file — both the Cross-doc invariants table AND the Lessons logged index)
   - `ARCHITECTURE.md`
   - `docs/orchestrator-briefing.md` / `docs/tdd-brief-template.md` / `docs/briefs/` / `docs/runbooks/`
   - other top-level deliverable / design docs
   - `.gitignore` and root-level dotfiles (unless adding a new artifact to ignore, flagged at Step 9)

   At the slice's Step 10 commit, **explicit `git add <path>` for each slice file**; **never `git add -A`** or `git add .`; **never stage an orchestrator-territory file**. If the slice surfaces a change to any orchestrator-territory file (new model needing a cross-doc table row, a lesson candidate, an architecture note), the implementer **flags it at Step 9** per the routing matrix in `docs/orchestrator-briefing.md`. The orchestrator writes the change hot during the same session — working-tree state stays aligned within the round even though commits stagger.

2. **Orchestrator runs `/orchestrate-end`** for round close-out + Carry-forward triage + round terminal commit + push.

## Lookup table — where to find canonical info

Don't paste these sections into the prompt. Grep the file:section, read only what you need. `/check-arch <topic>` dispatches off this table.

| Topic | File (relative to repo root) | Section |
|---|---|---|
| <subsystem A> | `ARCHITECTURE.md` | §X |
| <subsystem B> | `ARCHITECTURE.md` | §Y |
| Lessons logged (full prose) | `apps/wc-web/LESSONS.md` | by lesson # |

<!-- Starts near-empty. Add a row whenever a topic is looked up twice. -->

**Code intelligence & docs (when available):** prefer a code-intelligence MCP (e.g. CodeGraph) for code navigation / callers / traces over `grep`+read loops, and a docs MCP (e.g. Context7) for up-to-date library/API docs — see root `CLAUDE.md` "Code intelligence & docs." No-op if not installed.

## Stack

<!-- ▼ EXAMPLE BLOCK [id=area-stack]: stack quick-reference for implementer sessions. Canonical stack lives in root CLAUDE.md + ARCHITECTURE.md; this is the cheat sheet. ▼ -->

- **Runtime:** Node 20 LTS · Yarn Workspaces + Nx
- **Framework:** React 18 + Vite 5 (Module Federation remote) · RTK Query · Flowbite React + Tailwind
- **Validation:** TypeScript types (RTK Query slice types are the API contract)
- **Lint / types / tests:** ESLint 9 + Prettier 3.3 / tsc --noEmit (TS strict) / Vitest (unit) + Cypress/Cucumber (E2E in `apps/wc-e2e/`)

<!-- ▲ END EXAMPLE BLOCK [id=area-stack] ▲ -->

## Standard commands

```bash
# Install deps (run once; re-run when the manifest changes)
yarn install

# Run the dev server (if applicable)
yarn nx dev wc-web

# Tests
yarn nx test wc-web

# Quality
yarn nx lint wc-web
yarn prettier --check .
yarn nx typecheck wc-web

# Preflight (use before saying "done" with a feature)
yarn nx lint wc-web && yarn nx typecheck wc-web && yarn nx test wc-web
```

## TDD protocol

**Write the failing test first.** Applies to deterministic code — see the TDD posture in root `CLAUDE.md` for what is test-first vs. exempt.

**Commit per slice when practical.** Never bundle a safety-critical slice with anything else.

## Forbidden patterns

<!-- ▼ EXAMPLE BLOCK [id=forbidden-patterns]: forbidden patterns — 3-5 narrow, enforceable, domain-specific rules. Shape: "Don't <pattern X> because <reason / past incident>; use <alternative Y>." Test-pin them where possible. Starts small; accretes as lessons surface. ▼ -->

Do not:

1. **Write a component/slice without a failing Vitest test first** (deterministic logic; visual-only is exempt).
2. **Use Redux Saga or Thunk** — RTK Query only; mutations invalidate cache tags.
3. **Use CSS Modules or styled-components** — Tailwind utility classes + Flowbite React only.
4. **Use `dangerouslySetInnerHTML`** — render user text via React default escaping (stored-XSS surface).
5. **Apply optimistic updates** — mutations refetch/invalidate; render explicit loading/empty/error/success states; surface the API `safeMessage` as Cypress-assertable error text.
6. **Leak demo/persona logic into the exposed remote** — `PersonaSwitcher` + demo-header branch live only in `src/standalone/` and are tree-shaken out of the Module Federation build.

<!-- ▲ END EXAMPLE BLOCK [id=forbidden-patterns] ▲ -->

## Cross-doc invariants — schema/docs mirroring

Several typed models in this codebase are **contracts** mirrored in `ARCHITECTURE.md` and indexed in the table below. The architecture doc is the canonical contract; the model is the executable enforcement. Drift produces silent disagreement.

**Authoring discipline (orchestrator owns this table).** When the implementer adds, removes, or renames a field on one of these models, the implementer **flags it at Step 9 categorized as `Cross-doc invariant change`** per the routing matrix in `docs/orchestrator-briefing.md`. The implementer does NOT edit `apps/wc-web/CLAUDE.md` or `ARCHITECTURE.md` directly — the orchestrator writes the table row + the architecture edit hot during the same session. Working-tree state aligns within the round; commits stagger (implementer's slice commit lands code+tests; orchestrator's round commit lands the doc rows).

| Model | `ARCHITECTURE.md` section | Notes |
|---|---|---|
| <model> | §X | <field summary> |

<!-- Starts empty (or with the first model if one exists). Populated as contract models land. -->

## Module organization

<!-- ▼ EXAMPLE BLOCK [id=module-layout]: module layout + layer dependency rule. Replace with the project's real directory tree and import-direction DAG. ▼ -->

```
apps/wc-web/src/
  remote/WeeklyCommitApp.tsx     # exposed Module Federation module (consumes host router; no BrowserRouter/persona)
  standalone/main.tsx            # owns BrowserRouter + store + identity provider + PersonaSwitcher (demo-only)
  app/{store,baseApi,authAccessor,tags}.ts
  routes/AppRoutes.tsx           # lazy routes
  features/<domain>/             # per-domain RTK Query api slice + components
  shared/components|lib/
```

Layer dependency direction (top depends on bottom, never reverse):

```
features/* → app/baseApi + shared/*
remote + standalone mount features
no feature imports another feature's internals (cross-feature via the api slices/store only)
```

Cross-cutting layers can be imported from anywhere. Enforce the rule mechanically with a test where possible — the test *is* the spec for the rule.

<!-- ▲ END EXAMPLE BLOCK [id=module-layout] ▲ -->

## Subagents

See `.claude/agents/README.md` for the canonical inventory + integration points.

<!-- ▼ EXAMPLE BLOCK [id=area-subagent-candidates]: area-specific subagent candidates — list candidates that would earn their keep specifically in this area (e.g. an ABI/types syncer for a frontend area, a Pyth/feed verifier for a contracts area). Build only on real friction. ▼ -->

Candidates (build only on real friction): an **RTK-Query / DTO type syncer** (keeps slice types aligned with `ARCHITECTURE.md` Appendix B); a **view-state coverage checker** (asserts each data view renders loading/empty/error/success).

<!-- ▲ END EXAMPLE BLOCK [id=area-subagent-candidates] ▲ -->

## Lessons logged from prior sessions

The full prose for each lesson lives in `apps/wc-web/LESSONS.md`. This index is the compact orientation surface.

**Lesson numbers are stable IDs** — once assigned, they don't change. New lessons get the next sequential number. `/session-end` proposes additions when it detects them; the user approves before the entry is written and a row is added here.

Lessons start at §1.

| # | Date | Topic | Rule (one-liner) |
|--:|---|---|---|
| | | | |

<!-- Starts empty. Each row links to its `LESSONS.md` anchor. -->

<!-- Slash commands: see root CLAUDE.md "Slash commands available." Implementer pair: /session-start + /session-end. -->
