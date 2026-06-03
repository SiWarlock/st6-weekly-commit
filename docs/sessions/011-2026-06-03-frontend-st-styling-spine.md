# Session 011 — Frontend: Phase-ST styling spine (ST.4 → ST.6b) + Phase-9 closeout

- **Date:** 2026-06-03
- **Track:** `st6-main` · area `apps/wc-web/` (frontend implementer)
- **Phase:** ST (Cadence styling spine) — with the Phase-9 functional remainder (9.11b/9.13) closed at the session's start
- **Predecessor:** [007 — frontend Phase-9 reconciliation/manager/sync](007-2026-06-03-frontend-phase9-reconciliation-manager-sync.md)
- **Successor:** _(next fresh frontend impl — ST.6c via brief 048)_
- **Cycle reason:** impl reached 72% WARN at the clean ST.6b boundary; lead-approved cycle.

## Why this session existed

A fresh frontend implementer (successor to the 007 pair) to (a) finish the unblocked Phase-9 functional surface (flat comments 9.11b + the host-integration contract doc 9.13), and then (b) run the **Cadence styling spine** — Phase ST — slice-by-slice: ST.3 chess atoms → ST.4 surface/density/elevation → ST.5a/b IC plan-view composition → ST.6a/b manager surfaces. All ST work is **render-only, Tailwind/token-native (no `.wc-*` CSS), cross-doc invariant NONE**.

## What was built (this session, 8 slices / 8 feature commits)

**Detailed scope — the ST styling spine (ST.4–ST.6b):**

| Slice | Commit | Summary |
|---|---|---|
| ST.4 | `62b4b92` | Surface/density/elevation skin — token `maxWidth` scale (`reading-col`/`content-max`) + `boxShadow.hairline` wired into the theme; 3 surfaces swapped off Tailwind defaults; `CommitmentList` card hairline + earned unplanned left-accent; `CommandCenter` sticky thead + hover rows |
| ST.5a | `e49e46a` | IC plan-view composition — `PlanLifecycleBar` 4-node forward-only stepper (derived display); lock glyph on locked titles; UNPLANNED kind-badge↔WorkTypeTag redundancy fix |
| ST.5b | `7e247d4` | Card display completion — read-only `RcdoBreadcrumb` (DO › SO) + missing-SO warning; `RECONCILIATION_OUTCOME_TAXONOMY` + `OutcomePill`; `outcomeNote`; RECONCILED card muting |
| ST.6a | `41f1052` | Heatmap volume-fill — neutral `bg-vol-*` load intensity keyed on `commitmentCount` (Cadence `volMeta` breakpoints), decoupled from risk badges |
| ST.6b | `86a2173` | Command-center dense table — zebra rows + accessible tone count-pills (glyph+count+`title`); `CommandCenterFilters` removable filter chips + Clear-all |

**Earlier this session (Phase-9 remainder + ST.3; separately round-closed by the prior orchestrator at `1bd8050`):**

| Slice | Commit | Summary |
|---|---|---|
| 9.11b | `3cf2a16` | Flat plan/commitment comments — `commentsApi` (E20/E21) + `CommentList`/`CommentForm`/`CommentThread` (lazy/collapsible, COMMENT-gated); `dtos.ts` +`CommentDto`/`CommentTargetType`/`CreateCommentRequest` (B.9/B.1) |
| 9.13 | `1fef7a5` | PA host-integration contract — `apps/wc-web/README.md` "Host integration" + `.env.example` + `host-integration.test.ts` anti-drift guard |
| ST.3 | `cf609a5` | Chess-layer atom skins — `PriorityTag`/`WorkTypeTag`/`ConfidenceMeter`/`AlignmentChip` + the WORKTYPE/ALIGNMENT/PRIORITY/CONFIDENCE taxonomies in `statusTaxonomy.ts` |

### Files created (ST.4–ST.6b scope)
- `src/app/theme/surfaceSkin.test.ts` (ST.4) — config-token assertions + the structural guard (`no .wc-*`/hex/arbitrary-`[…]`); `TOUCHED_SURFACES` grows each ST slice (now 7 surfaces).
- `src/features/commitment/RcdoBreadcrumb.tsx` (+test, ST.5b) — read-only DO › SO chip + missing-SO warning variant.
- `src/features/commitment/OutcomePill.tsx` (+test, ST.5b) — `Badge`-wrapping reconciliation-outcome pill.

### Files modified (ST.4–ST.6b scope)
- `tailwind.config.ts` (ST.4) — `+maxWidth.{reading-col,content-max}`, `+boxShadow.hairline` (var-backed).
- `src/styles/theme.css` (ST.4) — `+--shadow-hairline` composite (token layer; the blessed forbidden-#3 exception).
- `src/features/plan/WeeklyPlanView.tsx` (ST.4) — `max-w-reading-col`.
- `src/features/plan/PlanLifecycleBar.tsx` (+test, ST.5a) — `stepperNodes()` helper + 4-node stepper.
- `src/features/manager/CommandCenter.tsx` (+test, ST.4/6b) — `max-w-content-max` + sticky thead + hover; zebra rows + tone count-pills (`countMeta()` reusing `RISK_TAXONOMY`).
- `src/features/manager/CommandCenterFilters.tsx` (+test, ST.6b) — active-filter chips + Clear-all.
- `src/features/manager/HeatmapGrid.tsx` (+test, ST.4/6a) — `max-w-content-max`; `cellVolume()` + `bg-vol-*` + `data-volume`.
- `src/features/commitment/CommitmentList.tsx` (+test, ST.4/5a/5b) — `shadow-hairline` + `cardAccent()`; lock glyph; WorkTypeTag suppression; `RcdoBreadcrumb` + `OutcomePill` + `outcomeNote` + RECONCILED `data-readonly` muting.
- `src/shared/lib/statusTaxonomy.ts` (ST.5b) — `+RECONCILIATION_OUTCOME_TAXONOMY` (+ `HiAdjustments`/`HiXCircle` icons).

## Decisions made (with rationale)

- **`boxShadow.hairline` = var-backed (Option B), not the brief's inline composite** — the `tailwind.config` header rule is "tokens are NEVER hardcoded here"; the existing 4 shadow tokens all reference `var(--shadow-*)` with the composite in `theme.css`. Kept the config composite-free; orch-blessed. (ST.4)
- **Stepper is derived display, non-interactive** — `stepperNodes(plan.state)` projects the §3 lifecycle (done/active/pending); actions stay on the server-gated buttons. StatusBadge kept alongside (consolidation → ST.7). (ST.5a)
- **UNPLANNED redundancy → suppress `WorkTypeTag` when `workType==='UNPLANNED'`** (keep the accent kind-badge). (ST.5a)
- **Breadcrumb = 2-level DO › SO** (TWEAK from the brief's RC › DO › SO) — the binding Cadence `atoms.jsx:142-149` renders DO › SO; the org-wide Rally Cry is constant noise. Emphasize the SO **title**, never the UUID. (ST.5b)
- **`RECONCILIATION_OUTCOME_TAXONOMY` in `statusTaxonomy.ts`** (§7 single source), icons cross-checked against Cadence `OUTCOME`. RECONCILED card muting via `planState`; OutcomePill independent of muting. (ST.5b)
- **Heatmap volume thresholds pinned to Cadence `volMeta()`** (0→none/transparent, 1→light, 2-3→normal, ≥4→heavy); `data-volume` hook; decoupled from risk badges. (ST.6a)
- **Count-pill tones reuse `RISK_TAXONOMY` verbatim via `countMeta()`** (NOT re-mapped inline — §7). **CARRY_FORWARD→warning** (the real map value; the brief's "info" was a slip). **Dispute→failure** (`HiFlag`; accent=UNPLANNED-violet in this app, would conflate). Avoided a non-null assertion via a kind→risk-key lookup + typed dispute fallback. Zebra parity is **index-based** (CSS `even:`/`odd:` would miscount the interleaved review-expand detail row). weekStart is never a filter chip. (ST.6b)

## Decisions explicitly NOT made (deferred)

- **Disputed → failure left-accent** (`cardAccent` has the one-line drop-in point) — waits on the backend B.6 Option-A edit (nests `dispute?`), the same dep that blocks **9.11a**.
- **StatusBadge ↔ stepper consolidation** → ST.7 (decide rendered, via `/design-review`).
- **Heatmap heavy-cell border / count-0 dashed gap-border / volume bars / a11y high-contrast pattern-mode** → ST.7.
- **CARRIED_FORWARD successor-week note** → carry-forward (no outgoing data path; `carryForwardSourceCommitmentId` is the incoming link).
- **At-a-glance SLA strip** (backend `summary` dep) + **DisputePanel** (disputes/9.11a-blocked) — lead-confirmed defers; not in ST.6.

## TDD compliance

**Clean — no violations.** Every slice ran strict `/tdd`: RED tests written first → Step-2.5 design sent to + approved by the orchestrator → confirmed RED for the right reason → minimum GREEN → full suite → reachability → gates. Post-RED test edits were test-quality only (a setup-ordering fix on a 9.11b loading test; existing-assertion disambiguations for new ripples — kind-badge, status-badge scoping) — never implementation-before-test. The structural `no-.wc-*`/hex/arbitrary-hatch guard (`surfaceSkin.test.ts`) extended to each touched surface.

## Cross-doc invariant audit

**NONE this session (ST.4–ST.6b).** All five ST slices are render-only — no `dtos.ts` contract-model field add/remove/rename. The `statusTaxonomy.ts` taxonomy maps (WORKTYPE/ALIGNMENT/PRIORITY/CONFIDENCE/RECONCILIATION_OUTCOME) are **visual maps, not Appendix-A/B contract**. (9.11b's earlier `dtos.ts` mirrors — `CommentDto`/`CommentTargetType`/`CreateCommentRequest`, B.9/B.1 verbatim — were cross-doc-table-routed by the prior orchestrator at the round-5 close `1bd8050`; no Appendix-B content change.)

## Reachability (Step 7.5)

All five ST features are reachable from live production routes (not test-only), pinned by render tests:
- ST.4 surfaces + ST.5a stepper/lock-glyph + ST.5b breadcrumb/OutcomePill/muting → `/weekly-commit` (`WeeklyCommitApp → AppRoutes → WeeklyCommitPage → WeeklyPlanView → PlanLifecycleBar`/`CommitmentList`).
- ST.6a volume-fill → `/manager/heatmap` (`HeatmapPage → HeatmapGrid`).
- ST.6b dense table + chips → `/manager/command-center` (`CommandCenterPage → CommandCenter`/`CommandCenterFilters`).
- **No tested-but-unwired features.** (Note: a RECONCILED *current* plan is viewable at `/weekly-commit` — `getCurrentPlan` has no state filter — which is what made the ST.5b OutcomePill/muting reachable; past-week RECONCILED browsing still awaits the `PlanHistoryPage` real view.)

## Open follow-ups (Step-9 categorized — for the orchestrator's `/orchestrate-end` to verify; I did NOT write MVP_TASKS/LESSONS)

- **Cross-doc invariant change:** NONE (all ST slices render-only).
- **Future TODO — next-brief working set (Carry-forward):**
  - **ST.6c** (manager Drawers — review + drilldown) next, via **brief 048**.
  - **ST.7** — a11y high-contrast pattern-mode end-to-end + `prefers-reduced-motion`/`focus-visible` pass + the gstack `/design-review` + real-browser QA against `docs/design/cadence-design-system/preview/*.html`; also: heatmap heavy-border/count-0 gap-border/volume bars, StatusBadge↔stepper consolidation, exact 44px row-height fidelity.
  - **Disputed→failure card accent** rides with **9.11a / the backend B.6 Option-A edit** (`cardAccent` drop-in point ready).
  - **SLA strip** (backend `summary` dep) + **DisputePanel** (disputes-blocked) — lead-confirmed defers.
- **Convention candidates (orchestrator's call at round close):**
  - The §7 lighter taxonomy shapes (`ToneLabel`/`ConfidenceEntry`) beside `TaxonomyEntry` (ST.3) — possible §7 note.
  - `exactOptionalPropertyTypes` "optional-source → `| undefined` prop" (ST.5b `RcdoBreadcrumb`) — possible §12 refinement.
  - "lifecycle → forward-only derived stepper" (ST.5a); "quantitative load → neutral volume-fill decoupled from categorical risk" (ST.6a); "count → accessible tone pill (glyph+count+`title`)" + "active-filter chip + clear-all" (ST.6b) — §-notes if ST.6c/ST.7 reuse them (rule of three).
- **Architecture doc note:** none.

## How to use what was built

The Cadence visual language is now applied across the IC workspace + manager heatmap/command-center surfaces, **token-native** — a `[data-theme]` flip re-skins everything via `var()`; the `surfaceSkin.test.ts` structural guard fails the build if any touched surface introduces `.wc-*` CSS, a hardcoded hex, or an arbitrary `[…]` hatch. The `statusTaxonomy.ts` maps remain the single source of visual truth for every status/risk/outcome/chess display.
