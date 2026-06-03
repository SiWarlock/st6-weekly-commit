# Cadence — ST6 Weekly Commit Module · Design System

A design system for the **ST6 Weekly Commit (WC)** module: a strategy-enforced
weekly-alignment tool that replaces the weekly Check-in / Priorities /
Objectives-linking slice of **15Five**. WC forces a structural connection
between an individual's weekly commitments and the organization's strategy
hierarchy (**RCDO** — Rally Cry › Defining Objective › Supporting Outcome), then
walks each week through a strict lifecycle: **DRAFT → LOCKED → RECONCILING →
RECONCILED**, with carry-forward into the next week.

> **Aesthetic:** Linear.app's dark, calm, high-density surface language applied
> to an enterprise execution-SaaS data tool. Near-black layered surfaces,
> near-white ink, indigo (`#5E6AD2`) as the single interactive brand accent, and
> a disciplined six-tone status palette where **color carries meaning, never
> decoration**.

---

## Product context

Two surfaces live inside one Module-Federation **remote** (it renders *only* its
own content + sub-nav; the PA host app owns global chrome):

- **My Weekly Commit (IC surface)** — an individual contributor drafts weekly
  commitments, links each to exactly one **Supporting Outcome**, locks the plan
  to freeze a baseline, then reconciles actual outcomes and carries unfinished
  work forward.
- **My Team (manager surface)** — an **Alignment Command Center** (direct-report
  roll-up of plan / review / reconciliation status with review-SLA + dispute
  signals) and an **RCDO Coverage Heatmap** (report × Defining-Objective grid
  with explicit risk badges and cell drill-down).

The product's reason to exist is a **forcing function**: every planned
commitment must link to a Supporting Outcome before a plan can lock, locked
baselines are immutable, and every unplanned commitment must link before
reconciliation closes. The UI's job is to make these gates feel like **calm
guardrails, not friction**.

### The five routes
`/` (persona redirect) · `/weekly-commit` (stateful IC editor/reconciler) ·
`/weekly-commit/history/:planId` (read-only past plan) ·
`/manager/command-center` (manager default) · `/manager/heatmap`.

### Personas / seed cast (used throughout the UI kit)
Manager **Dana Okafor** (also an IC; manages 6 reports) + her direct reports:
**Priya Raman** (locked, awaiting review), **Marco Bellini** (locked, review
OVERDUE + failed Outlook sync), **Aisha Khan** (locked, open MISALIGNED
dispute), **Tomas Novak** (locked, resolved dispute loop), **Grace Liu**
(reconciling + carry-forward + blocked), **Sam Carter** (draft, one unlinked
commitment — the lock-blocker).

### Sources & provenance
- **`uploads/UI_UX_SPEC.md`** — the binding UI/UX design contract (IA, routes,
  component inventory, page layouts, journeys, states/microcopy, a11y). Read it
  for ground truth on behavior and DTO field names. Copied references to its
  sections (§) appear throughout this system.
- **`assets/reference-inspiration.webp`** — the original dark-dashboard reference
  the brand direction was anchored to (Linear-like density + calm).
- No production codebase or Figma file was provided. UI-kit screens are
  faithful builds **from the spec's exact layouts, microcopy, and seed data** —
  not invented flows. The spec's stack is locked to Flowbite-on-Tailwind +
  Heroicons + Inter; this system honors those families.

> **Palette note (intentional deviation from the spec):** the spec specifies a
> *light* enterprise palette (`white` / `gray-50` surfaces, `gray-900` ink,
> `blue-600` accent). Per direction, this system **overrides that with Linear's
> dark palette** while preserving the spec's structure: the six-tone semantic
> taxonomy, density, component inventory, iconography, and microcopy are
> unchanged — only the foundation surfaces/ink/accent are re-skinned dark, and
> the brand accent moves from `blue-600` to Linear indigo `#5E6AD2`.

---

## CONTENT FUNDAMENTALS

The voice is **calm, plain, accountable enterprise** — it explains *why* a
control is disabled and *what* to do next, never scolds, never celebrates.

- **Person & address:** second person to the actor ("**you** can't edit… after
  locking"), third person for others ("**Sam** hasn't locked this plan yet").
  Managers read about reports by **first name**.
- **Casing:** **Sentence case everywhere** — buttons ("Lock plan", "Start
  reconciliation", "Close week"), headers ("Alignment Command Center"), labels.
  The only UPPERCASE is the small `text-xs tracking-wide` field/column labels
  (e.g. `REPORT`, `PLAN STATE`, `RISK CHIPS`) and mono enum chips (`P0`,
  `LOCKED`). Status words render as written in the taxonomy ("Reviewed",
  "Reconciling", "Overdue").
- **Tone — guardrails, not gates:** disabled controls always carry a tooltip
  with the unmet precondition. Examples (verbatim from spec):
  - "1 of 2 commitments isn't linked to a Supporting Outcome."
  - "Add at least one commitment to lock."
  - "Locking freezes your planned commitments as the baseline for
    reconciliation. You can't edit, add, or remove planned commitments after
    locking — this can't be undone."
- **Errors = the server's `safeMessage`, verbatim.** Never invent error prose,
  never surface a `failureCode` or token. e.g. "Every planned commitment must
  link to a Supporting Outcome before you can lock this plan." Forbidden/404 is
  deliberately ambiguous (IDOR-safe): "You don't have access to this, or it no
  longer exists."
- **Reassurance over blank space:** absence is phrased as a calm line, not an
  empty card — "No alignment flags on this plan."; "— no risk"; "Calendar sync
  failed; you can retry. This does not affect your locked plan."
- **Strategy framing:** copy ladders work up to strategy — "Each one links to a
  Supporting Outcome so your work ladders up to the Rally Cry."
- **Numbers & time:** counts are terse and mono (`3 planned · 0 unplanned`,
  `3P 1U`). Dates are explicit org-tz (America/Chicago): "Week of Jun 1–7,
  2026", "Locked Mon Jun 1, 9:14 AM CT", "Due Jun 2, 5:00 PM".
- **No gamification, ever** (load-bearing rule): the prioritization metadata
  (`priority`, `workType`, `confidence`, `alignmentStatus`) is **strategic
  metadata, not a game.** No chess/knight/pawn imagery, no trophies, stars,
  points, streaks, or celebratory motifs. The internal term "chess layer" must
  **never** appear in the UI.
- **No emoji in product UI.** (A single 🗓 appears only in the spec's
  not-started onboarding line; treat sparingly. Default is icon-led, not
  emoji-led.)

---

## VISUAL FOUNDATIONS

**Direction:** Linear's dark calm × observability-grade data discipline. The
page reads as *structure*, not chrome.

- **Surfaces & depth.** Near-black app background (`#08090A`); cards/tables on a
  marginally lighter panel (`#0F1011`); floating layers (drawer, modal,
  dropdown, toast) on `#18191B`. Depth comes from **layered surface lightness +
  hairline borders**, not heavy shadow. Cards are flat: a 1px low-alpha white
  border (`rgba(255,255,255,0.08)`) and an optional faint inset top highlight —
  no drop shadow at rest. Real shadow is reserved for things that float.
- **Color = meaning.** Six tones, shared across every status family:
  **neutral/slate** (inert), **info/blue** `#4EA7FC` (in-progress/system),
  **success/green** `#4CB782` (done/healthy), **caution/amber** `#F2C94C`
  (needs-attention), **critical/red** `#EB5757` (blocking/overdue),
  **accent/violet** `#A77CFF` (unplanned/additive). Badges are translucent fill
  + bright fg + low-alpha colored border. The indigo brand (`#5E6AD2`) is
  **interactive-only** and never carries status — which is why Info-blue is a
  separate hue.
- **Typography.** Inter (UI + body), mono stack for IDs/counts/`P0`. Dense,
  tight leading, slightly negative tracking on headings (Linear's signature).
  `text-sm` (14px) is the workhorse; page titles `text-2xl/600`; small
  `text-xs` uppercase tracked labels. Numerics are tabular-aligned.
- **Spacing & density.** 8px soft grid, **compact-first** (4px chip gaps, 8px
  inline, 12px stacks, 16px card padding, 24px sections/gutters). Table rows
  ~44px; heatmap cells ~88px min. No airy marketing spacing.
- **Corner radii.** 6px badges/inputs, 8px cards/buttons, 12px modals, full
  pills/avatars/dots. Restrained, never pill-everything.
- **Cards.** Flat panel surface, 1px hairline border, 8px radius, 16px padding,
  no rest shadow. Meaning is added via a **left-accent border** only where
  semantically earned (violet on unplanned, red/amber on disputed).
- **Borders & dividers.** Low-alpha white hairlines (`0.08`) for rules/cell
  grid; `0.13` for stronger separation; table header gets a slightly heavier
  bottom rule and sticks on scroll.
- **Backgrounds.** Solid near-black. **No gradients, no brand-color washes, no
  imagery, no textures, no patterns** on data surfaces — restraint is the brand.
  (The only optional pattern is the heatmap's high-contrast hatch/dot a11y mode.)
- **Heatmap encoding.** Volume (commitment count) = neutral **slate-ish
  white-alpha fill** intensity (load, never danger); risk = explicit named
  badges. The two are deliberately decoupled — never a single "health score"
  color.
- **Hover / press / focus.** Hover lifts to a subtle white-alpha fill
  (`rgba(255,255,255,0.04–0.06)`) or +1 surface step; rows/cells get a hairline
  brighten. Press = a barely-darker fill, **no scale/bounce**. Focus = a 3px
  indigo ring (`rgba(94,106,210,0.55)`) — visible, never removed.
- **Motion.** Minimal and functional only — communicate async + reduce layout
  jank. `≤150ms` ease-out color/opacity on badges/hover/tabs; drawer/modal
  slide/fade `≤200ms`; skeleton `animate-pulse` on first load (~150ms delay
  before a spinner); toasts auto-dismiss 4s. **No bounce, no spring, no
  celebratory animation.** Respect `prefers-reduced-motion` (drop shimmer/spin).
- **Transparency & blur.** Used sparingly: translucent status-badge fills, and a
  subtle backdrop scrim behind modals/drawers. No frosted-glass everywhere.
- **Imagery vibe.** None by default — this is a data tool. Avatars are
  initials-on-tinted-disc, not photos. No illustration, no stock photography.

---

## ICONOGRAPHY

- **Family:** Heroicons (the spec locks `react-icons/hi`). Rendered **outline**,
  24×24, **1.75px stroke**, `currentColor`, round caps/joins — tuned thin to
  match Linear's line weight. 16px inline (`h-4 w-4`), 20px in headers.
- **Source file:** `assets/icons.js` — a single map of the icons this product
  uses, keyed by their Heroicons identifier, with a `wcIcon(name, opts)` helper
  for plain HTML and an `<Icon>` wrapper in the UI kit. **Canonical assignments
  are pinned** (do not improvise a different glyph for a status):
  - Lifecycle: `HiPencilAlt` Draft · `HiLockClosed` Locked · `HiClipboardCheck`
    Reconciling · `HiCheckCircle` Reconciled · `HiMinusCircle` Not started.
  - Review: `HiOutlineEye` Not reviewed · `HiExclamationCircle` Disputes ·
    `HiCheckCircle` Reviewed · `HiClock` Overdue.
  - Risk: `HiExclamation` Misaligned · `HiBan` Blocked · `HiClock` Overdue
    review · `HiQuestionMarkCircle` Needs review · `HiArrowNarrowRight`
    Carry-forward · `HiOutlineEye` Unreviewed.
  - Work type: `HiSparkles` Strategic · `HiCog` Maintenance · `HiBan` Blocker ·
    `HiPlusCircle` Unplanned.
  - Sync: `HiCalendar` Synced · `HiExclamationCircle` Failed · `HiRefresh`
    Retry. Reserved utility: `HiChevronRight/Left/Down`, `HiSelector`,
    `HiFilter`, `HiChat`, `HiInformationCircle`, `HiSearch`, `HiExternalLink`,
    `HiPlus`, `HiTrash`, `HiDotsHorizontal`.
- **Confidence is NOT an icon** — it's a 3-segment signal bar
  (`▮▮▮ / ▮▮▯ / ▮▯▯`), never stars or a number.
- **No game/chess glyphs, no emoji as icons, no unicode-char icons** in product
  UI. (Lightweight unicode glyphs like `●○◔↻` appear only in this doc's ASCII
  layout sketches, not the rendered product.)

> **Substitution flag:** icons are hand-authored in the Heroicons *style* rather
> than imported from the `react-icons/hi` package, so a few glyphs are close
> approximations of the exact Heroicons paths. If pixel-exact Heroicons are
> required, swap `assets/icons.js` for the real package in production. Fonts
> (Inter) load via Google Fonts CDN — no local font files are bundled; provide
> licensed `.woff2` files if offline/self-hosted delivery is needed.

---

## Index — what's in this system

| Path | What |
|---|---|
| `README.md` | This file — context, content + visual foundations, iconography, index. |
| `SKILL.md` | Agent-Skills front-matter so this folder works as a downloadable skill. |
| `colors_and_type.css` | All design tokens — palette, type scale, spacing, radii, elevation, motion (CSS custom properties + `.ds-*` helpers). |
| `components.css` | Token-driven component atoms (badges, status pills, chess-layer chips, buttons, cards, RCDO breadcrumb) reused by preview cards + the UI kit. |
| `assets/icons.js` | Heroicons-family outline icon set + `wcIcon()` helper. |
| `assets/reference-inspiration.webp` | Original dark-dashboard brand reference. |
| `preview/*.html` | Design-system specimen cards (type, color, status taxonomy, chess-layer atoms, spacing/elevation, components) shown in the Design System tab. |
| `ui_kits/weekly-commit/` | The UI kit — interactive, high-fidelity recreations of the IC workspace, manager command center, and RCDO heatmap. See its own `README.md`. |
| `uploads/UI_UX_SPEC.md` | The binding spec (provided input). |

**UI kits:** `ui_kits/weekly-commit/` (the only product surface).
