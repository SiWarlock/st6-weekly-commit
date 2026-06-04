# Session 018 — Frontend demo-fidelity round (9.15 stateful MSW + ST.8a–c styling fidelity)

> **Orchestrator-authored continuity doc** (NOT a `/session-end` output). The frontend implementer cycled at the ST.8c boundary reading **76% [ACTION]** (above the 75% threshold) — per the lead's pre-arranged cycle plan it **SKIPPED `/session-end`** (confabulation-zone avoidance) and the orchestrator (`st6-main-wc-web-orchestrator`, persisting) captured this continuity. A fresh frontend impl picks up **ST.8d** (the last slice of the round). Date: 2026-06-04. Track: st6-main.

## Slices shipped this session (5, all green)
All render-only / standalone-demo / **no contract change** / REQ-I-008 + surfaceSkin token-native scan green every slice. Final suite: **284 Vitest passing** (69 files). §11 server-authoritative gating held throughout.

| Slice | Commit | What |
|---|---|---|
| **9.15** stateful MSW | `7571222` | Mutable in-memory `db` (deep-clone-from-fixtures seed + `resetDb()`), the E17/E18/E19 dispute mutations writing through (`MockDbError`→RFC-7807), live read-selectors, `emitDisputeAffordances` over live state, §3 review re-derivation, the targeted command-center review-state overlay; the SW-control cold-install boot fix (`swControl.ts`) + the relative-base demo config (`.env.example`). |
| **ST.8a** app-shell | `674021c` | The standalone demo app-shell (`src/standalone/shell/{AppShell,AppBar,PrimaryNav,Breadcrumb,navModel}`): app-bar (brand + Demo pill + week + identity slot) + persona-gated route-driven sub-nav + breadcrumb; PersonaSwitcher restyled into the identity dropdown (preserves `setPersonaId` + §16 reset, reroutes via `navigate('/')`→RootRedirect). Closes doc 024 §A/S1. |
| **ST.8b** command-center fidelity | `8259131` | "At a glance" summary strip (D-1 client-computed `summarizeRows`), labeled hide-zero risk chips + P/U pills (`CC_RISK_CHIP_TAXONOMY`), report avatars, review-timestamp sub-lines, RECONCILE column, static week-pager + functional refresh, section caption/sort + footer legend, Review/Open action, "Alignment Command Center" title. Doc 024 §B. |
| **ST.8b-2** filter rebuild | `1930340` | `CommandCenterFilters` rebuilt into the compact dropdown-chip row (hand-rolled token-native `FilterDropdown`) + active-filter chips + Clear-all + "Showing N of M reports"; Person + DO dropdowns (DO sourced from `useGetRcdoQuery`); in-filter week-of dropped. Doc 024 §B.2. |
| **ST.8c** IC page/card fidelity | `9216050` | PlanLifecycleBar → bordered plan-header card (week+status+counts+review+owner / stepper+actions); **GOLD** Start-reconciliation (`warning` tone) + success Close-week, both still server-gated; commitment-card description line + chips-above-SO-box + non-mono SO; section captions. Doc 024 §C. |

## Decisions made
- **D-1 (lead/user-approved):** the command-center "At a glance" strip is **client-computed** from the loaded rows now (pure `summarizeRows`); the backend §9 `summary` field stays the production follow-up. (LESSONS §24.)
- **9.15 scope:** disputes-mutations-only (E17/E18/E19) write-through + plan reads from the db + a **targeted** command-center overlay (the affected row's `unresolvedDisputeCount`/`reviewStatus` from the db; hand-tuned styling counts stay seeded). Non-dispute mutations + full CC/heatmap rollups → **9.15b** (deferred). (LESSONS §21.)
- **Cold-install boot:** await SW **control** (not just `worker.start()`) via a pure `swControl.ts` helper + timeout backstop; demo uses a relative base URL. (LESSONS §22.)
- **App-shell (§7/REQ-I-008 corollary):** simulate the host chrome standalone-only (tree-shaken); nav drives the EXISTING routes; persona-switch reroutes via `navigate('/')`→`RootRedirect` (server-authoritative role-landing, no role read in the switcher). (LESSONS §23.)
- **Per-surface tone map:** `CC_RISK_CHIP_TAXONOMY` is a distinct named map in `statusTaxonomy.ts` (CANON CC tones diverge from the heatmap's `RISK_TAXONOMY`); §7 "single source" = the file, not one-map-per-enum. (LESSONS §25.)
- **`FilterDropdown`:** hand-rolled token-native dropdown (not Flowbite `Dropdown`, which is unused/unthemed) — the ST.8a PersonaSwitcher precedent.
- **ST.7c reversal (canon-driven):** the plan status pill moves back INTO the plan-header card (the mockup's plan-head top row), superseding ST.7c's in-bar-badge drop — NOT a regression (in the card, not duplicated).
- **Gold/success buttons:** reuse the existing `warning`/`success` tone tokens (no new "gold" token, no hex); `text-white` on the solid tone (the white-on-amber/green contrast is an ST.8d visual check).

## Decisions explicitly NOT made / deferred
- **9.15b** — the non-dispute lifecycle mutations writing through + full command-center/heatmap count rollups + drilldown live-read (deferred; demo-only fast-follow).
- **6-vs-7 filter chips** — the mockup shows 6 filters (omits Alignment status); the app keeps the 7th functional. **Canon-vs-functionality call for the user to adjudicate at ST.8d.**
- **Backend follow-ups (frontend-origin, B.11/§9):** `resolvedDisputeCount` + `unlinkedCount` (→ the resolved/unlinked CC risk chips) + `reviewedAt` (→ the CC reviewed-time sub-line). The §9 `summary` field (D-1 production source) is the backend orch's kept Carry-forward item.
- **Stepper extraction** — `PlanLifecycleBar` + `DisputePanel` stay the 2 consumers (extract a shared `<Stepper>` at the 3rd).

## Open follow-ups — what the FRESH impl picks up
- **ST.8d (the remaining slice) — gstack canon-compare gate + live-disputes-loop QA.** Side-by-side mockup-vs-real per surface (command center · IC plan view · heatmap · drawers · BOTH themes); **the mockup is CANON + doc 024 is NON-EXHAUSTIVE → fix EVERY deviation found.** Plus the live open→respond→resolve disputes loop (now enabled by 9.15's mutable db). **Two adjudication items to surface to the user:** (1) the 6-vs-7 alignment-status filter; (2) the white-on-amber/green button contrast (both themes); plus eyeball that the filtered-view "Showing N of M" count + the "At a glance" strip read sensibly with a filter active. **No upfront `/tdd` brief** — ST.8d is a QA pass whose fix-list emerges from the compare (any fixes are small render-only follow-ups).
- **Rule-of-three watch:** extract a shared token-native dropdown primitive (PersonaSwitcher + `FilterDropdown`) at the 3rd consumer.
- **Env (reuse):** dev server on `localhost:5180` (`VITE_AUTH_MODE=demo`, **NO `VITE_API_BASE_URL`** — relative base); gstack `/connect-chrome` headed browser; serve the canon mockup (`docs/design/cadence-design-system/ui_kits/weekly-commit/index.html`) locally for the side-by-side. The 9.15 cold-install fix should make a fresh browser load cleanly.

## LESSONS banked this session
wc-web **§21** (standalone MSW mutable-db) · **§22** (MSW cold-install SW-control boot) · **§23** (standalone app-shell mirrors host chrome) · **§24** (D-1 client-computed summary) · **§25** (per-surface tone map / §7 clarification). All written to `apps/wc-web/LESSONS.md` + the `CLAUDE.md` index (orchestrator hot-routing; committed at the round seal).

## Round-seal note (orchestrator, NOT yet done)
This was an **impl cycle**, not a round close-out — the orchestrator persists; `/orchestrate-end` runs after **ST.8d** lands. Staged for that round seal (two-way `MVP_TASKS`/`ARCHITECTURE` window handshake with `st6-main-orchestrator`): the ST.8 sub-block task rows (ST.8a–d) + the 9.15 tick + 9.15b; the Carry-forward items above; the deferred Phase-9 sub-checkbox hygiene. Briefs 077–080 (ST.8a/b/c + ST.8b-2) committed at the seal. Backend Phase 6 (6.5a-2/6.5b/6.6/6.7) sealed in parallel (`cce700e`/`827538f`/`ebb4d46` + the backend orch's round commit).
