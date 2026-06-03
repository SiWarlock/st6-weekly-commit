# Frontend Styling Proposal — Cadence Design System Integration

> **Status: PROPOSAL — awaiting user sign-off (via team lead).** Authored by `st6-main-wc-web-orchestrator` (frontend/styling track). **No binding doc is edited until sign-off:** `MVP_TASKS.md` and `ARCHITECTURE.md` currently carry the **backend orchestrator's** uncommitted hot-routing edits (awaiting its `/orchestrate-end`); this proposal is fully self-contained so none of the styling/color/phase changes touch those files yet. They are applied only **after** (a) user sign-off and (b) the backend orch's round-commit clears those files — timing coordinated directly with `st6-main-orchestrator`.
>
> **Inputs reviewed:** `docs/design/cadence-design-system/` (README, SKILL, `colors_and_type.css`, `components.css`, 19 `preview/*.html`, `assets/icons.js`) + the `ui_kits/weekly-commit/` mockup app (`app.jsx`, `WeeklyPlanView.jsx`, `CommandCenter.jsx`, `CommitmentCard.jsx`, `Heatmap.jsx`, `overlays.jsx`, `atoms.jsx`, `data.jsx`, `kit.css`); reconciled against `docs/design/UI_UX_SPEC.md`, `ARCHITECTURE.md` (§7, Appendix A/B/C.4/D.1/F), and `MVP_TASKS.md` Phase 9.

---

## 0. Executive summary (TL;DR for sign-off)

The user delivered **Cadence** — a complete, build-ready design system for WC in **Linear-dark** visual language, plus a high-fidelity interactive UI kit covering all three product surfaces (IC workspace, manager command center, RCDO heatmap). It faithfully builds `UI_UX_SPEC.md`'s layouts/microcopy/taxonomy with **one deliberate override**: the spec's *light* foundation palette is replaced by Linear's *dark* palette, and the brand accent moves `blue-600` → indigo `#5E6AD2`.

Three load-bearing takeaways:

1. **The color override is foundation-and-brand only.** The six-tone *semantic* taxonomy (which tone each enum value maps to) is **preserved 1:1** between the spec and the delivered system. **No `ARCHITECTURE.md` enum/contract cross-doc invariant is affected** (incl. `RiskBadge` — see §3.3).
2. **Dark is the delivered default; the user wants a light-mode toggle.** A light palette **does not exist yet** — it is net-new work. Mechanism decided in §4 (CSS custom properties + `[data-theme]` + persisted preference; toggle is standalone-only chrome, tree-shaken from the remote).
3. **A stack-integration fork exists.** Cadence is authored as **CSS custom properties + hand-rolled `.wc-*` classes**, but the project stack mandate is **Tailwind utilities + Flowbite React only** (`ARCHITECTURE.md §7`; `apps/wc-web/CLAUDE.md` forbidden pattern #3). How we port one into the other needs the user's call (§6, Fork 1).

Phase 9 (as written in `MVP_TASKS.md`) builds the frontend **structure + logic** — RTK Query slices, the MFE boundary, view-state primitives, `allowedActions[]` wiring, cache invalidation — and is **silent on visual styling/theming**. This proposal adds a **Phase ST (Styling & Theming)** to own the token foundation, the dark/light mechanism, the taxonomy skin, and the a11y/design-review pass, and proposes how per-surface visual fidelity folds into the existing Phase 9 component tasks.

---

## 1. Review findings — design system + UI kit → Phase 9 surface mapping

| Cadence artifact | Provides | Maps to Phase 9 / `ARCHITECTURE.md` surface |
|---|---|---|
| `colors_and_type.css` | All tokens — neutral ramp, surfaces, ink, borders, brand, **six status tones**, heatmap volume fill, type scale, spacing, radii, elevation, motion (CSS custom properties) | Token layer for **all** of Phase 9; `StatusBadge`/`RiskBadge` (9.2) |
| `components.css` | Token-driven atoms — `.wc-badge` (+ ring variant), status pills, chess chips, buttons, cards, RCDO breadcrumb, confidence meter, heatmap cell | 9.2 view-state/badge primitives; 9.7–9.12 components |
| `atoms.jsx` | Authoritative **enum→tone+icon maps** (`PLAN_STATE`, `REVIEW`, `OUTCOME`, `RISK`, `WORKTYPE`, `ALIGNMENT`) + `Icon`/`Avatar`/`Badge`/`PriorityTag`/`WorkTypeTag`/`ConfidenceMeter`/`AlignmentChip`/`RcdoBreadcrumb`/`Btn` | `StatusBadge`/`RiskBadge` (9.2); `ChessLayerFields` (9.7); `SupportingOutcomePicker` breadcrumb (9.5) |
| `WeeklyPlanView.jsx` + `CommitmentCard.jsx` + `overlays.jsx` | IC workspace: lifecycle bar, mode-switched commitment cards (draft/locked/reconciling/readOnly), `RcdoPicker`, `LockModal`, sync warning, dispute strip | 9.7 (`WeeklyPlanView`/`PlanLifecycleBar`/`LockButton`/`CommitmentForm`/`CommitmentList`), 9.8 (reconcile/carry-forward) |
| `CommandCenter.jsx` | Manager roll-up: at-a-glance SLA strip, filter chips, dense table, review Drawer, `MarkReviewed`/`Flag` modals | 9.9 (`CommandCenter`/`CommandCenterFilters`/`MarkReviewedAction`) |
| `Heatmap.jsx` | RCDO grid: volume neutral-fill **decoupled** from explicit risk badges, legend, drill-down Drawer | 9.10 (`HeatmapGrid`/`HeatmapCellDrilldown`) |
| `overlays.jsx` | Dispute open/respond/resolve affordances + flat comments | 9.11 (`DisputePanel`/`DisputeRespondForm`/`DisputeResolveAction`/comments) |
| sync chip (in `WeeklyPlanView`/`CommitmentCard`) | `SyncStatusChip` — FAILED = **caution (amber)**, retry affordance, non-blocking | 9.12 (`SyncStatusBadge`/`SyncRetryAction`) |
| `assets/icons.js` | Heroicons-family outline set + canonical per-status glyph assignments | All status/risk rendering (production swaps in `react-icons/hi`) |
| `app.jsx` | Standalone shell: demo top bar + persona switcher + WC sub-nav (**no theme toggle yet**) | 9.3 standalone `main.tsx` / `PersonaSwitcher` (theme toggle slots in here) |
| `preview/*.html` | Design-system specimen cards (type, color, taxonomy, spacing/elevation, components) | Visual-fidelity QA reference for the ST.7 `/design-review` pass |

**Production-stack caveats the UI kit itself flags:** components share scope via `window` (demo pattern, not production); state mutations are local React (no RTK Query / `allowedActions[]` wiring / cache invalidation behind them); icons are hand-authored in the Heroicons *style* (swap in the real `react-icons/hi` for pixel-exact glyphs); Inter loads via Google Fonts CDN (provide licensed `.woff2` for offline/self-host). **The UI kit is a visual + interaction reference, not code to lift** — Phase 9 supplies the real RTK Query / MFE architecture; Phase ST supplies the skin.

---

## 2. Proposed styling-phase shape (Phase ST) + Phase 9 fold-ins

> Area: `apps/wc-web`. **Spec anchors:** `ARCHITECTURE.md §7`; `docs/design/cadence-design-system/` (binding styling SoT); `UI_UX_SPEC.md §4` (taxonomy/density; foundation superseded); REQ-UX-002, REQ-S-005 (a11y / color-not-only-signal), REQ-NF-005 (no CSS bloat on initial render). **No safety invariant** — styling slices may bundle.

| Task | Scope | Folds with |
|---|---|---|
| **ST.1 — Token foundation + Tailwind/Flowbite bridge** | Bring `colors_and_type.css` into `apps/wc-web` as the canonical token layer (global import); bridge tokens into `tailwind.config` theme (semantic color scales `surface`/`ink`/`border`/`brand`/`tone-*`, spacing, radii, fontFamily/fontSize, boxShadow, transitionDuration) **and** a Flowbite custom-theme object so Flowbite-React `Badge`/`Button`/`Table`/`Drawer`/`Modal` inherit the Cadence skin. | 0.6 shell, 9.1/9.2 |
| **ST.2 — Dark/light mechanism + light-palette authoring** | `data-theme` on the WC root; author the net-new `[data-theme="light"]` token block (AA-contrast status tones; indigo retained); `ThemeProvider` + `useThemePreference` (localStorage → `prefers-color-scheme` → dark); **`ThemeToggle` standalone-only, tree-shaken from remote**; remote consumes ambient theme. | 9.3 (standalone/remote split) |
| **ST.3 — Status / risk / chess atom skin (taxonomy SoT)** | Skin `StatusBadge`, `RiskBadge`, `PriorityTag`, `WorkTypeTag`, `ConfidenceMeter` (3-segment bar, not number/stars), `AlignmentChip`, `SyncStatusBadge` per `UI_UX_SPEC §4.2/§4.3` + `atoms.jsx` maps — six tones, **ring variant** for the 2nd of a same-color risk pair (`BLOCKED`, `CARRY_FORWARD`), pinned Heroicons via `react-icons/hi`. No game/chess glyphs. | 9.2, 9.7 |
| **ST.4 — Surface / density / elevation / motion skin** | Cards (flat, hairline border, 8px radius, 16px pad, left-accent only where earned), tables (~44px rows, sticky header, striped/hoverable), drawers/modals (raised surface + shadow + scrim), focus ring (3px indigo, never removed), motion tokens (≤150/200ms, no bounce), solid backgrounds (no gradients/imagery on data surfaces). | 9.7–9.11 |
| **ST.5 — IC workspace visual composition** | `PlanLifecycleBar` 4-node forward-only stepper; `CommitmentCard` modes + left-accent (violet unplanned / red-amber disputed); `RcdoPicker`/`RcdoBreadcrumb`; non-blocking sync warning strip. | **9.7 / 9.8 (fold-in candidate)** |
| **ST.6 — Manager surfaces visual composition** | `CommandCenter` dense table + at-a-glance SLA strip + filter chips + review Drawer; `HeatmapGrid` with **volume neutral-fill decoupled from risk badges** + a11y hatch/dot high-contrast pattern-mode toggle; drilldown Drawer; `DisputePanel` 3-step stepper. | **9.9 / 9.10 / 9.11 (fold-in candidate)** |
| **ST.7 — A11y + responsive + design-review pass** | Color-never-the-only-signal (glyph+text+color, grayscale/colorblind legible) across all status/risk; heatmap high-contrast pattern mode; `focus-visible` rings; desktop-first responsive (`content-max` 1440 / `reading-col` 1040); `prefers-reduced-motion`; run `/design-review` against `preview/*.html` for visual-fidelity QA. | end of Phase 9 |

**Fold-in note:** under the recommended sequencing (Fork 2, Option 2), **ST.5/ST.6 are absorbed into the Phase 9 component task ACs** (each 9.x task gains "renders per Cadence §4.2/§4.3 + tokens") rather than running as separate late slices; **ST.1–ST.4 + ST.7** remain the distinct Phase ST spine. Under Option 1 they run as standalone late slices.

**Proposed Phase 9 AC fold-ins (on sign-off):** extend each Phase 9 component task's acceptance criteria with a styling clause — e.g. *"Renders per Cadence design system — tokens from `colors_and_type.css`, status/risk via the §4.2 six-tone taxonomy, density/elevation/motion per §4.4–4.7; both `data-theme` values resolve."* Add a Phase 9 acceptance bullet: *"Every status/risk conveys meaning via glyph + text + color (never color alone); dark + light themes both render; the theme toggle is absent from the exposed remote build (REQ-I-008-style)."*

---

## 3. Color-taxonomy reconciliation (the override)

### 3.1 What deviates from the spec

| Layer | `UI_UX_SPEC.md` (§4.1/§2) | Cadence delivered | Override? |
|---|---|---|---|
| App / card surfaces | `white` / `gray-50` | near-black `#08090A` app · `#0F1011` panel · `#18191B` raised | **Yes — foundation re-skin (light→dark)** |
| Primary ink | `gray-900` | near-white `#F7F8F8` | **Yes — foundation** |
| Hairlines / borders | `gray-200` | low-alpha white `rgba(255,255,255,0.08–0.13)` | **Yes — foundation** |
| Brand accent | `blue-600` (Flowbite `info`) | Linear indigo `#5E6AD2` (interactive-only) | **Yes — brand hue shift** |
| Info status tone | `info` / blue (shared with brand) | distinct blue `#4EA7FC` (separated from brand) | **Yes — de-collision** |
| Six semantic tones (neutral/info/success/warning/critical/accent) | red=blocking · amber=needs-attention · green=done · blue=in-progress · slate=inert · violet=unplanned | **identical** | **No — preserved 1:1** |
| Every enum→tone mapping (PlanState, ReviewStatus, AlignmentStatus, RiskBadge, ReconciliationOutcome, DisputeStatus, FlagType, SyncStatus, Priority, WorkType) | per spec §4.2/§4.3 | **identical** (verified vs `atoms.jsx` maps) | **No — preserved 1:1** |

**The override is exactly two things:** (a) the **foundation** flips light→dark, and (b) the **brand accent** moves `blue-600` → indigo `#5E6AD2`, with Info-status-blue split to its own hue so *status never rides the brand color*. Everything semantic — the six tones and every status/risk/chess enum's tone+icon — is unchanged.

### 3.2 A spec inconsistency the design system resolves

`UI_UX_SPEC.md` is internally inconsistent on `MISALIGNED`: its §4.2 canonical table and the heatmap design-tool prompt map `MISALIGNED → Critical/red`, but the command-center design-tool prompt text says `purple 1 misaligned`. Cadence resolves to the **§4.2 canonical: `MISALIGNED` = Critical/red**, and reserves **violet (accent) for `UNPLANNED`/additive work only**. `atoms.jsx` confirms: `RISK.MISALIGNED → tone "failure"`, `WORKTYPE.UNPLANNED → tone "accent"`. Correct reading; removes the ambiguity.

### 3.3 Cross-doc invariant impact — **NONE at the contract level**

This is the load-bearing reconciliation answer:

- `ARCHITECTURE.md` pins `RiskBadge` `{MISALIGNED, NEEDS_REVIEW, BLOCKED, CARRY_FORWARD, UNREVIEWED, OVERDUE_REVIEW}`, `AlignmentStatus`, `ReviewStatus`, etc. as **enum vocabularies (wire names)** — Appendix A row + Appendix B.1 enum table + the `:shared` `enums/` package mirror (pinned by `EnumVocabularyTest`). It pins **no colors**.
- `ARCHITECTURE.md §7` (frontend) is **silent on the foundation palette** — it says only "Flowbite React + Tailwind utilities." No `blue-600`, no light palette, no theme is documented there.
- `apps/wc-web/CLAUDE.md` Cross-doc invariants table is **empty** (no model rows yet).

⇒ **The color override touches zero Appendix-A / §-anchored / enum cross-doc invariant.** The `RiskBadge` change is *render-only* (how a badge looks), not a *value* change (no add/rename/remove) — confirmed with `st6-main-orchestrator` (backend): `EnumVocabularyTest` stays green, **no paired backend contract edit needed**. Colors are pure frontend styling, and Cadence is now their source of truth.

### 3.4 Doc surfaces superseded (non-invariant, orchestrator-written on sign-off)

1. **`UI_UX_SPEC.md §4.1` (foundation palette) + the `blue-600`-brand lines in §2/§4.2** are superseded by Cadence. `UI_UX_SPEC.md` is a **design *input* doc under `docs/design/`, not the binding `ARCHITECTURE.md` and not a cross-doc invariant.** Cadence's README already documents the override; propose a one-line "superseded-by Cadence dark palette (`docs/design/cadence-design-system/`)" note atop §4.1.
2. **`ARCHITECTURE.md §7`** — propose adding one clarifying sentence naming `docs/design/cadence-design-system/` as the **binding styling source of truth** (dark default + light toggle). This is an **architecture-doc *note*, not an invariant edit** (no Appendix A row, no enum change) → no atomic enum pairing required. §7 is the frontend orchestrator's territory; staggered with the backend orchestrator's in-flight edits.
3. **`apps/wc-web/CLAUDE.md` forbidden pattern #3** ("no CSS Modules / styled-components — Tailwind + Flowbite only") needs a **clarification** (depending on Fork 1): a single **global design-system token stylesheet (CSS custom properties)** + an optional `@layer components` sheet for bespoke atoms is *permitted* and is neither CSS Modules nor styled-components; those two remain forbidden.

None are safety invariants; none require human sign-off *as invariants*. Folded into the round on the user's go, **after** the backend orch's round-commit clears `MVP_TASKS.md`/`ARCHITECTURE.md`.

---

## 4. Theming approach — dark default + light toggle (mechanism decided)

The delivered design is **dark by default**; the user wants a **light-mode toggle**. Proposed mechanism:

- **Token layer = CSS custom properties.** `colors_and_type.css` already defines every surface/ink/border/tone as a `var(--…)` under `:root` (dark). This is the seam: a theme is just a different set of values for the same variable names.
- **`[data-theme]` switch.** `data-theme="dark"` (default) and `data-theme="light"` blocks on the WC root element redefine the **foundation** vars (surfaces, ink, borders) and the **contrast-adjusted status tones**. **Author the light block as net-new work** — it does not exist today.
  - The brand indigo `#5E6AD2` is **retained across both themes** (do *not* revert light mode to `blue-600` — keeps brand identity theme-stable; see Fork 3).
  - The spec's overridden light palette (`white`/`gray-50`/`gray-900`/`gray-200`) is the natural **base** for the light token set, re-derived for AA contrast on the six status tones.
- **Preference resolution + persistence.** A `ThemeProvider` + `useThemePreference()` hook: initial = persisted `localStorage` value → else `prefers-color-scheme` → else dark. Persists on toggle. Respects `prefers-reduced-motion` (already in tokens — drop shimmer/spin).
- **Remote-vs-standalone boundary (mirrors REQ-I-008).** The **toggle control is standalone-only chrome** — it lives in `src/standalone/` next to `PersonaSwitcher` and is **tree-shaken out of the exposed remote build**. In remote mode the PA host owns global chrome, so WC **consumes** an ambient/host-provided `data-theme` (default dark) and renders **no global theme toggle** inside the remote. Keeps the remote free of host-owned concerns, exactly as the persona switcher is.

---

## 5. Load-bearing decisions for the user (orchestrator recommends; does not decide)

### Fork 1 — How to port the CSS-var design system into the Tailwind/Flowbite stack *(load-bearing — shapes the dev-facing styling API)*

| Option | Approach | Trade-off |
|---|---|---|
| **A** | **Tailwind/Flowbite-native:** translate tokens → `tailwind.config` theme + Flowbite custom theme; rebuild all atoms as Flowbite-React themed via utilities; drop the `.wc-*` CSS. | Most stack-native; **most porting effort**; highest risk of drift from the reference; slowest for a 1-week box. |
| **C (RECOMMENDED)** | **Hybrid token-bridge:** CSS custom properties (`colors_and_type.css`) are the canonical token layer (global import) + bridged into `tailwind.config` theme **and** a Flowbite custom theme; components use Flowbite-React primitives + Tailwind utilities that reference the vars; port the `.wc-*` component CSS as a thin `@layer components` sheet only for bespoke atoms Flowbite can't express (ConfidenceMeter bars, heatmap cells, RCDO breadcrumb). Dark/light = flip vars under `[data-theme]`. | Preserves the design system as **single source of truth**; honors the Tailwind/Flowbite mandate; makes the toggle a one-attribute flip; **needs the forbidden-pattern clarification (§3.4.3)**. Moderate effort. |
| **B** | **Ship Cadence CSS as-is:** import `colors_and_type.css` + `components.css` globally; components emit `.wc-*` classes directly. | Fastest, pixel-faithful to the reference; but **bypasses Tailwind/Flowbite theming** and leans hardest on the forbidden-pattern clarification; least aligned with `ARCHITECTURE.md §7`. |

**Recommendation: C** — keeps Cadence canonical, honors the stack, trivial theming. Requires amending `apps/wc-web/CLAUDE.md` forbidden pattern #3 to permit a global token stylesheet + a bespoke-atom `@layer components` sheet.

### Fork 2 — Sequencing: when does styling happen relative to Phase 9? *(load-bearing — shapes the build order)*

| Option | Approach | Trade-off |
|---|---|---|
| **2 (RECOMMENDED)** | **Foundation-early + style-as-you-build:** land ST.1+ST.2 (tokens + theming) as a foundation slice right after the 0.6 shell / 9.1–9.2; **fold per-surface fidelity (ST.5/ST.6) into each Phase 9 component task's ACs**; finish with the ST.3/ST.4 cross-cutting skin where not already absorbed + the ST.7 a11y/design-review tail. Phase ST exists as a distinct tracker section owning ST.1–ST.4 + ST.7. | Most efficient for the 1-week box; design system is ready so building styled costs little extra; **avoids unstyled→restyled rework**. Slightly more interleaving per slice. |
| **1** | **Dedicated late STYLING phase:** build all of Phase 9 unstyled, then run Phase ST end-to-end to skin it. | Cleanest separation of logic vs visual; **double-touches every component**; rework risk; styling compressed at the end of the timebox. |

**Recommendation: 2** — gives the user the distinct STYLING phase they asked for (ST.1–ST.4 + ST.7 spine) while folding per-surface fidelity into Phase 9 to avoid rework.

### Fork 3 — Light-mode brand color

Recommend **retaining indigo `#5E6AD2` as the brand accent in *both* themes** (light mode keeps indigo, does not revert to the spec's `blue-600`) so brand identity is theme-stable and Info-blue stays distinct from the brand in both themes. Light mode re-derives only the foundation surfaces/ink/borders + AA-contrast status tones. *(Lower-stakes; flag only — recommend yes.)*

---

## 6. Sequence on sign-off

1. User signs off (via lead) on Fork 1 / Fork 2 / Fork 3 + the Phase ST shape + the §3.4 doc actions.
2. **Wait for the backend orch's `/orchestrate-end` round-commit to clear `MVP_TASKS.md` + `ARCHITECTURE.md`** (its uncommitted hot-edits land first). Coordinate timing with `st6-main-orchestrator`; ping before staging either shared file.
3. Frontend orchestrator makes the **non-invasive doc edits** in clearly-labeled `**Frontend:**` sub-blocks: add **Phase ST** to `MVP_TASKS.md`; fold styling ACs into Phase 9; add the single `ARCHITECTURE.md §7` styling-SoT note (frontend territory); add the `UI_UX_SPEC.md §4.1` superseded note; clarify `apps/wc-web/CLAUDE.md` forbidden pattern #3.
4. Lead relays the go + spawns the **frontend implementer** (git worktree).
5. Orchestrator authors the first `/tdd` brief — **ST.1 token foundation + Tailwind/Flowbite bridge** (or per the chosen sequencing) — into `docs/briefs/NNN-ST-1-token-foundation.md`.

---

## 7. Sign-off checklist (what the user is confirming)

- [ ] **Color reconciliation accepted** — override is foundation+brand only; semantic taxonomy preserved 1:1; **no enum cross-doc invariant affected** (`RiskBadge` render-only, `EnumVocabularyTest` unaffected); `MISALIGNED`=red / `UNPLANNED`=violet resolution accepted.
- [ ] **Theming approach accepted** — dark default + light toggle via CSS vars + `[data-theme]` + persisted preference; toggle standalone-only/tree-shaken; light palette authored net-new.
- [ ] **Fork 1** — token-port approach: **A / B / C(rec)**.
- [ ] **Fork 2** — sequencing: **1 / 2(rec)**.
- [ ] **Fork 3** — light-mode brand stays indigo: **yes(rec) / no**.
- [ ] **Phase ST shape accepted** (ST.1–ST.7) + Phase 9 AC fold-ins.
- [ ] **Doc edits authorized** (§3.4 + §6) — none are safety invariants; applied after the backend orch's round-commit clears the shared files.
</content>
