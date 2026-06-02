# /tdd Brief — Template

> The orchestrator session uses this template to author hand-offs to the implementer session. The implementer session reads the brief as context, then runs `/tdd <feature>` — the slash command's Step 0 (Restate) confirms the brief was parsed correctly; Steps 1–10 execute against the spec.

This doc is the canonical reference both sessions use:

- **Orchestrator** writes briefs in this format. The "Things to flag at Step 2.5" section is pre-loaded with design questions + default votes.
- **Implementer** reads the brief, runs `/tdd`, and at Step 9 surfaces categorized flags per the brief's "Lessons-logged candidates anticipated" section.

**TDD scope:** applies to deterministic code — code where a failing test can pin the behavior before the implementation exists. When a slice involves non-deterministic work (LLM-driven generation, pure visual changes), drop the `/tdd` brief in favor of the project's non-deterministic-coverage brief format (e.g. eval-fixture-first, or design-fixture-first).

Briefs scale down for trivial slices — drop sections that don't apply rather than truncating remaining ones.

---

## Where briefs live

Every `/tdd` brief is authored as a **file** in `docs/briefs/`, not just pasted ephemerally into the implementer session. Briefs are a permanent artifact — the design-decision audit trail for every slice the project has run.

**Naming:** `docs/briefs/NNN-<task-id>-<short-topic>.md` — e.g. `024-P3-2-payment-retry-logic.md`.

- **`NNN`** — a stable, zero-padded, sequential id on its own counter (parallel to `docs/sessions/`). Compute it the way session docs do: `ls docs/briefs/`, find the highest `NNN` prefix, increment. Numbers are stable IDs — never reused, never reordered.
- **`<task-id>`** — the `MVP_TASKS.md` task this brief implements. Ties the brief to its phase.
- **`<short-topic>`** — kebab-case feature topic.

**Who writes + commits it:** the orchestrator authors the brief file; it rides the orchestrator's `/orchestrate-end` round terminal commit (`docs/briefs/` is orchestrator territory — the implementer never edits it). When a stale brief is refreshed for a re-run, **edit the existing file in place** rather than spawning a new number — the brief tracks the slice, not the attempt.

---

## Template format

```markdown
# /tdd brief — <feature_name>

## Feature
<one sentence: what's being built>

## Use case + traceability
- **Task ID:** <e.g. P3.2>
- **Architecture sections it implements:** <`ARCHITECTURE.md §X.Y`>
- **Related context:** <other docs / prior slices the implementer should know>

## Acceptance criteria (what "done" means)
- [ ] <concrete behavior pin 1>
- [ ] <concrete behavior pin 2>
- [ ] All unit tests in `<path>` pass
- [ ] Integration test in `<path>` passes
- [ ] `/preflight` clean
- [ ] If applicable: cross-doc invariant updated atomic with the model change

## Files expected to touch
**New:**
- `<path>` — <what it does>

**Modified:**
- `<path>` — <what changes>

If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline (Step 2)
Tests to write in `<test_path>`:

1. **`test_<name>`** — <one-sentence contract>
   - Asserts: <specific assertion>
   - Why: <pin to `ARCHITECTURE.md §X` or a LESSONS entry>

2. **`test_<name>`** — ...

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** <none / list of contract models touched>
- **Orchestrator doc rows to write hot (Step 9 routing):** <none / which <area>/CLAUDE.md cross-doc rows + `ARCHITECTURE.md` Appendix A rows the orchestrator authors atomic with the round>

> **Implementer never edits <area>/CLAUDE.md, `ARCHITECTURE.md`, `MVP_TASKS.md`, or <area>/LESSONS.md** — these are orchestrator territory. Flag at Step 9 categorized; orchestrator writes hot during the same session; orchestrator commits at `/orchestrate-end`.

## Things to flag at Step 2.5
Open design questions the implementer should surface before going GREEN. Pre-loaded with default votes — the implementer can take defaults or ping back with disagreement.

1. **<Question>.** <Two or three plausible answers.> My default vote: **<recommendation>**. <one-sentence rationale>.
2. **<Question>.** ...

## Dependencies + sequencing
- **Depends on:** <prior slices that must have landed>
- **Blocks:** <future slices that need this>

## Estimated commit count

**Prefer bundling when safe** — default to 2-4 related tasks per slice when bundling makes sense. A "slice" is one focused feature OR a small bundle of related features that share a commit. Bundling saves time + reduces Step-2.5 review overhead + commit verbosity without losing rigor.

**Bundle when ALL apply:**
- All features touch the same code area
- Total size is manageable (rough heuristic: < 100 lines added, < 30 min of TDD work)
- Features share context (similar setup, related concepts, overlapping test files)
- None of the features touch a safety invariant (per root `CLAUDE.md` "Key safety rules")
- Bisectability stays meaningful (the bundle is one logical unit)
- A reviewer can grok the whole thing in one sitting

**Do NOT bundle when ANY apply:**
- A safety-critical pin is in the slice (gets its OWN commit, always)
- Cross-area work
- Features have conflicting Step-2.5 design questions
- Each feature is large on its own (≥ 30 lines added each)
- Features have independent caller bases and might be cherry-picked separately
- A cross-doc invariant change is involved (atomic doc-edit pairing wants traceability)

Examples:
- ❌ DON'T: "add staleness check to oracle + add operator role to factory" (mixes safety invariants)
- ✅ DO: "add idempotency key + add retry helper + wire into payment handler" (3 related features, same module)>

## Lessons-logged candidates anticipated
Pre-bets the orchestrator is making about what Step 9 will surface.

- **Convention candidate** — <pattern likely to recur>
- **Future TODO — operational** — <perf/scaling consideration>
- **Architecture-doc note candidate** — <behavior consumers will depend on>

## How to invoke

> Do NOT prescribe `/session-start` here. Implementer sessions are reused across slices within a round — the session is already oriented. `/session-start` belongs only in the FIRST slice of a session, or after an explicit session swap. Jump straight to pre-flight checks + `/tdd <feature_name>`.

1. **Read this brief end-to-end.** Don't skip "Things to flag at Step 2.5" — design questions need answers before tests.
2. **Run `/tdd <feature_name>`** in the implementer session.
3. **Step 0 (Restate)** — confirm the restatement matches the Feature line.
4. **Step 1 (Identify files)** — confirm the file list matches Files expected to touch.
5. **Step 2.5 (test review pause)** — ping back with answers to the design questions (or take defaults). Don't proceed to Step 4 until orchestrator + user sign off.
6. **Step 9 (summarize)** — surface anything that didn't fit the anticipated lessons-logged candidates.
```

---

## Worked example

<!-- ▼ EXAMPLE BLOCK [id=tdd-brief-worked-example]: worked example — illustrative format reference, NOT project content. Replace with a worked example from this project once the first real brief lands, OR keep this one labelled as illustrative. ▼ -->

> **Worked example — brief for task 3.5 (lock enforcement).** *Feature:* `POST /api/plans/{id}/lock` enforces required Supporting Outcome + freezes the planned baseline. *Traceability:* ARCHITECTURE.md §3 (plan lifecycle), §5 (E8 preconditions), Appendix A (WeeklyPlan, WeeklyCommitment). *Acceptance (behaviors):* lock rejected with `409 EMPTY_PLAN_LOCK` when no planned commitments; rejected `409 UNLINKED_PLANNED_COMMITMENT` (with `fieldErrors[]`) when any planned commitment lacks `supporting_outcome_id`; on success → state `LOCKED`, `locked_at` set, baseline frozen, `manager_review` created (`NOT_REVIEWED` + `reviewDueAt`), projections + audit + `IC_PLANNING` sync record written. *Files:* extend `api/.../plan/PlanLifecycleService.java`, `plan/PlanController.java`; tests in `api/src/test/.../plan/`. *Cross-doc invariant:* extended (WeeklyPlan). *Step-2.5 things to flag:* test the unlinked-rejection path AND the success path; assert baseline immutability post-lock; assert sync failure is non-blocking (stub). *Estimated commit count:* 1 (do NOT bundle — this is a safety-critical invariant slice).

<!-- ▲ END EXAMPLE BLOCK [id=tdd-brief-worked-example] ▲ -->

---

## Why this format works

- **The brief lives outside the slash command's prompt loop.** It's context the implementer reads before invoking `/tdd`. Step 0 becomes the *check* that the brief was parsed correctly, not the *source* of the spec.
- **Step 2.5 design questions are pre-loaded.** Without them, the implementer either makes unilateral decisions or pauses Step 2.5 to ask. Pre-loading 3-4 plausible questions with default votes lets the implementer take defaults (fast path) or ping back with real disagreement (slower but correct).
- **Cross-references inline.** The implementer can look up underlying findings without navigating whole docs.
- **Acceptance criteria are concrete behaviors, not abstractions.** "Confidence <0.7 escalates" is testable; "the judge is reliable" is not.
- **Cross-doc invariant impact named explicitly even when "none."** Forces the orchestrator to actually check.

The format scales down for trivial slices by dropping sections that don't apply — not by truncating remaining ones.

---

## Common pitfalls (orchestrator self-check before handing the brief over)

### Pitfall — Bundling a safety-critical slice with anything else

Symptom: a brief bundles a safety-critical pin (an authorization gate, an isolation boundary, a data-handling invariant) with unrelated work. The safety pin ends up in a commit that also carries other changes, making it harder to bisect a regression and harder to review the safety pin on its own.

**Rule** — every safety-critical slice gets its own commit. The brief's "Estimated commit count" should call it out explicitly when one of the acceptance criteria is a safety pin.

### Pitfall — Over-atomizing trivial slices

Symptom: a brief authored for one 8-line helper. Then another for the next 12-line helper. Then another for wiring them together. Three separate `/tdd` cycles, three Step-2.5 reviews, three commits — when the whole thing could have shipped as one slice with three features.

**Rule** — when 2-4 small related features could ship as one brief without violating any "Do NOT bundle" criterion (see "Estimated commit count"), bundle them. The bundled brief lists each feature in its own RED-test section; the implementer goes RED → 2.5 → GREEN for each feature in sequence, then one Step-10 commit at the end. Saves time + review overhead without losing rigor.

The default posture is **"bundle when safe, atomize only when required"** — not the other way around.

### Pitfall — Skipping Step 2.5 design questions because the brief "felt small"

Symptom: a brief omits "Things to flag at Step 2.5" because the slice is "obvious." The implementer then makes ≥3 design decisions silently during GREEN — the orchestrator finds out at Step 9 with no review opportunity.

**Rule** — every brief has at least one pre-loaded Step 2.5 question even when the slice feels trivial. If you can't find a real design question, the slice is probably implementing something already-decided and doesn't need a brief at all.

### Pitfall — Acceptance criteria phrased as abstractions instead of behaviors

Symptom: "the parser is robust" / "storage is performant" / "the API is well-designed." Not testable.

**Rule** — every acceptance criterion is a concrete behavior pin: "filter-by-category returns the subset," "round-trip preserves equality." If you can't write a test for it, it's not an acceptance criterion.

### Pitfall — Prescribing `/session-start` in the brief's "How to invoke"

Symptom: the brief's "How to invoke" lists `/session-start` as Step 1. The implementer reuses the same terminal across slices in a round — session context is already oriented — so re-running `/session-start` is redundant friction.

**Rule** — "How to invoke" jumps straight to pre-flight checks + `/tdd <feature_name>`. Include `/session-start` ONLY for the first slice of a session or after an explicit session swap.

<!-- ▼ EXAMPLE BLOCK [id=project-specific-pitfalls]: project-specific pitfalls — the source project accreted several more pitfalls unique to its domain (contract-type placement, model-ID verification against live catalogs, matrix-driven brief file-list reconciliation, agent-existence ≠ pipeline-readiness). Add the project's own recurring brief-authoring mistakes here as they emerge — each one is cheap insurance against a repeat. ▼ -->

- **Never bundle the lock-enforcement, authorization/IDOR, baseline-immutability, or non-blocking-sync slices** with anything else — each is a standalone safety-critical commit.
- **Every Outlook-sync task asserts the non-blocking guarantee** (core mutation commits even when the publish/Graph call fails).
- **`OVERDUE` is derived** — never write a test that expects a stored `OVERDUE` status; test the read-time derivation with an injectable `Clock`.
- **Comments are flat** — don't write tests/impl for nested threading; `parent_comment_id` is always null in MVP.
- **Manager review-block sync is per-manager/week** — keyed on `(owner=manager, week_start_date)`; the first direct-report lock creates it, later locks are idempotent no-ops.

<!-- ▲ END EXAMPLE BLOCK [id=project-specific-pitfalls] ▲ -->

---

## When NOT to use a /tdd brief

The brief is for **TDD slices** (deterministic code). Skip (or use a simpler hand-off) for:

- **Pure documentation work** (`MVP_TASKS.md` edits, `ARCHITECTURE.md` prose, session docs). Just edit directly.
- **Infrastructure / deploy work.** Use `docs/runbooks/` instead.
- **Exploratory spikes** to learn an API. Mark as exploratory; throw away; then TDD the real implementation.
- **Non-deterministic behavior** (LLM-driven generation, pure visual changes). Use the project's non-deterministic-coverage brief format instead.

Outside these cases, brief in this format. Every time.
