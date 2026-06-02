# ST6 Weekly Commit Module — Scaffolding Reference

> Project-specific map of this repo's Claude Code scaffolding. Documents what each piece is and how this project adapts the universal agent-team pattern.
>
> **For the universal pattern documented end-to-end**, see `SCAFFOLDING-GUIDE.md` (the project-agnostic guide this scaffolding was generated from). This file is the project-specific instance.

---

## TL;DR

This project runs the **agent-team orchestrator + implementer pattern** (three roles + human). Same slash commands, same Step-9 routing matrix, same N+2 commit cadence, same cross-doc invariants discipline as the universal pattern. **Adaptations are project-shaped:** this project's stack, code areas, phase plan (0–13 (tasks `<phase>.<n>`, e.g. 3.4)), forbidden patterns, and architecture are its own.

_(Single-operator fallback: drop the team-lead row + `/team-start`/`/team-end`; the human bridges between orchestrator + implementer sessions.)_

---

## File inventory

```
ST6/
├── .claude/
│   ├── commands/                       # Slash commands
│   └── agents/                         # Subagents (opt-in starter set + reactive additions)
├── apps/wc-api/
│   ├── CLAUDE.md                       # Code-area conventions
│   └── LESSONS.md                      # Lessons logged (§1+)
├── docs/
│   ├── team-protocol.md                # Loaded by /team-start (team pattern only)
│   ├── orchestrator-briefing.md        # Loaded by /orchestrate-start
│   ├── tdd-brief-template.md           # /tdd brief format
│   ├── scaffolding-reference.md        # THIS FILE
│   ├── team-handoffs/                  # /team-end outputs (team pattern only)
│   ├── briefs/                         # Numbered /tdd briefs (NNN-<task-id>-<topic>.md)
│   ├── sessions/                       # Numbered chronological session docs
│   └── runbooks/                       # Operational procedures
├── CLAUDE.md                           # Root — project conventions + shared comm rules
├── MVP_TASKS.md                    # Task tracker
└── ARCHITECTURE.md                 # Architecture / design contract

# User-global (~/.claude/) — populated at /team-start by spawn prompts (team mode only):
~/.claude/
├── statusline-command.sh               # Status line + heartbeat writer (install once)
├── scripts/
│   └── check-team-context.sh           # /context-check helper (install once)
├── team-registry/                      # Per-session: {session_id, name, team, role, cwd, ts}
│   └── <session_id>.json               # Written by teammate at startup via spawn prompt
├── heartbeats/                         # Per-session ctx_pct heartbeats (status line writes IFF registry exists)
│   └── <session_id>.json               # Updated every status line refresh
└── team-history/                       # Per-slice trajectory data
    └── <team>/<name>.jsonl             # Appended by orch each slice for 3-slice rolling growth
```

<!-- ▼ EXAMPLE BLOCK [id=inventory-extension]: extend the inventory with the project's real layout — extra code areas, deliverable docs, eval suites. ▼ -->
This project's scaffolding: 12 slash commands (`.claude/commands/`: team-start, team-end, orchestrate-start, orchestrate-end, session-start, session-end, tdd, preflight, run-tests, check-arch, wired, context-check); 3 subagents (`.claude/agents/`: code-quality-reviewer, security-reviewer, reachability-auditor — no brief-drafter); layered `CLAUDE.md` (root + `apps/wc-api/` + `apps/wc-web/` + `infra/`), each area with a `LESSONS.md`; `docs/` carries team-protocol, orchestrator-briefing, tdd-brief-template, scaffolding-reference + briefs/sessions/runbooks/team-handoffs. The binding contract `ARCHITECTURE.md` + tracker `MVP_TASKS.md` were produced by the upstream `/arch-finalize` + `/tasks-gen` stages and are preserved as canonical.
<!-- ▲ END EXAMPLE BLOCK [id=inventory-extension] ▲ -->

---

## Team pattern (three roles + human)

Full topology + escalation rules: `docs/team-protocol.md` (team pattern) + root `CLAUDE.md` "Team coordination — shared rules" (all roles).

| Role | cwd | Loads |
|---|---|---|
| Team lead (thin) | repo root | root `CLAUDE.md` + `docs/team-protocol.md` |
| Orchestrator | repo root | root `CLAUDE.md` + `docs/orchestrator-briefing.md` |
| Implementer (per area) | `apps/wc-api/` | area `CLAUDE.md` + root `CLAUDE.md` |

*Teammate names: `<track>-<area>-<role>` when parallel teams run in the same repo, else `<area>-<role>`. The track prefix is load-bearing for cross-bleed prevention.*

- **Team lead:** `/team-start` + `/team-end`, **escalation conduit** to the human. **Stateless between events; persists across orchestrator/implementer session cycles; stays lean both ways** — no relaying down, no per-slice narration up, no task tracker (re-reads `MVP_TASKS.md` on demand for cycles/escalations); upward output only for the **close-out gate** (user-on-demand OR auto-cycle when context monitoring detects ACTION threshold) + the 4 escalation categories + context tier surfaces. **At auto-cycle: BOTH orch + impl cycle together** for clean handoff.
- **Orchestrator:** planning, scope, docs, **Step-2.5 review**, commit messages, push, Step-9 routing, `/orchestrate-end`.
- **Implementer:** `/tdd` cycles, `/preflight`, `/session-end`, code commits only.

Orchestrator and implementers communicate **directly**. The lead is looped in **only** for the 4 escalation categories.

---

## Slash commands

| Command | Purpose |
|---|---|
| `/team-start [track]` | _(lead)_ stand up the team; establish direct comms + escalation |
| `/team-end` | _(lead)_ close out the team session; write handoff doc (user-on-demand or auto-cycle) |
| `/orchestrate-start` | Orient an orchestrator session |
| `/orchestrate-end` | Orchestrator close-out + Carry-forward triage + round commit + push |
| `/session-start` | Orient an implementer session |
| `/session-end` | Implementer close-out (incl. wiring/reachability audit) |
| `/tdd <feature>` | TDD discipline walker — deterministic code (Step 2.5 design review + Step 7.5 reachability) |
| `/wired <feature>` | Trace a feature's call path from a production entry point |
| `/context-check [team]` | _(team mode)_ report per-teammate context usage; auto-flow + manual |
| `/preflight` | Quality gate |
| `/run-tests [class]` | Typed test runner shortcut |
| `/check-arch <topic>` | Architecture doc lookup |
| `/eval [category]` | _(optional)_ runs an eval class |
| `/trace <id>` | _(optional)_ pulls a structured trace |

_( Single-operator fallback: remove the `/team-start` and `/team-end` rows. )_

---

## Workflow patterns

### Per-slice TDD round

1. Orchestrator authors a brief → `docs/briefs/NNN-<task-id>-<topic>.md`
2. Orchestrator sends brief reference directly to the area implementer
3. Implementer runs `/tdd <feature>` → Step 0 (self-check in team mode; user-confirm in single-operator) → Step 1 → Step 2 RED → Step 2.5 design write-up
4. Orchestrator reviews + replies directly (approve/tweak/add); escalates a safety design Q if needed
5. Implementer Steps 3-7 (confirm RED → GREEN → refactor → suite)
6. **Step 7.5 reachability check** — confirm wiring from a production entry point
7. Step 8 lint+typecheck (optional: parallel `code-quality-reviewer` + `security-reviewer` if installed)
8. Implementer sends categorized Step-9 summary directly to orchestrator
9. Orchestrator routes hot (commit-message-first reply); escalates deferments / safety findings / load-bearing architectural Option A/B/C calls
10. Implementer Step 10: commits the slice with the orchestrator-authored message
11. Implementer sends "done with slice — `<commit hash>`" one-liner
12. **(Team mode)** Orchestrator runs `/context-check <team>` + appends per-slice snapshot to history file + sends structured ping to lead with the report. Lead processes silently unless threshold tier crossed.
13. Repeat

### Context monitoring + auto-cycle (team mode only)

Each team-mode teammate's status line writes a heartbeat to `~/.claude/heartbeats/<session_id>.json` (ctx_pct, tokens, cost, rate limits). The heartbeat write is gated on `~/.claude/team-registry/<session_id>.json` existing for that session — written by the teammate at startup via the `/team-start` spawn prompt. **Solo (non-team) sessions never write registry entries → no heartbeats → no monitoring for them.**

Per-slice flow (Step 12 above): the orchestrator runs `/context-check <team>` after every Step-10 commit, pings the lead with the report. Lead evaluates against thresholds:

- **OK** (< 70%) — silent log
- **WARN** (70-74%) — one-line surface + trajectory
- **ACTION** (75-79%) — auto-trigger close-out cycle at this clean break
- **HARD-STOP** (≥ 80%) — halt dispatch + immediate cycle

Full flow + auto-cycle mechanics in `docs/team-protocol.md` "Context monitoring + auto-cycle." Thresholds configurable via `CLAUDE_TEAM_CTX_WARN` / `CLAUDE_TEAM_CTX_ACTION` / `CLAUDE_TEAM_CTX_HARD` env vars.

### Step-9 routing matrix

Canonical copy in `docs/orchestrator-briefing.md` — **don't re-copy it; point there.** Routed hot: Convention candidate → prose in `LESSONS.md` + one-line index row with anchor link · Architecture doc note → `ARCHITECTURE.md §` · Future TODO *belongs-to-a-phase* → real task checkbox in that phase · *next-brief* → Carry-forward · *out-of-scope* → deferment escalation · Cross-doc invariant change → orchestrator writes the doc rows · Completed work → tick checkbox.

### Carry-forward triage at `/orchestrate-end`

Five outcomes: DELETE / KEEP / **INLINE-TARGET (→ real task checkbox, not an annotation)** / DEFER (escalate) / SPREAD.

### Reachability (tested ≠ shipped)

`/tdd` Step 7.5 + `/wired` prove each feature is invoked from a real entry point; the `reachability-auditor` subagent (if installed) audits an entire code area at phase boundaries.

### Commit cadence

N slice commits + 1 session-doc commit + 1 round commit = **N + 2** per round. Push once at `/orchestrate-end` — **only when a remote is configured** (to **origin (not yet configured — push deferred until a remote is set)**).

---

## Project-specific conventions

<!-- ▼ EXAMPLE BLOCK [id=instance-conventions]: the conventions unique to this project — its architecture sentence (if any), its forbidden patterns, its key safety rules, its layer dependency rule, its cross-doc invariant set. These distinguish this project's instance from the universal pattern. ▼ -->

- **3 code areas, 3 implementer sessions:** `apps/wc-api/` (backend, Java/Gradle), `apps/wc-web/` (frontend, React/Yarn-Nx; carries `apps/wc-e2e` Cypress), `infra/` (Terraform/k8s — verified by validate/plan, not red-green).
- **Team mode, solo team-lead:** `<area>-<role>` naming (`wc-api-orchestrator`, `web-implementer`, `infra-implementer`).
- **Push:** to `origin` only at `/orchestrate-end`, once a remote is configured (none yet).
- **Phase anchors:** every `MVP_TASKS.md` phase cites `ARCHITECTURE.md §`; re-read at session start; drift → cross-doc flag at Step 9.

<!-- ▲ END EXAMPLE BLOCK [id=instance-conventions] ▲ -->

---

## State sources of truth

| Concern | Source of truth | Loaded by |
|---|---|---|
| Team topology + escalation rules | Root `CLAUDE.md` "Team coordination" + `docs/team-protocol.md` | `/team-start` (lead-specific) |
| Current state, "what's done, what's next" | `MVP_TASKS.md` | `/orchestrate-start` + `/session-start` |
| Technical narrative of just-landed work | Most recent `docs/sessions/<NNN>-*.md` | `/orchestrate-start` |
| Round ledger (thin pointer-lines) | `MVP_TASKS.md` "Log" | `/orchestrate-start` |
| Per-slice design audit trail | `docs/briefs/<NNN>-<task-id>-<topic>.md` | On-demand; latest at `/orchestrate-start` |
| Team-pause handoff state | Most recent `docs/team-handoffs/<NNN>-*.md` | `/team-start` (when resuming) |
| Conventions / patterns | `apps/wc-api/LESSONS.md` (prose) + `apps/wc-api/CLAUDE.md` (index) | On-demand |
| Architecture / design contract | `ARCHITECTURE.md` | On-demand via `/check-arch` |
| Workflow rules | `docs/orchestrator-briefing.md` (Step-9 matrix canonical); this doc | `/orchestrate-start` |
| `/tdd` brief format | `docs/tdd-brief-template.md` | Via `/orchestrate-start` |
| Universal scaffolding pattern | `SCAFFOLDING-GUIDE.md` (in the source scaffolding repo) | Reference only; not loaded per session |

The principle: **single source of truth per concern.** Drift between sources is a bug.

---

## How to evolve this scaffolding

- **New slash command** → file in `.claude/commands/` + add to root `CLAUDE.md` "Slash commands" + reference in `docs/orchestrator-briefing.md`.
- **New subagent** → file in `.claude/agents/` + `.claude/agents/README.md` inventory + area `CLAUDE.md` "Subagents".
- **New lesson** → next anchor in `apps/wc-api/LESSONS.md` + row in the `apps/wc-api/CLAUDE.md` index. Hot-routed at Step 9.
- **New convention** → entry in `apps/wc-api/CLAUDE.md` Forbidden patterns or root `CLAUDE.md` Key safety rules + a `LESSONS.md` entry if durable.
- **New cross-doc invariant** → row in the `apps/wc-api/CLAUDE.md` table + atomic `ARCHITECTURE.md` edit.
- **New escalation category** → root `CLAUDE.md` "Escalation taxonomy" + `docs/team-protocol.md` "What the lead does NOT do" cross-reference.

**Don't** add project *state* to scaffolding files — state lives in `MVP_TASKS.md`. **Don't** rename the cross-referenced files (`MVP_TASKS.md`, the `CLAUDE.md` files, `apps/wc-api/LESSONS.md`, `docs/team-protocol.md`, `docs/orchestrator-briefing.md`, `docs/tdd-brief-template.md`) casually — they're named inside slash command bodies; renaming is a multi-file ripple.

---

## Limits / known gaps

1. **Cross-team channel-bleed is a real failure mode** — the track-prefix naming rule + ignore-mismatched-prefix posture mitigate it but don't fully eliminate it.
2. **Documentation drift between a lesson and the code it governs is only audit-caught** — the cross-doc invariants table catches model↔spec drift mechanically; lesson↔code drift is not.
3. **Subagents aren't sandboxed** — their forbidden-patterns section is the only guard.
4. **HITL chokepoints stay HITL** — deploys, scope cuts, load-bearing architectural decisions, push approvals keep the user in the loop.
5. **The brief-drafter subagent (if installed) requires a quality trial before standard adoption** — briefs are load-bearing.

See `SCAFFOLDING-GUIDE.md §12` for the full list.

---

**End of scaffolding reference.** For the universal pattern, see `SCAFFOLDING-GUIDE.md`.
