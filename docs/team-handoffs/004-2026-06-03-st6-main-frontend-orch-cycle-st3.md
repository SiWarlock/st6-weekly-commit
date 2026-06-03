# Handoff 004 — st6-main frontend orchestrator cycle (post-ST.3, at ACTION 76%)

- **Date:** 2026-06-03 · **Track:** st6-main · **Cycling:** `st6-main-wc-web-orchestrator` (frontend orch) at ACTION 76%, clean ST.3 boundary. **Impl persists** (`st6-main-wc-web-implementer`, ~41%). Backend pair (`st6-main-orchestrator` + impl) untouched.
- **HEAD:** `cf609a5` (ST.3). **Last frontend round commit:** `1bd8050` (round-5 close-out, on backend `38a596d`). **170/170 Vitest green.**

## Where the frontend is
- **Phase-9 FUNCTIONAL surface COMPLETE** (9.1–9.13): IC workspace + reconciliation + edit/delete, manager command-center + heatmap/drilldown + mark-reviewed, comments, Outlook sync, host-integration contract doc. All server-authoritative (`allowedActions[]`/`plan.state` via `can()`), invalidate→refetch, no optimistic, `safeMessage` verbatim.
- **Phase-ST Cadence styling spine IN PROGRESS.** **ST.3 ✅** (`cf609a5`) — chess-layer atom skins (PriorityTag/WorkTypeTag/ConfidenceMeter/AlignmentChip), Tailwind/token-native, in `statusTaxonomy.ts` §7, wired into CommitmentList.

## NEXT (fresh orch): ST.4 → ST.5/ST.6 → ST.7
- **ST.4 (NEXT) — surface/density/elevation/motion.** Author the brief from the **BANKED ST.4 gap-map** (impl's session context + reframed in `MVP_TASKS` Carry-forward "ST.4 token-wiring"): **HIGH** wire `maxWidth` tokens (`reading-col` 1040 / `content-max` 1440) into `tailwind.config` + swap the 3 surfaces off Tailwind defaults (`WeeklyPlanView max-w-3xl`, `CommandCenter`/`HeatmapGrid max-w-6xl`); **HIGH-semantic** conditional `CommitmentList` card left-accent (unplanned→accent, disputed→failure — `.wc-card--accent-*` defined-but-uninvoked → implement Tailwind-native); **MED** `CommandCenter` `--row-height` 44px + sticky `thead`; **LOW** `animate-pulse`→150ms, hairline-top, drawer/modal+scrim (→ST.6).
- **ST.5/ST.6 — IC + manager visual composition** per the Cadence refs (`docs/design/cadence-design-system/ui_kits/weekly-commit/{CommitmentCard,CommandCenter,Heatmap}.jsx`). **Carry the open item:** the "Unplanned" kind-badge↔WorkTypeTag redundancy (both say "Unplanned" on `commitmentKind===UNPLANNED` rows) — resolve in ST.5 (suppress/differentiate one).
- **ST.7 — a11y** (color-never-the-only-signal + responsive) + **gstack `/design-review`** as the QA gate against `docs/design/cadence-design-system/preview/*.html` (**user-blessed**; surface its output; user keeps final eyeball).

## Locked constraints (ALL ST slices)
- **Render-only — NO enum/Appendix-A cross-doc change** (B.1 enums already in `dtos.ts`; you skin their display).
- **Tailwind/token-native — NO `.wc-*` CSS, no second stylesheet** (forbidden pattern #3; ST.3 has a structural test #7 guarding it). The Cadence `*.jsx`/`components.css` are the **visual spec only**; `statusTaxonomy.ts` is the §7 single-source.
- Approach locked: **A token-native, indigo brand, Fork-2**.

## Pending hot-routing (land at your first `/orchestrate-end`)
- **Tick ST.3 `cf609a5`** in `MVP_TASKS` — *DONE in this cycle's close-out commit* (the orch round commit on top of this handoff).
- **Optional (low):** fold a one-line wc-web LESSONS §7 note (`ToneLabel`/`ConfidenceEntry` lighter shapes for icon-less atoms — natural growth of the single-source pattern; skip if not worth it). Deferred — your call.
- Cross-doc rows for ST.3: **NONE** (render-only; `statusTaxonomy.ts` maps are visual, not `dtos.ts` contract).

## Blocked / cross-track
- **9.11a (disputes) BLOCKED + Phase-4-deferred.** The backend B.6 **Option-A** edit (nest `dispute?: AlignmentDisputeDto` in `WeeklyCommitmentDto` + drop `hasUnresolvedDispute`) is held behind Phase 4 (lead default). **`st6-main-orchestrator` (backend orch) pings you when it lands** → then mirror `dtos.ts` (coordinated, not ahead) + brief 9.11a. Detail: `docs/planning/023` §6. Don't touch until then.
- **Brief lane:** used **040** (ST.3); backend holds **041** (Phase 4). Next (ST.4) = **next-free per `ls docs/briefs/`** — coordinate with `st6-main-orchestrator` before claiming (both tracks authoring concurrently).
- **Shared-doc serialize (two-way w/ `st6-main-orchestrator`):** `ls`-first, ping-before-staging `MVP_TASKS`/`ARCHITECTURE`, whole-file your frontend regions, never sweep backend edits. ST = render-only → no `ARCHITECTURE` edit; only `MVP_TASKS` frontend regions at `/orchestrate-end`. Backend's next shared-doc touch is its Phase-4 `/orchestrate-end`.

## Read on resume
`docs/orchestrator-briefing.md`, `docs/tdd-brief-template.md`, brief `040` (ST.3), impl session doc `007`, Finding `docs/planning/023`, the Cadence design system (`docs/design/cadence-design-system/`), wc-web `LESSONS.md` §1–§13 + `CLAUDE.md`. The impl holds the ST.4 gap-map — ask it if you want the per-component detail.
