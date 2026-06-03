# Frontend track — resume handoff (for the fresh `st6-main-wc-web-*` successor)

> **Status:** frontend track **PAUSED 2026-06-02**, resuming via a fresh frontend orchestrator + implementer pair (the prior pair cycled at impl ~70%). This note is the durable handoff — read it alongside `docs/sessions/002-2026-06-02-frontend-styling-foundation-and-phase9-spine.md` and `git show 9f17c3c` (the deferred-items list is in that commit message). Backend track (`st6-main-orchestrator` + `st6-main-wc-api-implementer`) runs untouched in parallel.

## What landed (commits)
- `2a307b8` — Cadence design system + UI kit (binding styling SoT, `docs/design/cadence-design-system/`).
- `e3c1cb7` — **ST.1/ST.2** Cadence-themed shell (approach-A token bridge + Flowbite custom theme) + dark-default/light-toggle `[data-theme]` mechanism.
- `4efd1e6` — **9.1** RTK Query `baseApi` (9 tags) + `prepareHeaders` auth XOR + injectable accessor seam + store + RFC-7807 parser.
- `1e4f5eb` — **9.3** MFE boundary (`expose ./WeeklyCommitApp`) + standalone shell + demo/persona wiring; **REQ-I-008 proven** (static graph + literal scan + auth0 build-grep, positive-control, gate PASS).
- `1479ea6` — **9.2/ST.3** view-state primitives + StatusBadge/RiskBadge via `statusTaxonomy.ts` + Pagination + WeekRangeLabel.
- `64b02b0` (round-1 doc) + `9f17c3c` (round-2 doc) + `cceaed7` (impl session doc 002).
- Phase 9 spine = **0.6 (partial) + ST.1/ST.2 + 9.1 + 9.3 + 9.2**. Frontend green at pause (preflight: 41 Vitest tests).

## ⚠ DEFERRED shared-doc edits — apply on resume (NOT yet written)
Held to avoid colliding with the backend orch's in-flight 2.x edits to the shared files. **Resume sequence:**
1. **Wait** for `st6-main-orchestrator`'s 2.x `/orchestrate-end` round-commit to land (on `9f17c3c`) and clear `MVP_TASKS.md` + `ARCHITECTURE.md`.
2. **Re-read HEAD**, then **ping `st6-main-orchestrator` before staging** (staggered commits, per the standing protocol), then apply — **explicit `git add` of your own paths only; backend sections untouched**:
   - `MVP_TASKS.md`: tick **Phase ST 9.1/9.2/9.3 done**; update the **Frontend** currently-in-progress sub-block; add a **Log** entry (frontend round 2); triage **Carry-forward** future-TODOs (below).
   - `ARCHITECTURE.md §7/D.1`: **`resolveApiBaseUrl`** required-env enforcement note + **single-React-instance is a host-contract** note (not assumed from `@originjs` v1.4.1's unimplemented `singleton`).
3. **Brief numbering starts at 015** (the backend reserved 013/014 — its `013-1.6`, `014-2.1`). You keep `011-9.3` + `012-9.2`.

## Next slice + the required 9.4 pre-step
- **Next slice = 9.5 (me/rcdo)** — `meApi` (→ `isManager`) + `rcdoApi` + `RcdoBrowser` + `SupportingOutcomePicker`. Builds on 9.1 baseApi + 9.2 view-states (both landed).
- **9.4 (lazy route tree) carries a REQUIRED pre-step (lead-signed-off security decision):** before wiring the store/route-tree into `WeeklyCommitApp` (which makes `baseApi` remote-reachable), **SPLIT the demo-header attach out of `baseApi.prepareHeaders` into a standalone-only injected seam** (same pattern as the accessor seam) so `baseApi` source carries no `X-Demo-Employee-Id` literal — keeps the fast fail-closed REQ-I-008 static guard viable. Without it, the static guard false-positives once baseApi enters the remote graph. (See LESSONS §6.)

## Open follow-ups (also in session doc 002)
- **Phase 11:** promote the REQ-I-008 auth0 build-grep to a **CI-enforced fail-closed guard** (backstop to the fail-open static walker).
- **Phase 10/11:** the demo persona ids (`demo-employee-*` in `DemoIdentityProvider`/`demoIdentity.ts`) must **align with the V5 seed employee UUIDs** — the demo breaks otherwise.
- **9.13:** singleton-fallback readiness (today only the prop path is wired/tested — make readiness reactive or document "seam populated before mount", OQ-004) + the single-React-instance host contract.
- **ST.7:** formal AA-contrast verification of the net-new light-mode status tones.
- **Minor:** `formatWeek` doesn't normalize a non-Monday `weekStart` in explicit/compact paths (relies on the documented server B.20 Monday precondition).

## Locked decisions (don't re-litigate)
- **Styling (user sign-off 2026-06-02):** Fork 1 = **A** (Tailwind/Flowbite-native; tokens → `tailwind.config` + Flowbite custom theme; no bespoke `.wc-*` CSS). Fork 2 = **Option 2** (foundation-early, style-as-you-build; per-surface fidelity folds into 9.x ACs; ST.3/ST.4 + ST.7 are the Phase ST spine). Fork 3 = **indigo `#5E6AD2` brand in both themes**.
- **Color override is render-only** — foundation (light→dark) + brand (`blue-600`→indigo); six-tone taxonomy + every enum→tone mapping preserved 1:1; **no enum/Appendix-A cross-doc invariant** (`RiskBadge`/`AlignmentStatus`/`ReviewStatus` vocabularies untouched; backend `EnumVocabularyTest` unaffected).
- **Custom token-driven `Badge` atom** (vs Flowbite Badge) for StatusBadge/RiskBadge — design-faithful to Cadence's own `.wc-badge`, still approach-A.
- **forbidden-pattern #3 narrow exception:** one CSS custom-property **token** stylesheet (`src/styles/theme.css`) is permitted; `.wc-*` component CSS / CSS Modules / styled-components remain forbidden.

## Lessons banked (wc-web `LESSONS.md`)
§3 multi-theme CSS-vars + `[data-theme]` flip · §4 flowbite-react 0.10.2 theming · §5 RTK Query base testing · §6 REQ-I-008 boundary proof · §7 statusTaxonomy single source of visual truth.

## Shared-tree protocol (with `st6-main-orchestrator`)
Section ownership: frontend owns `§7`, Phase ST + Phase 9 (frontend tasks), `apps/wc-web/*`, the color taxonomy; backend owns the rest (`§1–6/§8–17`, Appendix A/B.1/C–F, `apps/wc-api/*`). Labeled `**Frontend:**`/`**Backend:**` sub-blocks in the shared top-of-file sections. **Explicit `git add <own paths>`, never `-A`. Ping before staging `MVP_TASKS.md`/`ARCHITECTURE.md`.** `RiskBadge` enum *values* are a backend invariant (render is frontend) — a value add/rename/remove is a paired backend edit, ping first.
</content>
