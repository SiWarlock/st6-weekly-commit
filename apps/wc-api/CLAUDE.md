# ST6 Weekly Commit Module `apps/wc-api/` — Build Guide

> **You're in `apps/wc-api/`.** This file plus root `CLAUDE.md` both load. The root file covers global project conventions + shared comm rules (track-prefix, escalation taxonomy, messaging budget); this file owns code-area conventions for backend.

## Launch protocol

| Working on... | cwd | Loads |
|---|---|---|
| Planning / docs / commits | repo root (`ST6/`) | root `CLAUDE.md` only |
| backend code | `apps/wc-api/` | this `CLAUDE.md` + root |

<!-- For a multi-area project, add a row per additional code area. -->

If you find yourself fighting the wrong conventions, check your cwd.

## Session start/end protocol

**At session start:**
1. Read `MVP_TASKS.md` (repo root) → "Currently in progress" section.
2. Confirm with the user what feature this session is targeting.
3. Read the relevant section of `ARCHITECTURE.md` from the lookup table below.

**At session end** (only when the user explicitly says we're done):

1. **Implementer runs `/session-end`.** Implementer writes ONLY:
   - `apps/wc-api/` code files (the slice's implementation)
   - test files (the slice's tests)
   - dependency manifest / lockfile (deps the slice adds)
   - `docs/sessions/<NNN>-<date>-<topic>.md` (session doc, created at `/session-end` Step 5)

   **Implementer must NOT touch (all orchestrator territory):**
   - `MVP_TASKS.md`
   - `apps/wc-api/LESSONS.md`
   - `apps/wc-api/CLAUDE.md` (entire file — both the Cross-doc invariants table AND the Lessons logged index)
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
| Plan lifecycle (`DRAFT→LOCKED→RECONCILING→RECONCILED`) | `ARCHITECTURE.md` | §3 |
| Lessons logged (full prose) | `apps/wc-api/LESSONS.md` | by lesson # |

<!-- Starts near-empty. Add a row whenever a topic is looked up twice. -->

**Code intelligence & docs (when available):** prefer a code-intelligence MCP (e.g. CodeGraph) for code navigation / callers / traces over `grep`+read loops, and a docs MCP (e.g. Context7) for up-to-date library/API docs — see root `CLAUDE.md` "Code intelligence & docs." No-op if not installed.

## Stack

<!-- ▼ EXAMPLE BLOCK [id=area-stack]: stack quick-reference for implementer sessions. Canonical stack lives in root CLAUDE.md + ARCHITECTURE.md; this is the cheat sheet. ▼ -->

- **Runtime / build:** Java 21 · Gradle multi-module (shared / api / worker)
- **Framework:** Spring Boot 3.3 (Spring MVC · Spring Data JPA + Hibernate · Spring Security OAuth2 resource server)
- **Migrations / DB:** Flyway · PostgreSQL 16 (RDS, latest 16.x) — Flyway enabled ONLY on the migration Job
- **Validation:** Jakarta Bean Validation (Hibernate Validator) on every request DTO
- **Lint / types / tests:** Spotless + SpotBugs / javac (statically typed) + SpotBugs / JUnit 5 + Testcontainers · JaCoCo ≥80%/module

<!-- ▲ END EXAMPLE BLOCK [id=area-stack] ▲ -->

## Standard commands

```bash
# Install deps (run once; re-run when the manifest changes)
./gradlew build -x test

# Run the dev server (if applicable)
./gradlew :api:bootRun

# Tests
./gradlew test

# Quality
./gradlew spotbugsMain
./gradlew spotlessCheck
./gradlew compileJava

# Preflight (use before saying "done" with a feature)
./gradlew spotbugsMain && ./gradlew compileJava && ./gradlew test
```

## TDD protocol

**Write the failing test first.** Applies to deterministic code — see the TDD posture in root `CLAUDE.md` for what is test-first vs. exempt.

**Commit per slice when practical.** Never bundle a safety-critical slice with anything else.

## Forbidden patterns

<!-- ▼ EXAMPLE BLOCK [id=forbidden-patterns]: forbidden patterns — 3-5 narrow, enforceable, domain-specific rules. Shape: "Don't <pattern X> because <reason / past incident>; use <alternative Y>." Test-pin them where possible. Starts small; accretes as lessons surface. ▼ -->

Do not:

1. **Write code without a failing test first** (deterministic backend logic). Even one-line methods.
2. **Use Lombok `@Data`** — use `@Getter`/`@Setter`/`@Builder`; `@Data`'s generated `equals`/`hashCode` on JPA entities breaks Hibernate identity.
3. **Return JPA entities across the API boundary** — map to DTOs (per Appendix B); entities leaking causes lazy-init + over-exposure bugs.
4. **Put lifecycle transitions in controllers** — transitions live in service methods (`PlanLifecycleService`, etc.) inside one transaction with validation + projection updates.
5. **Store `OVERDUE` as a review status** — it is derived at read time via an injectable `Clock`.
6. **Let an Outlook/SNS failure roll back a core mutation** — the sync record is a downstream outbox; failures are caught, never rethrown into the core txn.

<!-- ▲ END EXAMPLE BLOCK [id=forbidden-patterns] ▲ -->

## Cross-doc invariants — schema/docs mirroring

Several typed models in this codebase are **contracts** mirrored in `ARCHITECTURE.md` and indexed in the table below. The architecture doc is the canonical contract; the model is the executable enforcement. Drift produces silent disagreement.

**Authoring discipline (orchestrator owns this table).** When the implementer adds, removes, or renames a field on one of these models, the implementer **flags it at Step 9 categorized as `Cross-doc invariant change`** per the routing matrix in `docs/orchestrator-briefing.md`. The implementer does NOT edit `apps/wc-api/CLAUDE.md` or `ARCHITECTURE.md` directly — the orchestrator writes the table row + the architecture edit hot during the same session. Working-tree state aligns within the round; commits stagger (implementer's slice commit lands code+tests; orchestrator's round commit lands the doc rows).

| Model | `ARCHITECTURE.md` section | Notes |
|---|---|---|
| WeeklyPlan | §3 / Appendix A | Lifecycle state + locked baseline; mirror field changes into Appendix A. |

<!-- Starts empty (or with the first model if one exists). Populated as contract models land. -->

## Module organization

<!-- ▼ EXAMPLE BLOCK [id=module-layout]: module layout + layer dependency rule. Replace with the project's real directory tree and import-direction DAG. ▼ -->

```
apps/wc-api/
  settings.gradle               # include 'shared','api','worker'
  shared/  src/main/java/com/st6/wc/   # JPA entities, enums, DTOs, repos (depended on by api + worker)
  api/     src/main/java/com/st6/wc/   # controllers, services, config (security/cors/jwt/flyway), jobs (generation, migration), auth, sns, web
  worker/  src/main/java/com/st6/wc/worker/   # SQS listener, graph adapters, sync-record service
```

Dependency direction (top depends on bottom, never reverse):

```
api  →  shared
worker → shared          (no api ↔ worker edge)
controllers → services → repositories → entities   (controllers never call repositories directly)
```

Cross-cutting layers can be imported from anywhere. Enforce the rule mechanically with a test where possible — the test *is* the spec for the rule.

<!-- ▲ END EXAMPLE BLOCK [id=module-layout] ▲ -->

## Subagents

See `.claude/agents/README.md` for the canonical inventory + integration points.

<!-- ▼ EXAMPLE BLOCK [id=area-subagent-candidates]: area-specific subagent candidates — list candidates that would earn their keep specifically in this area (e.g. an ABI/types syncer for a frontend area, a Pyth/feed verifier for a contracts area). Build only on real friction. ▼ -->

Candidates (build only on real friction): an **IDOR/authorization property-test writer** (generates the per-denial-case matrix); an **invariant test writer** for lifecycle/baseline-immutability; a **DTO↔entity mapper checker**.

<!-- ▲ END EXAMPLE BLOCK [id=area-subagent-candidates] ▲ -->

## Lessons logged from prior sessions

The full prose for each lesson lives in `apps/wc-api/LESSONS.md`. This index is the compact orientation surface.

**Lesson numbers are stable IDs** — once assigned, they don't change. New lessons get the next sequential number. `/session-end` proposes additions when it detects them; the user approves before the entry is written and a row is added here.

Lessons start at §1.

| # | Date | Topic | Rule (one-liner) |
|--:|---|---|---|
| | | | |

<!-- Starts empty. Each row links to its `LESSONS.md` anchor. -->

<!-- Slash commands: see root CLAUDE.md "Slash commands available." Implementer pair: /session-start + /session-end. -->
