## ST6 Weekly Commit Module — UI/UX Design Specification

> **Audience:** Claude design (AI mockup-generation tool). **Purpose:** a complete, build-ready UI/UX specification for the ST6 Weekly Commit Module (WC). This document is the single design contract — one consistent voice, one status/color taxonomy, real DTO fields, real microcopy, and real seeded example content. Generate mockups for **standalone demo mode** (what the assessor runs), keeping the WC-owned region visually self-contained.
>
> **UI kit (locked):** Flowbite React on Tailwind CSS utilities. No CSS Modules, no styled-components. **Data model (locked):** RTK Query refetch + cache invalidation, no optimistic UI, no websockets. **Routes (the only 5):** `/`, `/weekly-commit`, `/weekly-commit/history/:planId`, `/manager/command-center`, `/manager/heatmap`.

---

## 1. Overview & Design Goals

### What we are designing

WC is a **strategy-enforced weekly-alignment tool** — the replacement for the weekly Check-in / Priorities / Objectives-linking slice of 15Five, not the full suite. It has two surfaces inside one Module Federation remote:

- **The IC workspace ("My Weekly Commit")** — where an individual contributor drafts weekly commitments, links each to exactly one **Supporting Outcome** in the read-only RCDO hierarchy (Rally Cry › Defining Objective › Supporting Outcome), locks the plan to freeze a baseline, then reconciles actual outcomes and carries unfinished work forward.
- **The manager surfaces ("My Team")** — an **Alignment Command Center** (direct-report roll-up of plan/review/reconciliation status, with review-SLA and dispute signals) and an **RCDO Coverage Heatmap** (report × Defining-Objective grid with explicit risk badges and cell drill-down).

### The strategy-enforcement thesis

The product's reason to exist is the **forcing function**: every *planned* commitment must link to a Supporting Outcome before the plan can lock, locked baselines are immutable, and every unplanned commitment must link before reconciliation closes. The UI's job is to make these gates feel like calm guardrails, not friction — surfacing exactly what is missing, exactly where, and never letting a user reach a dead end. We borrow three patterns from the product WC replaces: 15Five's "can't submit without a status on every owned goal" forcing function (mapped to lock-gating on RCDO links and close-gating on outcomes), its priority→objective linking that surfaces an alignment breadcrumb, and its "close the prior period before opening the new one" carry-forward cycle.

### Success criteria

1. **Scannable manager visibility** — a manager resolves "who on my team is at risk this week?" in under 5 seconds from the command-center table + at-a-glance strip, drilling into a plan or heatmap cell only where a badge or OVERDUE signal demands it. This is the "see drift without opening every plan" promise.
2. **Low-friction IC entry** — an IC drafts, links, and locks a week without confusion about *why* a control is disabled or *what* blocks the lock; the strategy linkage is the path of least resistance.
3. **Trustworthy state legibility** — every data view shows explicit loading / empty / error / partial / success states; every action is gated by the server's `allowedActions[]`; errors render the API `safeMessage` verbatim. No silent failures, no speculative UI.

---

## 2. Aesthetic Direction

**Enterprise B2B execution-SaaS — clean, dense, data-forward, calm, trustworthy, professional.** Think Linear's density and calm, Vercel's restraint, the data-grid discipline of a modern observability or OKR dashboard — not a marketing site, not a consumer app.

| Principle | Application |
|---|---|
| **Calm neutral base** | White / `gray-50` surfaces, `gray-900` ink, `gray-200` hairlines. The page reads as structure, not chrome. |
| **Color = meaning only** | Color is reserved exclusively for status/risk semantics (the §4 taxonomy). No decorative gradients, no brand-color washes on data. The brand accent (`blue-600`) is reserved for *interactive* affordances (primary buttons, active tab, focus ring, links) — status never rides on the brand color. |
| **Density-first** | 8px soft grid but compact-first rhythm (4/8/12px), `text-sm` workhorse body, compact table rows (~44px), tight card padding. No airy marketing spacing. |
| **Flat & trustworthy** | Cards are flat (`border border-gray-200`, no heavy shadow). Sticky table headers, sticky sub-nav. |

### The no-gamification rule (load-bearing)

The **"chess layer"** — `priority` (P0/P1/P2), `workType` (Strategic/Maintenance/Blocker/Unplanned), `confidence` (High/Medium/Low), `alignmentStatus` (Aligned/Needs-Review/Misaligned) — is **strategic prioritization metadata, not a game.** Render it as quiet enterprise metadata chips and count pills. **Never** use chess imagery, knight/pawn icons, rank ornaments, trophies, stars, points, streaks, or any celebratory/playful motif. Confidence is a 3-segment signal bar (not stars, not a number). Priority is a severity-toned square chip (not a medal). The word "chess layer" is internal vocabulary only and must never appear in the UI.

---

## 3. Information Architecture & Navigation

### 3.1 Sitemap — the only 5 routes, grouped into 2 surfaces

WC has exactly five lazy-loaded routes, organizing into two top-level surfaces. Surface visibility is gated by `MeDto.isManager`: ICs see only **My Weekly Commit**; managers see both and toggle between them.

```
WC module (mounted under host base path, e.g. /apps/wc/*)
│
├─ /                                  ← persona-aware default redirect (renders no chrome;
│                                        resolves MeDto then navigates — see §3.4)
│
├─ SURFACE A · "My Weekly Commit"  (IC surface — every user has this)
│   ├─ /weekly-commit                 ← current-week plan (GET /api/plans/current → WeeklyPlanDto)
│   │                                    DRAFT→LOCKED→RECONCILING→RECONCILED in one stateful view
│   └─ /weekly-commit/history/:planId ← a past plan, READ-ONLY (GET /api/plans/{id})
│                                        allowedActions[] suppressed; outcomes shown static
│
└─ SURFACE B · "My Team"  (manager surface — only when MeDto.isManager === true)
    ├─ /manager/command-center        ← direct-report roll-up (GET /api/manager/command-center)
    └─ /manager/heatmap               ← RCDO coverage grid (GET /api/manager/heatmap)
```

| Route | Surface | Primary DTO | Who | Notes |
|---|---|---|---|---|
| `/` | — | `MeDto` | IC + Mgr | Redirect-only; no chrome |
| `/weekly-commit` | My Weekly Commit | `WeeklyPlanDto` | IC + Mgr (as IC) | Stateful editor/reconciler; the lifecycle "home" |
| `/weekly-commit/history/:planId` | My Weekly Commit | `WeeklyPlanDto` | IC + Mgr (as IC) | Read-only; `allowedActions[]` near-empty |
| `/manager/command-center` | My Team | `ManagerCommandCenterRowDto[]` | Manager only | Default landing for managers |
| `/manager/heatmap` | My Team | `HeatmapCellDto[]` + drilldown | Manager only | ICs never reach this |

**Guard rule:** the two `/manager/*` routes are wrapped in a `<RequireManager>` boundary reading `MeDto.isManager`. A non-manager deep-linking `/manager/heatmap` is redirected to `/weekly-commit` (the server already enforces `403/404`; the client guard is UX-only).

### 3.2 Embedded-remote vs. standalone-demo shell model

WC is a Module Federation **remote**. Chrome ownership differs by mode; demo chrome is **compiled out of the remote build** (tree-shaken via the `src/standalone/` boundary).

**What the HOST (PA shell) owns in production — WC renders NONE of this:** global top app bar / product switcher / org logo; global left navigation rail; global user menu / notifications / search / settings; the `BrowserRouter` (WC consumes the host's router context); the `getAccessToken()` accessor (RTK Query `prepareHeaders` calls it for the bearer token).

**What WC owns in BOTH modes:** its content area (the routed view); its **intra-module sub-navigation** (the surface/sub-surface switcher, §3.3); a WC-local breadcrumb / page header scoped to WC routes; all view states and toasts within its content region.

**What STANDALONE (demo) adds — `src/standalone/` only, excluded from the remote bundle:** a thin local top bar ("ST6 Weekly Commit — Demo" + week label); the **persona switcher** (§10); `BrowserRouter`, the Redux store `Provider`, and the `DemoIdentityProvider` that satisfies `getAccessToken()`.

```
┌─────────────────────────── PRODUCTION (embedded remote) ───────────────────────────┐
│  ███ PA HOST CHROME — top nav · product switcher · user menu  (WC renders NONE) ███ │
│  ███ PA LEFT RAIL ███ ┌──────────── WC REMOTE MOUNT (content area only) ──────────┐ │
│                       │ ╭ WC sub-nav (segmented) ───────────────────────────────╮ │ │
│                       │ │  [ My Weekly Commit ]  [ My Team ▾ ]   Week of Jun 1–7 │ │ │
│                       │ ╰────────────────────────────────────────────────────────╯ │ │
│                       │ Breadcrumb: My Team / Command Center                       │ │
│                       │ ┌──────────────── routed content ─────────────────────────┐│ │
│                       │ │  (command center table / plan editor / heatmap …)        ││ │
│                       │ └──────────────────────────────────────────────────────────┘│ │
│                       └────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────── STANDALONE (demo mode) ─────────────────────────────────┐
│  ST6 Weekly Commit — Demo     Viewing as: [ Dana Okafor · Manager ▾ ]  Week Jun 1–7 │ ← demo chrome
│  (this thin bar + persona switcher live in src/standalone/ ONLY — tree-shaken out)  │
│ ╭ WC sub-nav (segmented) ──────────────────────────────────────────────────────────╮ │
│ │  [ My Weekly Commit ]  [ My Team ▾ ]                          Week of Jun 1–7 ▾   │ │
│ ╰────────────────────────────────────────────────────────────────────────────────────╯ │
│  Breadcrumb: My Weekly Commit / Week of Jun 1–7                                     │
│ ┌─────────────────── routed content (identical component as remote) ───────────────┐ │
│ │  (plan editor / command center / heatmap …)                                       │ │
│ └─────────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

**Design-tool instruction:** draw every mockup for **standalone demo mode**, but keep the WC-owned region visually self-contained so a reviewer can see that removing the thin demo top bar + persona switcher yields the exact production remote. Do **not** draw a PA left rail or PA top nav as part of WC — at most render PA chrome as a greyed, out-of-scope frame to communicate the boundary.

### 3.3 Intra-module sub-navigation (WC-local)

A **horizontal bar at the top of the WC content region** (not a left sidebar — the host owns the left rail in production). Three registers:

1. **Surface switcher** — a Flowbite segmented control / `Tabs` toggling `My Weekly Commit ↔ My Team`. Rendered only when `MeDto.isManager === true`; for a pure IC it collapses to a single static label ("My Weekly Commit").
2. **Sub-surface switcher for My Team** — when "My Team" is active, a secondary segmented control exposes `Command Center | Heatmap`. When "My Weekly Commit" is active there is no sub-tab (history is reached contextually inside the plan view).
3. **Week context selector** — a right-aligned `Dropdown` showing the active Monday–Sunday range via the shared `WeekRangeLabel` ("Week of Jun 1–7, 2026"). On manager surfaces it drives the `weekStart` query param; on the IC surface it switches into `/weekly-commit/history/:planId` for past weeks.

```
WC SUB-NAV (manager, on My Team / Command Center)
╭─────────────────────────────────────────────────────────────────────────────────────╮
│  ▌ My Weekly Commit ▐  ▌▌ My Team ▐▐    Command Center · Heatmap     Week of Jun 1–7 ▾ │
╰─────────────────────────────────────────────────────────────────────────────────────╯

WC SUB-NAV (pure IC — no toggle, no team)
╭─────────────────────────────────────────────────────────────────────────────────────╮
│  My Weekly Commit                                                  Week of Jun 1–7 ▾  │
╰─────────────────────────────────────────────────────────────────────────────────────╯
```

The active segment uses a filled/elevated Flowbite pill; inactive segments are ghost/text. The surface switcher persists across route changes (derived from the path prefix). The sub-nav is **sticky within WC's scroll container** (not a global fixed header).

### 3.4 Persona / role model & default landing

Authorization is **relationship-driven**, so the UI keys surface visibility off `MeDto.isManager` (true iff the actor has ≥1 active direct report), not `role` alone.

- **Pure IC** (R1–R6: Priya, Marco, Aisha, Tomas, Grace, Sam): sees only My Weekly Commit. No surface toggle, no `/manager/*` access, never the heatmap or command center.
- **Manager-who-is-also-IC** (Dana Okafor): owns her own weekly plan *and* manages 6 reports. She gets the full surface switcher. Because no one manages Dana, her own plan view shows `managerReview === null` UX (no review/dispute affordances on her own plan).

`/` renders no chrome; it resolves `GET /api/me` and redirects:

```
GET /api/me → MeDto
   ├─ isManager === false  → redirect /weekly-commit        (IC: their only surface)
   └─ isManager === true   → redirect /manager/command-center
                              (manager default = the at-a-glance team roll-up)
```

While `MeDto` loads, `/` shows the shared `LoadingState` (centered `Spinner`) — never a flash of empty surface. On `MeDto` error, render `ErrorState` with the `safeMessage` + Retry (no silent redirect to a broken surface).

### 3.5 Global layout grid & density

| Property | Value |
|---|---|
| Content max-width | **Manager surfaces:** full-bleed within the WC region, capped ~1440px with side gutters. **IC plan editor:** ~960–1040px reading column. |
| Column model | Single content column; no WC-owned sidebar. Plan view = stacked `Card` list; command center = one wide `Table`; heatmap = CSS-grid matrix. |
| Vertical rhythm | Dense: ~8px base unit, compact table row height, tight card padding. |
| Typography | Functional sans (Inter / Tailwind default). Table data ~14px, labels ~12–13px, section headers ~16–18px. Numeric counts tabular/mono-aligned. |
| Sticky regions | WC sub-nav sticky within WC scroll container; table header rows sticky. |

---

## 4. Design System

This is the binding visual contract. Every color, label, icon, and chip maps to a literal enum value or a derived display state. All status/risk/chess rendering flows through the shared atoms (§5) so this taxonomy is the **single source of visual truth**.

### 4.1 Foundation palette

> **⚠ SUPERSEDED (2026-06-02) by the Cadence design system** (`docs/design/cadence-design-system/` — the binding styling source of truth; see `ARCHITECTURE.md §7` + `docs/planning/frontend-styling-proposal.md`). The user overrode this *light* foundation palette with Linear's **dark** palette (near-black surfaces, near-white ink) and moved the brand accent `blue-600` → indigo `#5E6AD2` (interactive-only; Info-status-blue split to its own hue). Dark is the default + a persisted light-mode toggle. **The §4.2 six-tone semantic taxonomy + every enum→tone mapping below are UNCHANGED** (preserved 1:1) — only the foundation surfaces/ink/brand are re-skinned.

| Role | Tailwind ramp | Use |
|---|---|---|
| Surface | `white` / `gray-50` | page + card backgrounds |
| Surface raised | `white` + `gray-200` border | cards, table, drawer |
| Ink primary | `gray-900` | titles, primary data |
| Ink secondary | `gray-600` | labels, metadata, breadcrumbs |
| Ink muted | `gray-400` | placeholders, disabled, empty-state |
| Brand accent | `blue-600` (Flowbite `info`) | primary actions, active nav, links, focus ring |
| Divider/border | `gray-200` | hairlines, table rules |

### 4.2 Status / Color Taxonomy (one coherent semantic palette)

Six tones map to Flowbite `Badge`/`Alert` colors. **Tone is shared across every enum family** — red always means broken/blocking-risk, amber always means needs-attention, green always means good/done. Icons are `react-icons/hi` (Heroicons).

| Tone | Flowbite color | Hue | Meaning |
|---|---|---|---|
| Neutral | `gray` / `light` | slate | inert, not-started, informational |
| Info | `info` | blue | in-progress, active, system state |
| Positive | `success` | green | done, healthy, satisfied |
| Caution | `warning` | amber | needs attention, soft risk |
| Critical | `failure` | red | blocking risk, misalignment, overdue |
| Accent | `purple` | violet | unplanned / additive (distinct, non-judgmental) |

**Plan lifecycle — `PlanState`:**

| Value | Label | Tone / color | Icon (`hi`) |
|---|---|---|---|
| `DRAFT` | Draft | Neutral `gray` | `HiPencilAlt` |
| `LOCKED` | Locked | Info `info` | `HiLockClosed` |
| `RECONCILING` | Reconciling | Caution `warning` | `HiClipboardCheck` |
| `RECONCILED` | Reconciled | Positive `success` | `HiCheckCircle` |

(Manager view also shows the not-started shell — `planState=DRAFT` with `plannedCount=0` — labeled **"Not started"**, Neutral, `HiMinusCircle`.)

**Manager review — `ReviewStatus` + derived `OVERDUE`:**

| Value | Label | Tone / color | Icon |
|---|---|---|---|
| `NOT_REVIEWED` | Not reviewed | Neutral `gray` | `HiOutlineEye` |
| `REVIEWED_WITH_DISPUTES` | Reviewed · disputes open | Caution `warning` | `HiExclamationCircle` |
| `REVIEWED` | Reviewed | Positive `success` | `HiCheckCircle` |
| `OVERDUE` *(derived: `isReviewOverdue`)* | Overdue | Critical `failure` | `HiClock` |

`OVERDUE` is a **derived overlay**, never a stored fourth status. `isReviewOverdue = (now > reviewDueAt) AND reviewStatus = NOT_REVIEWED`. **`REVIEWED_WITH_DISPUTES` satisfies the SLA** (`isReviewOverdue=false`) — its amber means "I looked and flagged something," visually distinct from gray "I haven't looked." The two must never be confused. (See §6.3 for the full SLA visual model.)

**Alignment — `AlignmentStatus`:** `ALIGNED` Positive `success` `HiCheck` · `NEEDS_REVIEW` Caution `warning` `HiQuestionMarkCircle` · `MISALIGNED` Critical `failure` `HiExclamation`. Read-only post-lock (IC self-assessment freezes at lock; manager concerns flow through disputes).

**Heatmap risk badges — `RiskBadge` (the only six):**

| Value | Label | Tone / color | Icon |
|---|---|---|---|
| `MISALIGNED` | Misaligned | Critical `failure` (solid) | `HiExclamation` |
| `BLOCKED` | Blocked | Critical `failure` (outline/ring) | `HiBan` |
| `OVERDUE_REVIEW` | Overdue review | Critical `failure` | `HiClock` |
| `NEEDS_REVIEW` | Needs review | Caution `warning` (solid) | `HiQuestionMarkCircle` |
| `CARRY_FORWARD` | Carry-forward | Caution `warning` (outline/ring) | `HiArrowNarrowRight` |
| `UNREVIEWED` | Unreviewed | Neutral `gray` | `HiOutlineEye` |

To keep the two reds (`MISALIGNED`/`BLOCKED`) and two ambers (`NEEDS_REVIEW`/`CARRY_FORWARD`) distinguishable in a dense grid, the second of each pair uses an **outline/ring** badge variant plus its distinct icon. `RiskBadge` is always icon-led, `size="xs"`, and clusters in a wrap row.

**Reconciliation outcomes — `ReconciliationOutcome`:** `COMPLETED` Positive `success` `HiCheckCircle` · `PARTIALLY_COMPLETED` Caution `warning` `HiAdjustments` · `BLOCKED` Critical `failure` `HiBan` · `CANCELED` Neutral `gray` `HiXCircle` · `CARRIED_FORWARD` Info `info` `HiArrowNarrowRight`. **Single-outcome rule** is visually enforced: these are a single-select group — never two badges on one record.

**Dispute — `DisputeStatus` + `FlagType`:** `OPEN` Critical `failure` `HiFlag` · `IC_RESPONDED` Caution `warning` `HiReply` · `RESOLVED` Positive `success` `HiCheckCircle` · `FlagType=NEEDS_REVISION` Caution `warning` `HiPencilAlt` · `FlagType=MISALIGNED` Critical `failure` `HiExclamation`.

**Outlook sync — `SyncStatus`:** `PENDING_PUBLISH`/`QUEUED` Neutral `gray` `HiOutlineClock` · `SYNCING` Info `info` (`Spinner size="xs"`) · `SYNCED` Positive `success` `HiCalendar` · `FAILED` **Caution `warning`** `HiExclamationCircle` · `RETRY_REQUESTED` Info `info` `HiRefresh`. **Sync failure is caution, never critical** — it must read as a non-blocking warning, not a workflow stop. `FAILED` is the only status that surfaces the inline `Retry` action (gated on `RETRY_SYNC`), rendering `safeMessage` verbatim.

### 4.3 Chess-layer visual encoding (compact, scannable, NOT gamified)

Three independent dimensions, each a fixed-width compact indicator so a card row stays aligned and a manager can scan a column. **No game imagery.**

**Priority — `PriorityTag`:** solid square chip, mono label, fixed ~28px. `P0` Critical `failure` · `P1` Caution `warning` · `P2` Neutral `gray`. Severity by tone, not size.

**Work type — `WorkTypeTag`:** outline pill, icon + short label. `STRATEGIC` Info `HiSparkles` · `MAINTENANCE` Neutral `HiCog` · `BLOCKER` Critical `HiBan` · `UNPLANNED` Accent `purple` `HiPlusCircle` (violet so additive post-lock work is distinguishable without implying "bad").

**Confidence — `ConfidenceMeter`:** a 3-segment signal bar (NOT a number, NOT stars). Tooltip reads the literal label.

```
HIGH    ▮▮▮   (success green, 3/3 filled)
MEDIUM  ▮▮▯   (warning amber, 2/3 filled)
LOW     ▮▯▯   (gray, 1/3 filled)
```

**Alignment — `AlignmentChip`:** dot + label per §4.2; read-only post-lock.

### 4.4 Typography scale

System font stack (`Inter`, `ui-sans-serif`). Data-dense, tight leading. IDs / counts / `P0` chips use `font-mono`.

| Token | Size / weight | Use |
|---|---|---|
| Display | `text-2xl` / `font-semibold` | route/page title ("Command Center") |
| H2 | `text-xl` / `font-semibold` | section header, plan title |
| H3 | `text-base` / `font-semibold` | card title, commitment title |
| Body | `text-sm` / `leading-normal` | primary content, table cells, notes |
| Label | `text-xs` / `font-medium uppercase tracking-wide` `text-gray-500` | field labels, column heads |
| Meta | `text-xs` / `text-gray-500` | timestamps, week range, breadcrumbs |
| Mono | `text-xs font-mono` | IDs, counts, `P0/P1/P2` |

### 4.5 Spacing & density

8px soft grid, **compact-first**. Density unit `4px` (chip gaps); inline gap `8px` (`gap-2`); stack gap `12px` (`space-y-3`, between cards/form rows); card padding `16px` (`p-4`); section gap `24px` (`space-y-6`); page gutter `24px` (`px-6 py-4`); table row `~44px` (`py-2.5`); heatmap cell `~88px` min square. `Table` uses `striped` + `hoverable` + sticky header; the heatmap has no zebra (the grid is the structure).

### 4.6 Iconography

Single family: `react-icons/hi` (Heroicons). 16px inline (`h-4 w-4`), 20px in headers. Canonical assignments are pinned in §4.2 (no improvising). Reserved utility icons: `HiChevronRight` (breadcrumb/drawer), `HiFilter`, `HiChat` (comments), `HiInformationCircle` (tooltips), `HiRefresh` (refetch/retry), `HiCalendar` (week/Outlook), `HiExternalLink` (Outlook deep link). **Never** chess/game glyphs.

### 4.7 Motion (minimal)

No optimistic UI, no websockets → motion's only jobs are communicating async and reducing layout jank. Loading: `Spinner` for actions, `animate-pulse` skeleton for first loads (~150ms delay before showing a spinner). Transitions: `transition-colors`/`opacity` ≤150ms ease-out on badges/hover/tab. Drawer/Modal: Flowbite default slide/fade ≤200ms. Toasts: auto-dismiss 4s, manual close always available. **No bounce, no spring, no celebratory animation.** Respect `prefers-reduced-motion` (drop shimmer/spin to static).

---

## 5. Component Inventory

Every component is a thin composition over a named Flowbite React primitive + Tailwind utilities. All data-bound components accept the relevant DTO (or a field subset) so mockups bind to real shapes.

### 5.1 Atoms

| Component | Flowbite primitive | Purpose / props | States / variants |
|---|---|---|---|
| **`StatusPill`** | `Badge` (+`Tooltip` when `iconOnly`) | universal status from §4.2. `kind: 'plan'\|'review'\|'sync'\|'dispute'\|'outcome'`, `value`, `derivedOverdue?`, `size?`, `iconOnly?` | one per enum value; `derivedOverdue` overrides a `NOT_REVIEWED` pill to the critical Overdue pill |
| **`RiskBadge`** | `Badge size="xs"` + `Tooltip` | one of the six `RiskBadge` values. `badge`, `count?` | six values; solid vs. outline/ring per §4.2; renders nothing when no risk |
| **`PriorityTag`** | `Badge` (square + mono theme) | `priority` | P0 red / P1 amber / P2 gray |
| **`WorkTypeTag`** | `Badge` (outline) + icon | `workType` | Strategic/Maintenance/Blocker/Unplanned |
| **`ConfidenceMeter`** | plain Tailwind `div` segments + `Tooltip` | `confidence` (deliberately NOT `Progress`) | HIGH 3/3 / MEDIUM 2/3 / LOW 1/3 |
| **`AlignmentChip`** | `Badge` (dot leading) | `status`, `readOnly?` | Aligned / Needs-review / Misaligned |
| **`SyncStatusChip`** | `Badge` + `Spinner` + `Tooltip` | `record: OutlookSyncRecordDto` (reads `status`, `safeMessage`, `graphEventId`, `allowedActions`) | queued / syncing(Spinner) / synced(deep-link) / failed(warning+Retry) / retrying |
| **`WeekRangeLabel`** | `span` + `HiCalendar` | Mon–Sun org-tz (America/Chicago). `weekStartDate`, `weekEndDate?`, `relative?` | explicit "Jun 1–7, 2026" / relative / compact |

### 5.2 Molecules

| Component | Flowbite | Purpose | Key states |
|---|---|---|---|
| **`CommitmentCard`** | `Card` + nested atoms + `Button`/`Dropdown` | display one `WeeklyCommitmentDto`: title, description, chess chips, `RcdoBreadcrumb`, outcome, dispute marker, carry-forward link. `mode: draft\|locked\|reconciling\|managerReview\|readOnly`. Actions gated by `commitment.allowedActions[]` | draft(editable) / locked(immutable, 🔒) / reconciling(outcome control) / managerReview(`OPEN_DISPUTE` + `managerAlignmentNote` editor) / readOnly. Unplanned = violet left-accent; disputed = red/amber left-accent |
| **`RcdoPicker`** | `Modal`/`Drawer` cascade tree + `TextInput` search → `Breadcrumb` | select exactly one Supporting Outcome from `RcdoTreeDto` (RC→DO→SO, single-select leaf, searchable). `value?`, `onChange`, `tree`, `required`, `error?` | empty(error at lock) / selected(breadcrumb preview) / loading / error |
| **`RcdoBreadcrumb`** | `Breadcrumb` + `HiChevronRight` | read-only RC › DO › SO from `RcdoBreadcrumbDto` | full / truncated(Tooltip) / missing(amber "No Supporting Outcome linked") |
| **`ReconciliationOutcomeControl`** | `Button.Group`/`Radio` + `Textarea` | single-select `reconciliationOutcome` + `outcomeNote`; `CARRIED_FORWARD` excludes completion outcomes | unset(required-at-close hint) / set / disabled |
| **`CommentThread`** | `Card` list + `Avatar` + `Textarea` + `Pagination` | flat (depth 0) comments on `PLAN`/`COMMITMENT`. `canComment` from `COMMENT` in `allowedActions`. React-escaped bodies, no `dangerouslySetInnerHTML` | loading / empty / paginated / posting / error |
| **`DisputePanel`** | `Drawer`/`Card` + `Select` + `Textarea` + `RcdoPicker` | full `OPEN→IC_RESPONDED→RESOLVED` lifecycle with role-correct affordances gated by `dispute.allowedActions[]`. IC never sees Resolve | open / ic-responded / resolved / submitting / error |
| **`FilterBar`** | `Datepicker` + `Dropdown`/`Select` + removable `Badge` chips | E13 query params (week + 7 filters). Person filter from the 6 reports; `reviewState` includes derived OVERDUE | default / active(chips + clear-all) / loading |
| **`Pagination`** | `Pagination` | Spring `Page<T>` envelope (`number`/`size`/`totalElements`/`totalPages`) | hidden when `totalPages ≤ 1` |

### 5.3 Organisms

- **`WeeklyPlanView`** (`/weekly-commit` + history) — full lifecycle of one `WeeklyPlanDto`. Renders `PlanLifecycleBar` (4-node forward-only stepper), `WeekRangeLabel`, plan-level primary action from `allowedActions[]`, counts, `ManagerReviewBanner` (when `managerReview` present), list of `CommitmentCard`, `SyncStatusChip` row, plan-level `CommentThread`. (See §6.1.)
- **`CommandCenterTable`** (`/manager/command-center`) — manager roll-up; one row per `ManagerCommandCenterRowDto`; dense `Table` (`striped hoverable`, sticky header) + card-grid fallback. (See §6.3.)
- **`HeatmapGrid`** (`/manager/heatmap`) — report-rows × Defining-Objective-columns CSS-grid of cells; each cell = volume fill + `RiskBadge[]`. (See §6.4.)
- **`HeatmapDrilldownDrawer`** — SO breakdown for one cell; right `Drawer`, paginated `CommitmentCard` list. (See §6.4.)

### 5.4 Shared view-state primitives (the view-state contract, §8)

First-class components, not ad-hoc markup. No screen rolls its own loading/error treatment.

- **`LoadingState`** — skeleton shimmer (`variant: 'table'|'cards'|'grid'|'inline'`) or centered `Spinner`. ~150ms delay.
- **`EmptyState`** — icon + headline + one-line guidance + optional CTA.
- **`ErrorState`** — `Alert color="failure"` rendering the RFC-7807 `safeMessage` verbatim + `traceId` (small mono) + optional `Retry` (refetch). `fieldErrors[]` route to inline form markers.
- **`PartialState`** — success view **plus** a dismissible `Alert color="warning"` with `safeMessage` + `Retry` (canonical case: plan loaded, Outlook sync `FAILED`). Never blocks the workflow.
- Plus `Toast` for transient post-mutation confirmations ("Plan locked," "Dispute resolved").

### 5.5 Cross-cutting wiring rules

1. **Every action button is conditionally rendered from the DTO's `allowedActions[]`** — if the action is absent, the control is absent (not just disabled), *except* where a disabled+tooltip better explains a blocked transition (e.g. `LOCK` is offered disabled with a tooltip when planned commitments are unlinked). Action → endpoint is fixed by the architecture's action→endpoint map.
2. **Managers are also ICs:** the sub-nav is a two-item segmented control; "My Team" only renders when `isManager=true`.
3. **The WC remote renders only content + sub-nav** — no global chrome.
4. **All status/risk/chess rendering goes through the §5.1 atoms** so the §4.2 taxonomy stays the single source of visual truth.

---

## 6. Page-by-Page Layouts

### 6.1 IC Weekly Commit Workspace — `/weekly-commit`

**Purpose:** the IC's single home for the current-week plan across its whole lifecycle (DRAFT → LOCKED → RECONCILING → RECONCILED). One persistent **plan header** (`PlanLifecycleBar`) atop a **mode-switched body** whose layout is selected by `WeeklyPlanDto.state`. Every primary affordance is gated on `allowedActions[]`.

**Demo content:** R6 Sam Carter (DRAFT, one deliberately unlinked commitment) and R5 Grace Liu (RECONCILING + carry-forward chain).

#### Plan header (`PlanLifecycleBar` — persistent across all modes)

A flat `Card` (bottom-border only) pinned above the body: `WeekRangeLabel`, state pill (`StatusPill`), plan-level counts (`plannedCount`/`unplannedCount`), a 4-node forward-only lifecycle stepper, and the **primary lifecycle action** derived from `allowedActions[]`.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ My Weekly Commit                                                  [Sam Carter] │  ← Breadcrumb
├──────────────────────────────────────────────────────────────────────────────┤
│  Week of Mon Jun 1 – Sun Jun 7, 2026   ● DRAFT     2 planned · 0 unplanned     │
│                                                                                │
│  ○─────────○─────────○─────────○        [ Lock plan ▸ ]  ← primary, allowedAct.│
│  Draft   Locked  Reconciling Reconciled                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Primary action** (single `Button`, color by state) from `allowedActions[]`: `LOCK` → "Lock plan" (indigo); `START_RECONCILIATION` → "Start reconciliation" (amber); `CLOSE_RECONCILIATION` → "Close week" (green). When the expected action is absent, render the button **disabled with a `Tooltip`** stating the unmet precondition. `ADD_UNPLANNED` surfaces as a secondary header button in LOCKED/RECONCILING.
- **Timestamps** (`lockedAt`, `reconciliationStartedAt`, `reconciledAt`) surface as a `Tooltip` on the relevant stepper node ("Locked Mon Jun 1, 9:14 AM CT").
- **Manager-review summary chip** — when `managerReview` is non-null (LOCKED+), a compact `Badge` shows `managerReview.status` + `isOverdue`/`reviewDueAt` (read-only to the IC).

#### DRAFT mode — commitment authoring + RCDO linking

Body is a vertical list of editable `CommitmentCard`s plus "Add commitment." Cards (not a dense table) because each carries the chess-layer + RCDO controls. The unlinked state is **loud** because it is the lock-blocker.

```
┌─ COMMITMENT 1 ───────────────────────────────────────────────  [Edit] [🗑] ─┐
│ Ship workspace onboarding telemetry events                                   │
│ Instrument first-locked-plan funnel; wire dashboards.                        │
│  [P1] [Strategic] [Conf: High] [Self: Aligned]        ← chess-layer chips    │
│  🎯 Become the system of record…  ›  Win customer adoption  ›  SO-1.2 Cut    │
│     new-workspace time-to-first-locked-plan under 10 minutes   [Change ▸]    │
└──────────────────────────────────────────────────────────────────────────────┘

┌─ COMMITMENT 2 ───────────────────────────────────────────────  [Edit] [🗑] ─┐
│ Pair with support on the Q2 escalation backlog                               │
│  [P2] [Maintenance] [Conf: Medium] [Self: Needs-Review]                      │
│  ⚠  No Supporting Outcome linked — required before you can lock              │
│     [ Link Supporting Outcome ▸ ]                          ← red Alert strip │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Add/edit** via `CommitmentForm` in a `Modal` (add) or inline-expanded card (edit). Fields: `title` (`TextInput`, required 1–255 cp), `description` (`Textarea`, ≤4000 cp), the chess-layer block, the Supporting-Outcome picker. `PATCH`/`DELETE` allowed only in DRAFT.
- **Chess-layer entry** (`ChessLayerFields`): `priority` segmented `ButtonGroup` (P0·Critical / P1·High / P2·Normal); `workType` `Select` (Strategic/Maintenance/Blocker; UNPLANNED rejected — planned-only); `confidence` `Select` (High/Medium/Low); `alignmentStatus` `Select` (Aligned/Needs-Review/Misaligned, default Needs-Review). Tooltip on `alignmentStatus`: "Your self-assessment. After you lock, this is read-only and your manager raises concerns via alignment disputes."

#### RCDO Supporting-Outcome picker (`RcdoPicker`) — the core alignment interaction

A `Modal` (or `Drawer` on wide viewports) backed by `GET /api/rcdo` (`RcdoTreeDto`: 1 Rally Cry / 3 Defining Objectives / 9 Supporting Outcomes). A **searchable cascade tree**; the user always selects a leaf **Supporting Outcome** (the only linkable level).

```
┌─ Link a Supporting Outcome ──────────────────────────────────────────[✕]─┐
│  🔎 [ Search outcomes…  "onboarding"                              ]        │
│ ─────────────────────────────────────────────────────────────────────────│
│  ▼ 🎯 Become the system of record every execution team trusts (FY26)      │
│     ▼ Win customer adoption & expansion                                   │
│         ○ SO-1.1  Lift activated-team weekly-active rate to 70%           │
│         ◉ SO-1.2  Cut new-workspace time-to-first-locked-plan < 10 min  ✓ │ ← selected
│         ○ SO-1.3  Reach 120% net revenue retention on strategic accounts  │
│     ▶ Operational excellence in delivery                                  │
│     ▶ Platform reliability & trust                                        │
│ ─────────────────────────────────────────────────────────────────────────│
│  Selected:  Win customer adoption  ›  SO-1.2  Cut time-to-first-locked…    │
│                                            [ Cancel ]  [ Link outcome ▸ ]  │
└──────────────────────────────────────────────────────────────────────────┘
```

Search filters by title/description across all levels (matching ancestors auto-expand, highlight matches). "Link outcome" is disabled until a leaf is chosen. On link, sets `supportingOutcomeId`; the card renders `supportingOutcomeBreadcrumb` and the red unlinked `Alert` clears. There is no RCDO admin/edit anywhere.

#### The LOCK interaction

Triggered by "Lock plan" (present only when `allowedActions` contains `LOCK`). Opens a confirmation `Modal` stating the irreversibility:

```
┌─ Lock this week's plan? ─────────────────────────────────────────[✕]─┐
│  Locking freezes your planned commitments as the baseline for        │
│  reconciliation. You can't edit, add, or remove planned commitments  │
│  after locking — this can't be undone.                               │
│  • 2 planned commitments, all linked to a Supporting Outcome         │
│  • Your manager (Dana Okafor) can review once locked                 │
│                                   [ Keep editing ]  [ Lock plan ▸ ]  │
└──────────────────────────────────────────────────────────────────────┘
```

Confirm → `POST /plans/{id}/lock`. On `200`, body re-renders LOCKED + `Toast` "Plan locked. Baseline frozen." **Blocked lock** (Sam fixture): the button is pre-disabled with the tooltip; if a race produces a `409 UNLINKED_PLANNED_COMMITMENT`, surface a page-top `Alert` rendering the `safeMessage` verbatim and scroll the offending card into view with its red strip + focus ring. (See §8.3.)

#### LOCKED / RECONCILING mode

Planned commitments become the **immutable baseline** (read-only cards, 🔒, chess chips and RCDO breadcrumb shown but disabled). Manager artifacts (`managerAlignmentNote`, dispute strips) become visible.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Week of Jun 1–7  ● RECONCILING  3 planned · 1 unplanned   [Close week ▸]       │  ← Grace Liu
│                                              (Close enabled when all outcomes set)│
├──── PLANNED (locked baseline) ────────────────────────────────────────────────┤
│ ┌ Draft the activation-onboarding runbook  [P1][Strategic] 🔒 ───────────────┐ │
│ │ Win customer adoption › SO-1.2                                              │ │
│ │ Outcome: ( ◉ Completed  ○ Partial  ○ Blocked  ○ Canceled  ○ Carry forward )│ │
│ │ Note: [ Shipped v1; ops reviewed.                                       ]   │ │
│ └────────────────────────────────────────────────────────────────────────────┘ │
│ ┌ Spec the multi-region failover runbook  [P1][Strategic] 🔒 ────────────────┐ │
│ │ Outcome: ( ○ Completed  ○ Partial  ○ Blocked  ○ Canceled  ◉ Carry forward )│ │
│ │           ↳ Carried forward to week of Jun 8–14   [view in next plan ›]     │ │
│ └────────────────────────────────────────────────────────────────────────────┘ │
├──── UNPLANNED (added after lock) ──────────────── [+ Add unplanned work] ──────┤
│ ┌ Hotfix support-portal auth regression  [Unplanned]  ⚠ Link SO before close ┐ │
│ │ [ Link Supporting Outcome ▸ ]  → Operational excellence › SO-2.2            │ │
│ │ Outcome: ( ◉ Completed  ○ Partial  ○ Blocked  ○ Canceled  ○ Carry forward )│ │
│ └────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Outcome entry** (`ReconciliationOutcomeControl`): single-select radio/segmented writing `reconciliationOutcome` + `outcomeNote` via `PATCH`. `CARRIED_FORWARD` is mutually exclusive with completion outcomes (structurally impossible to pick both). Tooltip on Carry-forward: "Selecting this creates a fresh commitment in next week's plan; record partial work as Partially-completed instead."
- **Add unplanned** (`ADD_UNPLANNED`): `CommitmentForm` → server forces `commitmentKind=UNPLANNED`, `workType=UNPLANNED`. Unplanned cards carry the violet `Unplanned` badge, sit under an "UNPLANNED (added after lock)" section, and must link an SO before close.
- **Carry-forward** (`CARRY_FORWARD`, idempotent): selecting the outcome calls `POST /commitments/{id}/carry-forward`; the card shows "↳ Carried forward to week of Jun 8–14 · [view in next plan]"; the successor lives in next week's DRAFT plan with `carryForwardSourceCommitmentId` set.
- **Close gating:** "Close week" appears in `allowedActions[]` **only** when every planned commitment has an outcome and every unplanned has both an outcome and a `supportingOutcomeId`. Disabled button's tooltip lists what's missing.

#### IC dispute-response affordance

When a commitment has `hasUnresolvedDispute=true` and its dispute `allowedActions[]` contains `RESPOND_DISPUTE`, the card surfaces a `DisputePanel` strip:

```
┌─ Alignment dispute — opened by Dana Okafor ──────────────────────────────┐
│  🚩 Flag: MISALIGNED                                       Status: OPEN   │
│  Manager note:                                                            │
│  "This maps to adoption, but the work reads like reliability — re-link    │
│   it to a Platform-reliability outcome or explain the connection."        │
│ ──────────────────────────────────────────────────────────────────────── │
│  Your response  (revise the Supporting Outcome and/or add rationale —     │
│  at least one is required; this does NOT resolve the dispute):            │
│   ◯ Re-link Supporting Outcome   [ Change outcome ▸ ]  → SO-3.2 selected  │
│   Rationale: [ Tying telemetry to p95 latency target, not adoption.   ]   │
│            Only Dana can mark this resolved.        [ Submit response ▸ ] │
└───────────────────────────────────────────────────────────────────────────┘
```

`managerNote` and `flagType` are read-only to the IC. The response form (`RespondDisputeRequest`) requires at least one of `newSupportingOutcomeId` (reuses `RcdoPicker`) or `icResponse` (`Textarea`, ≤4000 cp). `POST /disputes/{id}/respond` transitions `OPEN → IC_RESPONDED`. The IC never sees a Resolve control. Models R3 Aisha (OPEN, MISALIGNED) and R4 Tomas (full resolved loop).

#### Per-commitment Outlook sync chip + retry

Non-blocking; sourced from `GET /api/outlook-sync?planId=` (`OutlookSyncRecordDto`). See §8.2 for the full failure/retry UX. `FAILED` renders an amber, non-blocking `Alert` with the `safeMessage` verbatim + `[ Retry ↻ ]` (shown only when `RETRY_SYNC` is in `allowedActions`). Models R2 Marco's seeded `GRAPH_FORBIDDEN` failure.

**Component file homes:** `features/plan/{WeeklyPlanView,PlanLifecycleBar,LockButton}.tsx`, `features/commitment/{CommitmentForm,CommitmentList,ChessLayerFields,ReconciliationOutcomeForm,CarryForwardButton}.tsx`, `features/rcdo/SupportingOutcomePicker.tsx`, `features/dispute/{DisputePanel,DisputeRespondForm}.tsx`, `features/sync/{SyncStatusBadge,SyncRetryAction}.tsx`.

---

### 6.2 Plan History — read-only past plans, `/weekly-commit/history/:planId`

**Purpose:** a deliberately lighter, hard read-only view of a past plan. Data: `GET /api/plans/{id}` (`WeeklyPlanDto`). On a `RECONCILED`/past plan, `allowedActions[]` is empty of all mutating verbs; the only actions that may appear are `RETRY_SYNC` (a historic sync still `FAILED`) and `COMMENT` (comments stay open on one's own plan). Enablement is driven off `allowedActions[]` exactly as the live workspace — we just expect a near-empty array.

#### Entry point — week switcher / history list

Two paths, both reusing `WeekRangeLabel`:
- **(a) Inline "week switcher" `Dropdown`** anchored to the live-plan week label, recent weeks newest-first.
- **(b) "History" tab** — a compact `Table` of the IC's own past plans (the canonical index).

```
WC content area  ── IC workspace ──────────────────────────────────────────────
┌──────────────────────────────────────────────────────────────────────────────┐
│  My Weekly Commit    ┌─[ Current week ]─[ History ]─┐         (Tabs)           │
│  Plan history · Priya Raman                              Showing 8 of 8        │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ Week              │ State       │ Planned/Done │ Review     │ Risk        │ │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ Jun 1–7, 2026     │ ●RECONCILED │ 3 · 2✓1↻     │ REVIEWED   │ —           │ │
│  │ May 25–31, 2026   │ ●RECONCILED │ 4 · 3✓1⊘     │ ⚠ W/DISPUTES│ CARRY_FWD  │ │
│  │ May 11–17, 2026   │ ●RECONCILED │ 5 · 4✓1✕     │ ⚑ OVERDUE  │ BLOCKED     │ │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                              ‹ 1 2 ›   (Pagination)            │
└──────────────────────────────────────────────────────────────────────────────┘
```

Fields per row: `weekStartDate`–`weekEndDate` (via `WeekRangeLabel`), `state` Badge (near-always RECONCILED; a stray LOCKED/RECONCILING renders in its live color so an abandoned week is visible), `plannedCount` + outcome mini-tally, `managerReview.status` (with derived OVERDUE styling), and a compact risk summary. The whole row links to the plan. *(MVP has no list-plans endpoint; the list is assembled client-side from recently-touched planIds + carry-forward links discovered on the current plan, each row hydrating from `GET /api/plans/{id}` — the one place the UI synthesizes a collection.)*

#### The read-only reconciled plan view — planned baseline vs. actual

The core of this screen is the **two-column "story"**: each commitment renders as a `Card` split into a left **"Planned baseline (frozen)"** column and a right **"Actual outcome"** column, making the locked-immutability promise visible.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ Breadcrumb:  My Weekly Commit › History › Jun 1–7, 2026          [‹ Prev wk] [Next wk ›]│
│  Week of Jun 1–7, 2026   ●RECONCILED      Locked Jun 1 · Reconciled Jun 5             │
│  Owner: Priya Raman                                                                    │
│ ┌────────────────────────┐  ┌──────────────────────────────────────────────────────┐ │
│ │ SUMMARY (Card)         │  │  PLANNED-VS-ACTUAL                                     │ │
│ │ Planned        3       │  │  ┌─ Commitment 1 ───────────────────────────────────┐ │ │
│ │ Unplanned      1       │  │  │ PLANNED BASELINE (frozen)   │ ACTUAL OUTCOME      │ │ │
│ │ ─ Outcomes ─           │  │  │ Lift activation WAU dash    │ ✔ COMPLETED         │ │ │
│ │  Completed     2  ●●   │  │  │ P0 · Strategic · High conf  │                     │ │ │
│ │  Carried fwd   1  ↻    │  │  │ Aligned · RC ▸ DO-1 ▸ SO-1.1│ "Shipped Tue; 71%." │ │ │
│ │ ─ Review ─             │  │  └─────────────────────────────┴─────────────────────┘ │ │
│ │  REVIEWED              │  │  ┌─ Commitment 2 ───────────────────────────────────┐ │ │
│ │  Reviewer: Dana Okafor │  │  │ Reconciliation runbook v2   │ ↻ CARRIED-FORWARD   │ │ │
│ │  "Solid week."         │  │  │ P1 · Strategic · Med conf   │  → went to Jun 8–14 │ │ │
│ │ ─ Calendar ─           │  │  │ Needs-Review · RC▸DO-2▸SO-2.3│    [view successor] │ │ │
│ │  ✓ Planning synced     │  │  │  Lineage: ←in May 25–31 →out Jun 8–14            │ │ │
│ │  ✓ Recon synced        │  │  └─────────────────────────────┴─────────────────────┘ │ │
│ └────────────────────────┘  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Left (Planned baseline):** `title`, `description`, chess-layer Badges (`priority`/`workType`/`confidence`/`alignmentStatus`), `supportingOutcomeBreadcrumb` as a static RC ▸ DO ▸ SO trail, plus an "Unplanned" pill for `commitmentKind=UNPLANNED`.
- **Right (Actual outcome):** `reconciliationOutcome` as the dominant Badge (fixed icon+color per §4.2), `outcomeNote` as quoted muted text. **Visual diff cue:** any non-`COMPLETED` outcome gets a thin left-border accent in the outcome color so non-completed rows pop on a scan.
- **Carry-forward chains** (both directions): a "lineage" strip — "← Carried in from May 25–31 [view source]" and/or "→ Carried forward to Jun 8–14 [view successor]" — plus a compact breadcrumb-style chain strip in the summary Card ("May 25 ▸ [Jun 1 you are here] ▸ Jun 8"). Demo seed: Grace's `C_next` shows "← Carried in from May 25–31" linking to `C_src`. If a linked plan 404s, the chip degrades to a non-link "Linked plan unavailable" tooltip.
- **Alignment & dispute history:** an inline trigger badge ("Resolved dispute" / "Open dispute") opens a read-only `Accordion` timeline (flat, not nested) of `AlignmentDisputeDto`: `flagType` Badge, `managerNote`, `icResponse` (with before→after SO breadcrumb diff when `newSupportingOutcomeId` present), `resolutionNote`, each actor's `displayName` + timestamp. **No action buttons** even if a dispute is technically still OPEN on an old plan. Demo: R4 Tomas's full `OPEN→IC_RESPONDED→RESOLVED` loop.
- **Sync:** informational `SyncStatusBadge` rows in the summary Card; a still-`FAILED` historic record with `RETRY_SYNC` renders the same non-blocking warning + Retry, never blocking reading.

**Density:** desktop two-column; below `md` each Card stacks (Planned section then Actual section) with sticky "Planned"/"Actual" section headers.

---

### 6.3 Manager Alignment Command Center — `/manager/command-center`

**Purpose:** the manager's primary surface — the direct-report roll-up that answers "who is at risk this week?" at a glance, plus the entry to review, dispute, and comment flows. Source: `ManagerCommandCenterRowDto[]` via `GET /api/manager/command-center?weekStart=`. **Demo:** Dana Okafor + her 6 reports.

#### Page header — week selector + at-a-glance SLA strip

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  Alignment Command Center                                                              │
│  Your direct reports' weekly alignment at a glance.                                    │
│  Week:  [ ◀ ]  Jun 1 – Jun 7, 2026  [ ▾ ]  [ ▶ ]        Updated 2 min ago  [ ↻ ]      │
│  ┌─ At-a-glance (this week) ───────────────────────────────────────────────────────┐ │
│  │  6 reports   •   ⚠ 1 review OVERDUE   •   🔶 2 with open disputes   •            │ │
│  │  ◔ 1 reconciling   •   ○ 1 not locked (draft)   •   ✓ 1 reviewed clean          │ │
│  └────────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

- **Week selector** (`weekStart` query param): a `Datepicker` constrained to Mondays + prev/next chevrons; label via `WeekRangeLabel`. Changing week refetches with the new key.
- **Freshness:** "Updated N min ago" from the newest projection `updatedAt` + a manual refetch icon (no live updates).
- **At-a-glance strip:** a `Card` computed client-side from loaded rows — counts of OVERDUE reviews, rows with `unresolvedDisputeCount > 0`, RECONCILING, DRAFT, and REVIEWED-clean rows. This is the "see drift without opening every plan" promise.

#### Filter bar

All eight filters from the query params, as `Dropdown`s (multi-select where natural), applied filters shown as removable `Badge` chips with `✕`, plus "Clear all":

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  FILTERS                                                                  [ Clear all ]│
│  [ Person ▾ ] [ Plan state ▾ ] [ Review state ▾ ] [ Defining Obj ▾ ] [ Supporting O. ▾]│
│  [ Priority ▾ ] [ Work type ▾ ] [ Alignment ▾ ]                                        │
│  Active:  [ Review: OVERDUE ✕ ]  [ Priority: P0 ✕ ]            Showing 2 of 6 reports  │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

Vocabularies (exact wire enums): **Person** → `employeeId` (6 reports + Dana for her own row); **Plan state** → `planState` (DRAFT "Not locked / Draft", LOCKED, RECONCILING, RECONCILED); **Review state** → `reviewState` (NOT_REVIEWED, REVIEWED_WITH_DISPUTES, REVIEWED, **OVERDUE** — the only derived one, styled red); **Defining Objective** → `definingObjectiveId` (DO-1/2/3); **Supporting Outcome** → `supportingOutcomeId` (9 SOs grouped under DO); **Priority** → P0/P1/P2; **Work type** → STRATEGIC/MAINTENANCE/BLOCKER/UNPLANNED; **Alignment** → ALIGNED/NEEDS_REVIEW/MISALIGNED. Every change refetches; the count ("Showing 2 of 6") reads `page.totalElements`.

#### The direct-report roll-up — dense `Table` (the page's core value)

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ DIRECT REPORTS — Week of Jun 1–7, 2026                              Sort: Week ▼  Name ▲   6 reports │
├────────────────┬────────────┬────────────────────────┬───────────────┬──────────────────────┬──────┤
│ REPORT         │ PLAN STATE │ REVIEW STATUS          │ RECONCILE     │ RISK CHIPS           │      │
├────────────────┼────────────┼────────────────────────┼───────────────┼──────────────────────┼──────┤
│ ⬤PR Priya Raman│ ●LOCKED    │ ○ Not reviewed         │ —             │ 3P 0U                │[Review]│
│                │            │ Due Jun 2, 5:00 PM     │               │                      │  ›   │
├────────────────┼────────────┼────────────────────────┼───────────────┼──────────────────────┼──────┤
│ ⬤MB Marco Bell.│ ●LOCKED    │ 🔴 OVERDUE             │ —             │ 3P 1U · ⚠ sync failed│[Review]│
│         ⚠sync  │            │ Was due May 29, 5:00 PM│               │                      │  ›   │
├────────────────┼────────────┼────────────────────────┼───────────────┼──────────────────────┼──────┤
│ ⬤AK Aisha Khan │ ●LOCKED    │ 🟠 Reviewed w/ disputes│ —             │ 4P 🟣1 misaligned    │[Open]│
│                │            │ (SLA met) Jun 1, 3:12PM│               │ 🔵1 needs-rev ●1 disp│  ›   │
├────────────────┼────────────┼────────────────────────┼───────────────┼──────────────────────┼──────┤
│ ⬤TN Tomas Novak│ ●LOCKED    │ 🟢 Reviewed Jun 1      │ —             │ 3P (1 dispute resolvd)│[Open]│
├────────────────┼────────────┼────────────────────────┼───────────────┼──────────────────────┼──────┤
│ ⬤GL Grace Liu  │ ◔RECONCIL. │ 🟢 Reviewed Jun 1      │ In progress   │ 3P 1U ⛔1 blocked     │[Open]│
│                │            │                        │ 1 carry-fwd   │ ↪1 carry-forward     │  ›   │
├────────────────┼────────────┼────────────────────────┼───────────────┼──────────────────────┼──────┤
│ ⬤SC Sam Carter │ ○DRAFT     │ — (not locked)         │ —             │ 2P (1 unlinked)      │  —   │
└────────────────┴────────────┴────────────────────────┴───────────────┴──────────────────────┴──────┘
   Legend  P = planned  U = unplanned  ●LOCKED ◔RECONCILING ○DRAFT ✓RECONCILED      ‹ 1 › 25/page
```

Column → field mapping: **Report** = `employeeDisplayName` + `Avatar` (initials, `⚠sync` micro-indicator when that report owns a `FAILED` sync); **Plan state** = `planState` `StatusPill` (gray "Not started" when `weeklyPlanId === null`); **Review status** = `reviewStatus` + `isReviewOverdue` + `reviewDueAt`/`reviewedAt` (see §6.3 SLA model below); **Reconcile** = "In progress"/"✓ Done"/"—" + carry-forward count; **Risk chips** = the count fields as compact Badges (zero-valued risk chips omitted; `plannedCount`/`unplannedCount` always show as the neutral "3P 1U" volume chip); **Action** = primary `Button` from `allowedActions[]` ("Review" gray when NOT_REVIEWED, "Open" once reviewed, "—" disabled when DRAFT). Whole row clickable → opens the report drawer. Default sort `weekStartDate DESC, employeeDisplayName ASC`.

**Risk chips** (`Badge` + `Tooltip`, the "see drift" signal — no game imagery): `3P` planned (gray), `1U` unplanned (gray), 🟣 `misalignedCount` (purple), 🔵 `needsReviewCount` (blue), ⛔ `blockedCount` (red), ↪ `carryForwardCount` (amber), ● `unresolvedDisputeCount` (rose filled). *Count derivation:* `misalignedCount` counts a commitment if `alignmentStatus = MISALIGNED` **OR** it has an open dispute with `flagType = MISALIGNED` — so R3 Aisha's misaligned chip is driven by her open dispute. Chips wrap 2 lines max; overflow → `+N` chip with Tooltip. Responsive: below ~768px the Table collapses to a card-grid (one Card per report, same fields, risk chips in the footer).

#### Review SLA model & OVERDUE visual treatment

The SLA measures whether the manager examined the locked plan on time — `reviewDueAt` = 17:00 org-tz on the next business day after `lockedAt` (weekdays-only). `isReviewOverdue` is derived (`now > reviewDueAt AND reviewStatus = NOT_REVIEWED`), never stored.

```
 ○ Not reviewed            gray dot • "Due Jun 2, 5:00 PM"     → R1 Priya
 🔴 OVERDUE                red filled Badge • "Was due May 29"  → R2 Marco  (the only red)
 🟠 Reviewed w/ disputes    amber Badge • "SLA met · Jun 1"     → R3 Aisha
 🟢 Reviewed               green Badge • "Jun 1, 4:40 PM"      → R4 Tomas, R5 Grace
 — (not locked)            muted text • "Review unavailable"    → R6 Sam
```

- **OVERDUE is the only red** — a loud, derived signal (red Badge, bold, Tooltip "Review was due {reviewDueAt} — not yet reviewed"). It pulls attention without reordering (sort stays deterministic).
- **REVIEWED_WITH_DISPUTES is amber, explicitly tagged "SLA met"** — the micro-copy is essential so a manager never misreads amber as "I'm late." The amber communicates unresolved alignment work remains (mirrored by the dispute chip).
- 15Five learning applied: "value collapses if managers don't respond" — review is built into the loop as an accountable, visible obligation (the OVERDUE signal + the always-present Review action), not an optional nicety.

#### Opening a report — the locked-plan review `Drawer`

Clicking a row opens the report's plan (`GET /api/plans/{id}`, direct-report scoped) in a right-side `Drawer` (~640px) over the table, so the manager reviews-and-returns without losing context.

```
┌──────────────────────────────────────────── DRAWER (right) ──────────────────────────────┐
│  ⬤AK  Aisha Khan — Week of Jun 1–7, 2026                                            [ ✕ ] │
│  ●LOCKED   🟠 Reviewed with disputes (SLA met)   Locked Jun 1, 9:02 AM                     │
│  [ Mark reviewed ]   ← MARK_REVIEWED in allowedActions[]                                   │
│  COMMITMENTS (4 planned)                                                                   │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐   │
│  │ ● Ship squad reconciliation-rate dashboard                          P1 · Strategic │   │
│  │   RCDO: Operational excellence › Green weekly-commit reconciliation >90% (SO-2.3)  │   │
│  │   Alignment: NEEDS_REVIEW · Confidence: HIGH                                        │   │
│  │   🔴 OPEN DISPUTE — Misaligned                       [ View dispute ]  [ Comment ] │   │
│  ├──────────────────────────────────────────────────────────────────────────────────┤   │
│  │ ● Cut onboarding time-to-first-locked-plan                          P0 · Strategic │   │
│  │   RCDO: Win adoption › Time-to-first-locked-plan < 10 min (SO-1.2)                 │   │
│  │   Alignment: ALIGNED · Confidence: MEDIUM           [ Flag alignment ] [ Comment ] │   │
│  └──────────────────────────────────────────────────────────────────────────────────┘   │
│  PLAN COMMENTS (flat)  ───────────────────────────────────────────  see §6.3 comments     │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

Per-commitment read-only fields: `title`, `priority`, `workType`, `confidence`, `alignmentStatus` (quiet label, read-only post-lock), `supportingOutcomeBreadcrumb`, `hasUnresolvedDispute` (red strip), and outcome fields during RECONCILING/RECONCILED. **Manager actions gated by `allowedActions[]`:** `MARK_REVIEWED` (header), `OPEN_DISPUTE`/"Flag alignment" (per commitment, when no existing unresolved dispute), `COMMENT` (per commitment + plan), and `managerAlignmentNote` (the one manager-owned post-lock-mutable field, an inline editable "Manager note" saved via `PATCH` of only that field).

**Mark-reviewed:** a `Modal` with an optional `summaryNote` `Textarea`. **The status is server-derived** — the modal *previews* the outcome ("1 unresolved dispute → REVIEWED_WITH_DISPUTES; 0 → REVIEWED") but never lets the manager pick it. On success, RTK Query invalidates plan + manager-summary tags → the row's review cell flips, OVERDUE clears, `Toast` "Plan marked reviewed."

#### Alignment dispute workflow

Manager flags `NEEDS_REVISION | MISALIGNED` with a required note → IC responds → only the direct manager resolves. At most one unresolved dispute per commitment. Lifecycle `OPEN → IC_RESPONDED → RESOLVED`.

**Manager opens** (R3 Aisha): a `Modal` with `flagType` `Radio` and a required `managerNote` `Textarea` (submit disabled until non-blank; second open → `409 SECOND_OPEN_DISPUTE`). `POST /api/commitments/{id}/disputes`.

**Dispute panel** (`Drawer`): `flagType` Badge + `status` Badge + a 3-step stepper (`OPEN → IC_RESPONDED → RESOLVED`), `managerNote`, `icResponse` (null while OPEN), and on resolved, the optional `resolutionNote`. The manager sees **`RESOLVE_DISPUTE` only** (→ `POST /api/disputes/{id}/resolve`); never Respond. After resolve, if it was the last unresolved dispute, the parent review re-evaluates `REVIEWED_WITH_DISPUTES → REVIEWED`. Seed exemplars: R3 Aisha (1 OPEN MISALIGNED, `icResponse` null) and R4 Tomas (1 RESOLVED, full loop populated).

#### Flat comments

`CommentDto` (depth 0, `parentCommentId` null) on PLAN and COMMITMENT targets only. A manager may comment only when the plan is LOCKED+ (gated by `COMMENT`). `CommentList` = stacked `Avatar` + `authorDisplayName` + `createdAt` + React-escaped `body` (no reply/indent — nesting is out of scope). `CommentForm` = single `Textarea` + Post, shown only when `COMMENT ∈ allowedActions`. Paginated (`createdAt ASC`). Bodies rendered React-escaped — XSS/Unicode probe strings (`<img src=x onerror=alert(1)>`, `🚩مرحبا`) must render literally. The dispute IC-response uses the dedicated `icResponse` field, **not** comments.

---

### 6.4 RCDO Coverage Heatmap + Drill-down — `/manager/heatmap`

**Purpose:** answer one question at a glance — "Where is my team's week drifting, and under which strategic objective?" Source: `GET /api/manager/heatmap?weekStart=&definingObjectiveId=&supportingOutcomeId=` → `HeatmapResponseDto { weekStart, cells: HeatmapCellDto[] }` (NOT paginated — bounded by reports × DOs). Drill-down: `GET /api/manager/heatmap/{cellId}/drilldown` → `HeatmapDrilldownDto`.

**Core principle (NOT an opaque health score):** risk is always shown as explicit, named badges from the fixed six-badge vocabulary — never folded into a single calculated color/score. A cell is two **independent** signals: (a) **volume** = `commitmentCount` (calm neutral fill intensity), and (b) **risk** = `riskBadges[]` (distinct labeled chips). A manager must be able to say "this cell is hot because of OVERDUE_REVIEW + BLOCKED," not "this cell is 73% red." Volume and risk are deliberately decoupled.

#### Page layout (default grain: reports × Defining Objectives)

Rows = active direct reports; columns = the 3 Defining Objectives; each cell = the commitments that report mapped under that DO this week. Plus a leading "Report" label column and a trailing per-row total.

```
│  RCDO Coverage Heatmap            Week of Jun 1 – Jun 7, 2026  [◀ Datepicker ▶]   [↻]    │
│  Rally Cry: "Become the system of record every execution-driven team trusts by FY26."   │
│  Filters: [Person▾][Plan state▾][Review state▾][Defining Objective▾][Supporting O.▾]…    │
│  ┌──────────────┬───────────────────┬───────────────────┬───────────────────┬──────────┐│
│  │  Report      │ DO-1 Win customer │ DO-2 Operational  │ DO-3 Platform     │ ROW TOTAL ││
│  │              │ adoption & expan. │ excellence in del.│ reliability&trust │          ││
│  ├──────────────┼───────────────────┼───────────────────┼───────────────────┼──────────┤│
│  │ Priya Raman  │  ▓ 2 (no risk)    │  ▓ 1 (no risk)    │  ▓ 1 (no risk)    │  4 clean ││
│  │ Marco Bellini│  ▓▓ 3 ⚑OVR ⊘UNRV  │  ·  0 (gap) ░     │  ▓ 1 ⚑OVR ⊘UNRV   │  4 · 2bd ││
│  │ Aisha Khan   │  ▓ 1 ◐NEEDS_REVIEW│  ▓▓ 2 (no risk)   │  ▓ 1 ✕MISALIGNED  │  4 · 2bd ││
│  │ Tomas Novak  │  ▓ 1 (no risk)    │  ▓ 1 (no risk)    │  ▓▓ 2 (no risk)   │  4 clean ││
│  │ Grace Liu    │  ▓▓▓ 4 ⤳CARRY_FWD │  ▓▓ 3 ⛔BLOCKED    │  ·  0 (gap) ░     │  7 ⚠high ││
│  │ Sam Carter   │  ·  0 (gap) ░     │  ▓ 1 (no risk·dft)│  ·  0 (gap) ░     │ 1 notlckd││
│  └──────────────┴───────────────────┴───────────────────┴───────────────────┴──────────┘│
│  ◇ Dana Okafor (you · self plan) — muted, no risk action, RECONCILED      [collapse ▾]   │
│  Legend ▾   ·  6 reports · 18 cells · 4 cells with risk · 3 coverage gaps                 │
```

Maps exactly to the seeded `manager_heatmap_cell` rows: MISALIGNED (R3 Aisha), OVERDUE_REVIEW + UNREVIEWED (R2 Marco), BLOCKED (R5 Grace), CARRY_FORWARD (R5 Grace), NEEDS_REVIEW (R3 Aisha). *Self-row note:* Dana owns a RECONCILED self plan but no cells are scoped *to* her — her row is a muted, collapsible affordance linking to `/weekly-commit`, carrying no risk badges, excluded from the team roll-up.

#### Cell anatomy & visual encoding

Each cell is a styled `<button>` grid cell (for click + `aria-label`), two stacked encodings:

**(1) Volume fill — `commitmentCount` bucketed, NEUTRAL slate (never "health"):**

| Bucket | `commitmentCount` | Fill | Glyph |
|---|---|---|---|
| Gap | `0` | `bg-white` + dashed `border-gray-300`, "no coverage" | `·` / `░` |
| Light | `1` | `bg-slate-50` | `▓` |
| Normal | `2–3` | `bg-slate-100` | `▓▓` |
| Heavy / overload | `≥4` | `bg-slate-200` + thin `border-amber-300` + "⚠ high" in row total | `▓▓▓` |

Fill is monochrome slate on purpose — it conveys *load*, not *danger*. **Coverage gaps are first-class** (dashed border + "no coverage") so a manager sees a report with zero commitments under a strategic DO (Marco under DO-2; Sam under DO-1/DO-3), distinct from overload (Grace's DO-1 = 4).

**(2) Risk badges** — the six-value `RiskBadge` vocabulary (§4.2), color + icon + text, wrapped chip row, max 3 visible then `+N`, ordered by severity (red → amber → gray). A "no risk" cell shows a muted "— no risk" line (or "— draft" for DRAFT plans) — explicit reassurance, not blank space.

**(3) Optional dense micro-meta line** (Legend toggle, off by default): `plannedCount`/`unplannedCount` split + priority spread, backed by the per-cell counts.

#### Legend (collapsible, restates the principle)

```
┌─ Legend ──────────────────────────────────────────────────────────────────────┐
│  RISK BADGES                              VOLUME (commitment count)             │
│  ✕ Misaligned   strategic conflict        ░  0   no coverage (gap)              │
│  ⛔ Blocked      work is blocked           ▓  1   light                          │
│  ⚑ Overdue rev. review SLA missed         ▓▓ 2–3 normal                         │
│  ◐ Needs review IC self-flag              ▓▓▓ 4+ high load (⚠ investigate)       │
│  ⊘ Unreviewed   locked, not yet reviewed   [ ] High-contrast / pattern mode      │
│  ⤳ Carry-forward unfinished, moved fwd                                          │
│  Volume = load (neutral). Risk = explicit badges. No single health score.        │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Cell drill-down (`Drawer`)

Clicking a non-empty cell opens a right `Drawer` (focused detail, not a route). `GET /api/manager/heatmap/{cellId}/drilldown` → groups linked commitments by Supporting Outcome (`supportingOutcomes[]`), each = `supportingOutcomeTitle` + a **paginated** `commitments` envelope (default sort `priority ASC, createdAt ASC`).

```
┌─ Drawer: Grace Liu × DO-2 Operational excellence ───────────┐
│  Grace Liu  ·  DO-2 Operational excellence    plan: RECONCILING │
│  Cell risk:  ⛔ Blocked                                       │
│  ▸ SO-2.2  Median support first-response < 2 business hrs     │
│     ┌─────────────────────────────────────────────────────┐ │
│     │ P0 · STRATEGIC · conf HIGH      ✕ has dispute         │ │
│     │ "Cut triage handoff latency"   RC › DO-2 › SO-2.2     │ │
│     │ outcome: ⛔ BLOCKED  [View in command center →] [Comment]│ │
│     └─────────────────────────────────────────────────────┘ │
│  ▸ SO-2.3  Squad reconciliation rate > 90%                    │
│     ┌─────────────────────────────────────────────────────┐ │
│     │ P1 · MAINTENANCE · conf MEDIUM   1 UNPLANNED          │ │
│     │ "Backfill last week's runbook gaps"  outcome: ⤳ CARRIED-FWD│
│     └─────────────────────────────────────────────────────┘ │
│  Showing 1–3 of 3       [Pagination ◀ 1 ▶]                    │
└──────────────────────────────────────────────────────────────┘
```

Per-commitment fields: `title`, chess chips, `alignmentStatus`, `supportingOutcomeBreadcrumb`, `reconciliationOutcome` + `outcomeNote`, `hasUnresolvedDispute`, `CARRY_FORWARD` chip, `managerAlignmentNote`, UNPLANNED tag. Actions are `allowedActions[]`-driven (`OPEN_DISPUTE`, `COMMENT`); `MARK_REVIEWED` is intentionally **not** offered here — the heatmap stays a *diagnostic* surface and the command center the *action* surface, so the drawer's primary navigation is **"View in command center →"** (deep-links to `/manager/command-center` filtered to that report + DO). A drill-down on a cell not the manager's own → `404` (render its `safeMessage`, IDOR-safe).

#### Filter interaction

Same vocabulary as the command center. Two params are **server-side** (`definingObjectiveId` collapses the grid to a single DO column; `supportingOutcomeId` recomputes cell counts/badges to one SO and refetches); the rest narrow the bounded cell set client-side (no refetch). A filtered-to-empty result still renders the grid skeleton with an inline "No commitments match these filters." Outlook sync is **not** surfaced on the heatmap (it's an IC-plan / command-center concern — no `RETRY_SYNC` here).

---

### 6.5 Default / Landing — `/`

Renders no chrome. Resolves `GET /api/me` and redirects per §3.4 (IC → `/weekly-commit`; manager → `/manager/command-center`). Loading = centered `Spinner` (`LoadingState`); error = `ErrorState` with `safeMessage` + Retry. For the demo, Dana lands on the command center showing all 6 reports' varied states.

---

## 7. Key User Journeys

> Each step names the **route**, the **`allowedActions[]`** that gates the visible button, the **endpoint** the button hits, the **resulting state transition**, and the **view-state** the user lands in. `allowedActions[]` is a UI affordance only — a missing action is hidden or disabled-with-tooltip, never unguarded. All post-action state is what the *re-fetched* DTO returns (no optimistic UI).

### Journey 1 — IC drafts, links Supporting Outcomes, and locks (with the blocked-lock path)

*Persona: R6 Sam (DRAFT, one unlinked commitment).*

1. **`/weekly-commit`** — Sam lands via `/` (IC default). `GET /api/plans/current` → loading skeleton → success: DRAFT, `plannedCount=2`, two `CommitmentCard`s.
2. **Reads the lifecycle.** `PlanLifecycleBar` node 1 DRAFT active. `allowedActions[]` lacks `LOCK` (one commitment unlinked), so `LockButton` is **disabled** with a `Tooltip`: "1 of 2 commitments isn't linked to a Supporting Outcome."
3. **Edits a commitment.** Second card → `CommitmentForm`; title/description/`ChessLayerFields` editable (DRAFT). Save → `PATCH /api/commitments/{id}`; inline `ErrorState` from `safeMessage` on validation reject.
4. **Links the Supporting Outcome.** `RcdoPicker` (from `GET /api/rcdo`) → Sam picks SO-2.3. Save writes `supportingOutcomeId`; the row's `supportingOutcomeBreadcrumb` renders and the ⚠ clears.
5. **Lock becomes available.** Mutation invalidates `plans` → refetch; the DTO now has `LOCK` in `allowedActions[]`; `LockButton` enables.
6. **(Blocked path)** If Sam clicks Lock while still unlinked, `POST /api/plans/{id}/lock` returns `409 UNLINKED_PLANNED_COMMITMENT`; a failure `Alert` renders the `safeMessage` verbatim; `fieldErrors[]` highlight the offending rows. Plan stays DRAFT. (Empty plan → `409 EMPTY_PLAN_LOCK`.)
7. **Locks.** With all linked, Lock opens the confirm `Modal` → `POST /api/plans/{id}/lock` → `200`: `state=LOCKED`, `lockedAt` set, `managerReview` present (`NOT_REVIEWED`, `reviewDueAt` = next business day 17:00 CT), `allowedActions=[START_RECONCILIATION, ADD_UNPLANNED]`.
8. **Locked view.** Stepper advances; cards go read-only (baseline immutable); the `IC_PLANNING` `SyncStatusBadge` appears; `START_RECONCILIATION` is next.

### Journey 2 — IC reconciles, adds unplanned, carries forward (baseline stays visible & unchanged)

*Persona: R5 Grace (RECONCILING + carry-forward chain). Borrows 15Five's "close the prior period before opening the new one."*

1. **LOCKED → start.** `allowedActions` includes `START_RECONCILIATION` → `POST /api/plans/{id}/start-reconciliation` → `state=RECONCILING` (also fires the non-blocking `IC_RECONCILIATION` sync record).
2. **Reconciliation workspace.** The planned baseline is shown **read-only and unchanged** (immutability visible, not just enforced). Each row gains `ReconciliationOutcomeControl`. `CLOSE_RECONCILIATION` is absent until every outcome is set, so Close is disabled-with-tooltip.
3. **Records planned outcomes.** Row → Completed, row → Blocked (each `PATCH /api/commitments/{id}` with `{reconciliationOutcome, outcomeNote}`). Single-select enforces the single-outcome rule.
4. **Adds unplanned.** `ADD_UNPLANNED` → `POST /api/plans/{id}/unplanned-commitments`; server forces `commitmentKind=UNPLANNED`, `workType=UNPLANNED`; violet badge; ⚠ until SO linked via `RcdoPicker`.
5. **Carries forward.** On the source row, selecting Carried-forward surfaces `CARRY_FORWARD` → `POST /api/commitments/{id}/carry-forward` → returns the next-week `WeeklyCommitmentDto` (`carryForwardSourceCommitmentId` set, unlinked-DRAFT in the next-Monday shell). `Toast` "Carried forward to Jun 8 – Jun 14 — it's a draft in next week's plan." Source row unchanged; re-clicking is idempotent.
6. **Close gated.** Once every planned has an outcome and every unplanned has both an outcome and `supportingOutcomeId`, the refetched DTO adds `CLOSE_RECONCILIATION`. A premature close → `422 UNPLANNED_MISSING_LINK_AT_CLOSE` → `Alert` with `safeMessage`.
7. **Closes.** Confirm `Modal` → `POST /api/plans/{id}/close-reconciliation` → `state=RECONCILED`, `reconciledAt` set. The planned-vs-actual table is now read-only; later viewed via `/weekly-commit/history/:planId`.

### Journey 3 — Manager spots OVERDUE + misalignment, flags, IC responds, manager resolves + reviews

*Personas: Dana + R3 Aisha.*

1. **`/manager/command-center`.** `isManager=true` gates the sub-nav. `GET /api/manager/command-center?weekStart=2026-06-01` → loading → success table.
2. **Spots the overdue review.** Marco's row: `isReviewOverdue=true` → red OVERDUE_REVIEW + UNREVIEWED chips + the derived "Overdue" `StatusPill`.
3. **Spots misalignment on the heatmap.** Heatmap tab → `/manager/heatmap` → Aisha × DO-1 carries `riskBadges=[MISALIGNED, NEEDS_REVIEW]` (the borrowed 15Five red/yellow status-color pattern, no game imagery).
4. **Drills into the cell.** `GET /api/manager/heatmap/{cellId}/drilldown` → SO breakdown with the offending commitment (`NEEDS_REVIEW`).
5. **Flags Misaligned.** `OPEN_DISPUTE` present → `Modal`: `FlagType` (MISALIGNED) + required `managerNote`. Empty note → `422`. Submit → `POST /api/commitments/{id}/disputes` → `status=OPEN`. Second flag → `409 SECOND_OPEN_DISPUTE`. Invalidates `manager-summary` + `heatmap`.
6. **IC responds.** As Aisha (`/weekly-commit`), the disputed card shows the flag + note; `RESPOND_DISPUTE` present → `DisputeRespondForm` requires at least one of `icResponse` or `newSupportingOutcomeId` → `POST /api/disputes/{id}/respond` → `IC_RESPONDED`. The response does **not** resolve.
7. **IC cannot resolve.** Aisha never sees `RESOLVE_DISPUTE`; a direct call → `403 IC_CANNOT_RESOLVE_DISPUTE`.
8. **Manager resolves.** As Dana, dispute now `IC_RESPONDED`, `allowedActions=[RESOLVE_DISPUTE]` → `POST /api/disputes/{id}/resolve` (optional `resolutionNote`) → `RESOLVED`. Server re-derives the review: last dispute resolved ⇒ `REVIEWED_WITH_DISPUTES → REVIEWED`. The MISALIGNED heatmap badge clears.
9. **Marks reviewed.** `MARK_REVIEWED` → `POST /api/manager/reviews/{reviewId}/mark-reviewed` with optional `summaryNote`. Status server-derived from unresolved-dispute count. Row flips green; `isReviewOverdue=false`.

### Journey 4 — Outlook sync fails after lock → non-blocking warning → manual retry succeeds

*Persona: R2 Marco (seeded FAILED `IC_PLANNING` sync). Calendar failures never block the workflow.*

1. **Locked plan loads with a sync warning.** `GET /api/outlook-sync?planId=` returns the seeded record: `eventKind=IC_PLANNING`, `status=FAILED`, `failureCode=GRAPH_FORBIDDEN`, `safeMessage="Calendar sync failed; you can retry."`, `retryCount=1`, `graphEventId=null`.
2. **Warning, not a blocker.** `SyncStatusBadge` renders a **warning** (amber) `Alert`, subordinate to the plan, captioned "This does not affect your locked plan." Lifecycle and all plan `allowedActions[]` stay usable. `safeMessage` rendered verbatim (no `failureCode` prose, no tokens).
3. **Manual retry.** `RETRY_SYNC` present → `SyncRetryAction` "Retry sync."
4. **Retries.** `POST /api/outlook-sync/{syncRecordId}/retry` → `FAILED → RETRY_REQUESTED → QUEUED`. Button shows `Spinner`; badge updates to "Syncing…" on next refetch. No optimistic update.
5. **Success.** A later refetch returns `SYNCED` with `graphEventId`; the warning disappears, replaced by a green badge ("Calendar event created"); `Toast` "Calendar event synced."
6. **If retry fails again.** Returns to `FAILED`, `retryCount=2`, same `safeMessage`; the Retry affordance returns. The workflow was never blocked.

### Journey 5 — Manager toggles between "My Weekly Commit" and "My Team"

*Persona: Dana (manages R1–R6 and owns her own RECONCILED plan; no one manages Dana).*

1. **Sub-nav, not app chrome.** `GET /api/me` returns Dana, `isManager=true`; the **My Team** group renders only because of it. A pure IC sees only "My Weekly Commit."
2. **My Weekly Commit (IC surface).** `/weekly-commit` → `GET /api/plans/current` returns *her own* RECONCILED plan. Here Dana is a normal IC; no review/dispute affordances on her own work (`managerReview === null` end-to-end). Authorization is relationship-driven: she sees this because she *owns* it.
3. **My Team → Command Center.** `/manager/command-center` (`GET /api/manager/command-center`), scoped server-side to her active direct reports (R1–R6).
4. **My Team → Heatmap.** `/manager/heatmap`; drill-downs authorized on each cell's own `managerEmployeeId`.
5. **Scope isolation enforced, not just visual.** Dana cannot reach another manager's data; an IC manually hitting `/manager/command-center` gets `404`/`403`. Switching back returns her to her own IC plan with no team-data leakage.

---

## 8. States, Edge Cases & Microcopy

### 8.1 The five view-states + the no-optimistic-update model

Every RTK Query-backed view renders exactly one of five states, derived from the query result, never from local optimistic guesses.

| State | Trigger | Treatment |
|---|---|---|
| **Loading** | first fetch, no cache (`isLoading`) | skeleton matching the final layout shape (`animate-pulse`), no spinner-on-blank-page |
| **Empty** | `200` + zero meaningful rows | `EmptyState` Card: icon + headline + one-line guidance + CTA where applicable |
| **Error** | `isError` (4xx/5xx) | `ErrorState`: `Alert color="failure"` rendering `problem.safeMessage` (fallback `title`) + `traceId` (mono) + `Try again` (refetch) |
| **Partial** | success with a degraded sub-region (chiefly a `FAILED` Outlook sync inside a good plan) | main content renders fully; the degraded region shows its own inline `Alert color="warning"`. Never blocks |
| **Success** | `200` with data | the real layout |

**No-optimistic-update mutation loop:**
```
[Action button, enabled iff dto.allowedActions includes ACTION]
   │ click
   ▼
[Button → Spinner + disabled; co-located controls touching the same resource disabled;
          aria-busy="true" on the region]
   │ RTK Query resolves
   ├── success → invalidate tags → automatic refetch → UI repaints from server truth + success Toast
   └── error  → button re-enabled → ErrorState / inline Alert renders problem.safeMessage; no state change
```
Concrete rules: button enablement is `allowedActions`-driven, never inferred from state; pending = in-button `Spinner` + disabled (not a full-page overlay); co-located controls disable together (while locking, Add/Edit/Delete + the RCDO picker disable); on success the UI re-derives from the refetched DTO; the `version` token is echoed (`If-Match`) and a `409` optimistic-lock conflict renders "This plan changed in another tab. Refresh to see the latest, then try again." with a Refresh button; cache invalidation crosses surfaces (lock/dispute/reconcile invalidate `plans`, `manager-summary`, `heatmap`, `sync`).

### 8.2 Outlook sync FAILED warning + Retry UX (non-blocking, recoverable)

Sync is always downstream of a successful core mutation, so the failure surface is informational, never a stop. Two coordinated surfaces: a **one-time `Toast color="warning"`** (auto-dismiss 8s) at the moment lock/reconcile succeeds but sync returns FAILED, plus a **persistent per-item `SyncStatusBadge` chip** in the plan header.

```
┌───────────────────────────────────────────────────────────┐
│ ⚠ Calendar sync failed                                      │
│ Calendar sync failed; you can retry.        ← safeMessage   │
│ This does not affect your locked plan.      ← fixed WC copy │
│ Attempts: 1   ·   Ref: 0af7…319c            ← retryCount/trace│
│                                   [ Retry sync ]            │
└───────────────────────────────────────────────────────────┘
```

The visible failure line is **exactly `safeMessage`** — never `failureCode` (`GRAPH_FORBIDDEN`) as prose, never tokens/secrets. Retry → button `Spinner` + disabled, chip flips to "Retry queued…" → refetch. Successful retry end-state: green "Calendar synced" + `Toast` "Calendar event synced." Repeated failures stay the user-visible retryable terminal `FAILED` (no admin UI). The workflow is never blocked.

### 8.3 Validation & error microcopy by RFC-7807 code

`ErrorState`/inline `Alert` always prefers `problem.safeMessage`. When `fieldErrors[]` is present, offending commitment rows also get a red left border + per-row inline note.

| `code` | HTTP | Canonical `safeMessage` (visible) | UI placement |
|---|---|---|---|
| `EMPTY_PLAN_LOCK` | 409 | "Add at least one commitment before you lock this week's plan." | `Alert warning` above Lock; not-started shell CTA emphasized (button usually pre-disabled with same tooltip) |
| `UNLINKED_PLANNED_COMMITMENT` | 409 | "Every planned commitment must link to a Supporting Outcome before you can lock this plan." | `Alert failure` at plan top; each `fieldErrors[].field` (`commitments[3].supportingOutcomeId`) → row badge "⚠ Needs Supporting Outcome" + scroll to first offender |
| `LOCKED_BASELINE_EDIT` | 409 | "This plan is locked. Planned commitments can't be changed — record what actually happened in reconciliation instead." | only reachable via stale tab → `Toast failure` + forced refetch (baseline renders read-only) |
| `SECOND_OPEN_DISPUTE` | 409 | "There's already an open dispute on this commitment. Resolve it before opening another." | inline under Open dispute; existing dispute expanded/scrolled to |
| `IC_CANNOT_RESOLVE_DISPUTE` | 403 | "Only your manager can resolve this dispute. You can respond with a revised Supporting Outcome or add context." | defensive guard (IC never sees Resolve) → `Toast failure`; Respond form highlighted |
| `UNPLANNED_MISSING_LINK_AT_CLOSE` | 409/422 | "Link every unplanned commitment to a Supporting Outcome before you close reconciliation." | `Alert failure` above Close; offending unplanned rows badged; Close stays disabled-with-tooltip |
| `ILLEGAL_STATE_TRANSITION` | 409 | "This plan changed since you opened it. Refresh to see the latest, then try again." | generic stale/optimistic-lock guard → `Toast` + Refresh |
| `VALIDATION_ERROR` | 400/422 | per-field `fieldErrors[].message` — e.g. "Title is required.", "Title must be 255 characters or fewer.", dispute "A note is required to flag a commitment." | under the Flowbite input via `color="failure"` + `helperText` |
| forbidden/not-found (IDOR-safe) | 403/404 | "You don't have access to this, or it no longer exists." | full-page `ErrorState`; we deliberately do **not** distinguish 403 vs 404 (never reveal existence) |

Dispute-respond client guard (before submit): "Add a response or pick a new Supporting Outcome before submitting."

### 8.4 Key empty / edge states (first-class, realistic content)

- **(a) Not-started plan shell** (IC, DRAFT, zero commitments) — distinct from a generic empty card: shows the week range, the lifecycle bar at DRAFT, and an onboarding CTA: "🗓 Your week is ready to plan. Add the commitments you're making this week. Each one links to a Supporting Outcome so your work ladders up to the Rally Cry. [ + Add your first commitment ]". Lock disabled with tooltip "Add at least one commitment to lock."
- **(b) Manager with a report who hasn't locked (Sam, DRAFT)** — the row renders, never hidden: `StatusPill gray` "Not started / Draft"; review cell "—" with tooltip "Review opens once Sam locks this plan."; no Mark-reviewed/Open-dispute actions; counts still show; manager can read + `COMMENT`.
- **(c) No disputes** — an unobtrusive line, not an empty card: "No alignment flags on this plan."; in the command center, `unresolvedDisputeCount=0` simply renders no dispute chip (absence is the signal).
- **(d) Week with no commitments** — heatmap `EmptyState` "No commitments mapped for the week of Jun 1 – Jun 7. Nothing to chart yet."; command center with reports but no plans lists each report at "Not started."
- **(e) Manager-who-is-also-IC with no manager above (Dana)** — sees the toggle; on My Weekly Commit she's a normal IC (RECONCILED plan, no review/dispute affordances on her own work, `managerReview` null end-to-end, no pass-up control); on My Team she gets the command center + heatmap for her 6 reports.

---

## 9. Accessibility & Responsive

### 9.1 Accessibility

**Color is never the only signal** — every status/risk conveys meaning through **glyph + text label + color**, legible in grayscale and to color-blind users.

- **Plan/review status:** `DRAFT` ○ "Draft" · `LOCKED` 🔒 "Locked" · `RECONCILING` ↻ "Reconciling" · `RECONCILED` ✓ "Reconciled" · review pills carry their glyph + word; derived `OVERDUE` ⏰ "Review overdue."
- **Heatmap risk badges** each get a distinct glyph + short label (never a bare colored square); cell intensity is encoded by a printed numeric count **and** background tint, so density never relies on shade alone. A persistent Legend maps glyph→meaning; a high-contrast/pattern-mode toggle swaps to diagonal-hatch (red) / dotted (amber) fills without changing badge text.
- **Chess layer:** `priority` shows as text `P0/P1/P2`; `confidence` and `alignmentStatus` carry text labels.

**Keyboard navigation:**
- **`RcdoPicker`** (`role="tree"`): `↑/↓` within a level, `→/Enter` expand a Defining Objective, `←` collapse, type-ahead filter, `Enter` selects a Supporting Outcome, `Esc` closes and returns focus to the trigger. Selection announced via `aria-live`: "Selected: Win customer adoption & expansion › Cut time-to-first-locked-plan under 10 minutes."
- **Heatmap grid** (`role="grid"`): cells are `role="gridcell"` reachable by arrow keys (roving `tabindex`), `Enter`/`Space` opens the drill-down `Drawer`. Each cell's `aria-label` enumerates risk + counts: "Aisha Khan, Operational excellence, 3 commitments, risks: misaligned, needs review." Row/column headers use `rowheader`/`columnheader` + `scope`.

**Focus management:** `Modal` (lock confirm, dispute open/resolve) and `Drawer` (heatmap drill-down, report review) **trap focus**, move focus to the first interactive element/heading on open, restore to the invoking trigger on close, close on `Esc`, and carry `role="dialog"` + `aria-modal="true"` + `aria-labelledby`. Destructive/irreversible confirmations (Lock, Close reconciliation) default focus to **Cancel**.

**Dense Table/grid ARIA:** real `<th scope="col">` headers; sortable columns expose `aria-sort`; filter/pagination changes announce result counts via `aria-live="polite"` ("Showing 6 of 6 direct reports."). Loading regions set `aria-busy="true"` with SR-only "Loading…". Toasts use `role="status"` (success/info) or `role="alert"` (failure).

**Contrast & target size:** WCAG AA (≥4.5:1 body, ≥3:1 large/UI); badges never rely on hue contrast alone. Interactive targets ≥24px. Respect `prefers-reduced-motion`.

### 9.2 Responsive (desktop-first, dense data app)

- **Sub-nav (all widths):** the surface segmented control always stays; below ~640px the week selector + My-Team sub-tabs collapse into an overflow kebab `Dropdown` so the surface toggle is never lost. Breadcrumb truncates middle segments.
- **Command-center `Table`:** canonical degradation is horizontal scroll within the container (all columns preserved). Below ~768px → stacked card-per-report (avatar header, plan-state + review Badges, counts as a labeled key/value grid, wrapping risk badges). Filters collapse into a `Drawer` behind a "Filters" button. Pagination persists.
- **Heatmap grid:** desktop = true grid. Below ~768px → a per-report `Accordion` (one panel per report listing its 3 DO cells vertically with counts + risk badges — same data, linearized, never dropped). Cell drill-down opens in a `Drawer` on all widths (never reflows the grid).
- **IC plan editor / history:** narrow reading column reflows naturally; cards stack full-width; chess-layer fields move from inline row to stacked selects below ~640px; the lifecycle bar stays sticky.
- **Never:** a WC-owned global fixed header, a WC left sidebar in production, or hiding any column/cell/count to fit narrow widths (reflow or scroll instead — managers must scan all signals). At every breakpoint the Outlook FAILED warning + Retry stays reachable inline near the plan header.

---

## 10. Demo-Mode Specifics

### Persona switcher (standalone only)

`standalone/PersonaSwitcher.tsx` is a thin top-of-frame `Dropdown` (Avatar + name + role badge) — the single piece of "chrome" the demo shell adds, **tree-shaken out of the remote build**. Selecting a persona sets `X-Demo-Employee-Id` via `DemoIdentityProvider`, flips `MeDto`, re-derives `isManager`, resets RTK Query cache, and re-runs `/` landing logic. **No login screen** in demo mode.

```
┌──────────────────────────────────────────────────────────────┐
│ ST6 · Weekly Commit  [demo]     Viewing as: ▼ [👤 Dana Okafor · Manager] │
│                                  My Weekly Commit | My Team    │  ← only Dana sees "My Team"
└──────────────────────────────────────────────────────────────┘
   Persona list:
   ● Dana Okafor   · Manager   (lands /manager/command-center)
   ○ Priya Raman   · IC        (LOCKED, not overdue, sync SYNCED — happy path)
   ○ Marco Bellini · IC        (LOCKED, OVERDUE review · FAILED sync — warning/retry)
   ○ Aisha Khan    · IC        (REVIEWED_WITH_DISPUTES · 1 OPEN MISALIGNED dispute)
   ○ Tomas Novak   · IC        (REVIEWED · 1 RESOLVED dispute, full loop)
   ○ Grace Liu     · IC        (RECONCILING · mixed outcomes · carry-forward chain)
   ○ Sam Carter    · IC        (DRAFT · 1 unlinked commitment — lock-block)
```

### Seeded content for mockups (Dana + 6 reports; week of Jun 1–7, 2026; anchor Tue Jun 2)

| Report | Plan state | Review status (visual) | Risk chips | Notable |
|---|---|---|---|---|
| **R1 Priya Raman** | LOCKED | ○ Not reviewed · Due Jun 2, 5:00 PM | 3P | 3 planned, all linked (SO-1.1, SO-2.3, SO-3.2); sync SYNCED — the happy path |
| **R2 Marco Bellini** | LOCKED | 🔴 OVERDUE · was due May 29 | 3P 1U · ⚠ sync failed | seeded OVERDUE; FAILED `IC_PLANNING` sync (`GRAPH_FORBIDDEN`) → RETRY_SYNC affordance |
| **R3 Aisha Khan** | LOCKED | 🟠 Reviewed w/ disputes (SLA met) | 4P · 🟣1 misaligned · 🔵1 needs-review · ●1 dispute | 1 OPEN MISALIGNED dispute on a NEEDS_REVIEW commitment; `icResponse` null |
| **R4 Tomas Novak** | LOCKED | 🟢 Reviewed | 3P | 1 RESOLVED dispute (full OPEN→IC_RESPONDED→RESOLVED) |
| **R5 Grace Liu** | RECONCILING | 🟢 Reviewed | 3P 1U · ⛔1 blocked · ↪1 carry-forward | mixed outcomes; 1 UNPLANNED (SO-2.2); carry-forward "Draft the activation-onboarding runbook" → next week |
| **R6 Sam Carter** | DRAFT (Not locked) | — Review unavailable | 2P (1 unlinked) | 1 commitment deliberately unlinked → can't lock; no Review action |
| **Dana Okafor (self)** | RECONCILED | n/a (no manager) | fully reconciled | the manager's own complete-lifecycle exemplar |

**RCDO copy** (for commitment breadcrumbs): Rally Cry *"Become the system of record every execution-driven team trusts by end of FY26"*; DO-1 "Win customer adoption & expansion", DO-2 "Operational excellence in delivery", DO-3 "Platform reliability & trust"; SOs e.g. SO-1.2 "Cut new-workspace time-to-first-locked-plan under 10 minutes", SO-2.3 "Bring every squad to a green weekly-commit reconciliation rate above 90%", SO-3.2 "Keep p95 command-center latency under 200ms at 2,000-record scale."

Because projection rows are seeded as literal values, **Dana's command center and heatmap are populated the instant the demo opens** — overdue (Marco), misaligned + needs-review (Aisha), blocked + carry-forward (Grace), unreviewed (Marco), and full closed lifecycles (Dana's own + Tomas's resolved dispute) are all visible without opening another plan. The standalone build also exposes a **"Reset demo data"** affordance (re-runs the idempotent seed). A **negative-auth frame** is worth one mockup: an IC reaching `/manager/command-center` gets the not-authorized EmptyState (404/403 path).

---

## 11. Out of Scope for the UI

Do **not** design or imply any of the following — they are deferred or non-goals and must not appear, even as disabled stubs:

- Nested/threaded comments (comments are flat, one level only).
- RCDO admin / creation / editing (RCDO is read-only seeded reference data).
- Pass-up / escalation / multi-level leadership roll-ups.
- True live updates (WebSocket / SSE / push) — no live presence, no realtime cursors, no auto-refreshing tickers.
- Optimistic UI of any kind.
- IC access to any team data (command center or heatmap) — ICs never see "My Team."
- Unlock / amend after lock; editing a locked baseline.
- Holiday-aware SLA, user-local week timezones, per-report manager calendar events.
- SQS DLQ admin / redrive UI.
- Manager review status as a user-selectable field (it is always server-derived).
- A login screen in demo mode (persona switch only).
- Engagement surveys, pulse/sentiment, recognition/High-Fives, 360/Best-Self reviews, performance ratings, 1-on-1 workspace, manager coaching, HR analytics — all 15Five features WC does **not** replace.

---

## 12. Handoff Checklist for Claude Design

### Screens to generate first (priority order)

1. **Manager Command Center** (`/manager/command-center`, success state, Dana + 6 reports) — the highest-value, most-differentiated screen; proves the "scannable drift" thesis and the full status/risk taxonomy in one frame.
2. **RCDO Coverage Heatmap** (`/manager/heatmap`, success state) — the second signature surface; proves volume-vs-risk decoupling and the six-badge vocabulary.
3. **IC Weekly Commit Workspace — DRAFT mode** (`/weekly-commit`, Sam Carter, with the unlinked-commitment lock-block) — proves the strategy-enforcement forcing function and the chess layer.
4. **IC Workspace — RECONCILING mode** (`/weekly-commit`, Grace Liu, outcomes + unplanned + carry-forward) — proves the reconciliation + carry-forward cycle and baseline immutability.
5. **RCDO Supporting-Outcome picker** (`RcdoPicker` modal, open over a DRAFT commitment) — proves the core alignment interaction.
6. **Report review Drawer + Dispute panel** (over the command center, Aisha, OPEN MISALIGNED dispute) — proves the manager review + dispute loop.
7. **Heatmap cell drill-down Drawer** (Grace × DO-2, SO breakdown) — proves diagnostic→action navigation.
8. **Plan History — read-only planned-vs-actual** (`/weekly-commit/history/:planId`, Priya) — proves the two-column story + carry-forward lineage.
9. **State variants pack** — for the top 3 screens: LoadingState skeleton, EmptyState, ErrorState (with a real `safeMessage`), and the Outlook FAILED PartialState warning.

### Ready-to-paste design-tool prompts (highest-value screens)

**Prompt A — Manager Command Center**
> Design an enterprise B2B SaaS "Alignment Command Center" screen for a weekly-execution tool. Calm, dense, data-forward, trustworthy — Linear/Vercel restraint, never playful, no gamification. Render only a content area + a top horizontal sub-nav segmented control ("My Weekly Commit" | "My Team" active) with a "Command Center · Heatmap" secondary toggle and a right-aligned week selector "Week of Jun 1–7, 2026"; do NOT draw a global app top-bar or left rail (it lives in a host shell). Below the sub-nav: a page title "Alignment Command Center", an at-a-glance summary card ("6 reports · 1 review OVERDUE · 2 with open disputes · 1 reconciling · 1 not locked · 1 reviewed clean"), a filter bar of dropdown chips (Person, Plan state, Review state, Defining Objective, Supporting Outcome, Priority, Work type, Alignment) with two active removable chips, then a dense data table with one row per direct report. Columns: Report (avatar initials + name), Plan state badge, Review status (with a secondary due/reviewed line), Reconcile, Risk chips, and a right-side action button. Rows: "Priya Raman" LOCKED / gray "Not reviewed · Due Jun 2, 5:00 PM" / "3P" / [Review]; "Marco Bellini" LOCKED / red bold "OVERDUE · was due May 29" + a small "sync failed" warning / "3P 1U" / [Review]; "Aisha Khan" LOCKED / amber "Reviewed w/ disputes (SLA met)" / chips "4P, purple 1 misaligned, blue 1 needs-review, rose 1 dispute" / [Open]; "Tomas Novak" LOCKED / green "Reviewed" / "3P" / [Open]; "Grace Liu" RECONCILING (amber) / green "Reviewed" + "In progress · 1 carry-fwd" / chips "3P 1U, red 1 blocked, amber 1 carry-forward" / [Open]; "Sam Carter" DRAFT/gray "Not started" / "— (not locked)" / "2P" / disabled. Use semantic color only on badges (red=overdue/blocked, amber=disputes/carry-forward/reconciling, green=reviewed/done, gray=neutral, purple=misaligned, blue=needs-review). Every badge has an icon + text label. Flat cards, hairline table dividers, ~44px rows, Inter, text-sm body. Pagination footer "1–6 of 6, 25/page".

**Prompt B — RCDO Coverage Heatmap**
> Design an enterprise "RCDO Coverage Heatmap" screen (same calm, dense, no-gamification aesthetic and the same content-area-only framing with a top sub-nav: "My Team" active, "Heatmap" sub-tab selected, week "Jun 1–7, 2026"). Show a Rally Cry caption line and a filter chip row. Center: a matrix grid — rows are 6 direct reports (Priya, Marco, Aisha, Tomas, Grace, Sam), columns are 3 Defining Objectives ("DO-1 Win customer adoption", "DO-2 Operational excellence", "DO-3 Platform reliability"), plus a right "Row total" column. Each cell shows a large commitment-count numeral with a NEUTRAL slate background tint by volume (0 = white dashed-border "no coverage" gap, 1 = faint, 2–3 = light, 4+ = slate with a thin amber border + "high"), and BELOW the count a wrap row of small icon+text risk badges. Cells: Marco DO-1 "3" with amber "Overdue review" + gray "Unreviewed"; Marco DO-2 empty gap; Aisha DO-1 "1" amber "Needs review"; Aisha DO-3 "1" red "Misaligned"; Grace DO-1 "4" amber "Carry-forward"; Grace DO-2 "3" red "Blocked"; Grace DO-3 empty gap; Sam DO-1 empty gap, Sam DO-2 "1" muted "no risk · draft". Critically: volume tint (neutral slate) and risk (colored badges) are visually SEPARATE — a high-count cell is not automatically alarming. Below: a muted collapsed "Dana Okafor (you · self plan)" row and a legend mapping the 6 risk badges + the volume buckets, ending "Volume = load (neutral). Risk = explicit badges. No single health score." Flat, Inter, semantic color only, every badge icon+text.

**Prompt C — IC Weekly Commit Workspace, DRAFT with lock-block**
> Design an enterprise IC "My Weekly Commit" workspace in DRAFT state, content-area only with a top sub-nav (single static "My Weekly Commit" label — this user is not a manager) and a "Week of Jun 1–7, 2026" selector. Top: a flat plan-header card with the week range, a gray "DRAFT" status pill, "2 planned · 0 unplanned", a 4-node forward-only lifecycle stepper (Draft active → Locked → Reconciling → Reconciled), and a primary "Lock plan" button rendered DISABLED with a tooltip "1 of 2 commitments isn't linked to a Supporting Outcome." Body: a stacked list of two commitment cards. Card 1 "Ship workspace onboarding telemetry events" with quiet metadata chips [P1] [Strategic] [Confidence: High 3-segment bar] [Self: Aligned] and a green-checked RCDO breadcrumb "Become the system of record › Win customer adoption › SO-1.2 Cut new-workspace time-to-first-locked-plan under 10 minutes" + [Change]. Card 2 "Pair with support on the Q2 escalation backlog" with chips [P2] [Maintenance] [Confidence: Medium] [Self: Needs-Review] and a LOUD red alert strip "⚠ No Supporting Outcome linked — required before you can lock" + [Link Supporting Outcome] button, plus a red left-border accent on the card. An "+ Add commitment" affordance above the list. NO chess/game imagery — the priority/work-type/confidence are plain enterprise metadata chips and a 3-segment signal bar, never medals or stars. Calm, flat, Inter, text-sm.

**Prompt D — RCDO Supporting-Outcome picker (modal)**
> Design a modal "Link a Supporting Outcome" picker for an enterprise tool, overlaid on a dimmed plan editor. The modal has a search input (value "onboarding"), then a 3-level cascade tree: level 1 a single expanded Rally Cry "🎯 Become the system of record every execution team trusts (FY26)"; level 2 three Defining Objectives, the first ("Win customer adoption & expansion") expanded and the other two ("Operational excellence in delivery", "Platform reliability & trust") collapsed; level 3 three selectable radio-leaf Supporting Outcomes under the first — "SO-1.1 Lift activated-team weekly-active rate to 70%", "SO-1.2 Cut new-workspace time-to-first-locked-plan under 10 minutes" (SELECTED, with a checkmark), "SO-1.3 Reach 120% net revenue retention on strategic accounts". A footer pins a live selection breadcrumb "Selected: Win customer adoption › SO-1.2 Cut time-to-first-locked…" with [Cancel] and a primary [Link outcome] button. Only the leaf Supporting Outcome is selectable; Rally Cry and Defining Objectives are navigational. Calm enterprise styling, Inter, blue accent only on the primary button and selected radio.

**Prompt E — Manager report-review Drawer + Dispute panel**
> Design a right-side slide-over Drawer (~640px) over a dimmed command-center table, reviewing a direct report's locked plan. Header: avatar "AK" + "Aisha Khan — Week of Jun 1–7, 2026", a blue "LOCKED" pill, an amber "Reviewed with disputes (SLA met)" pill, "Locked Jun 1, 9:02 AM", and a "Mark reviewed" button. Below: a "Commitments (4 planned)" list of read-only cards. First card "Ship squad reconciliation-rate dashboard" — P1 · Strategic, RCDO line "Operational excellence › Green weekly-commit reconciliation >90% (SO-2.3)", "Alignment: NEEDS_REVIEW · Confidence: HIGH", and a red "OPEN DISPUTE — Misaligned" strip with [View dispute] [Comment]. Second card "Cut onboarding time-to-first-locked-plan" — P0 · Strategic, RCDO "Win adoption › Time-to-first-locked-plan < 10 min (SO-1.2)", "Alignment: ALIGNED", with [Flag alignment] [Comment]. Then a nested dispute panel showing flag "MISALIGNED" badge, status "OPEN", a 3-step stepper (OPEN → IC_RESPONDED → RESOLVED, first active), the manager note "This maps to SO-2.3 but the dashboard work is really platform reliability — SO-3.2 is correct. Please revise.", an empty "IC response — awaiting Aisha" slot, and a manager-only [Resolve dispute] button (no Respond button — that's the IC's surface). Footer: a flat plan-comments thread. Enterprise, calm, semantic color only, icon+text badges, Inter.

**Prompt F — IC Plan History, read-only planned-vs-actual**
> Design a read-only "Plan History" detail screen for an enterprise weekly tool, content-area only. Breadcrumb "My Weekly Commit › History › Jun 1–7, 2026" with [‹ Prev wk] [Next wk ›], a green "RECONCILED" pill, "Locked Jun 1 · Reconciled Jun 5", owner "Priya Raman". Left: a summary card (Planned 3, Unplanned 1; Outcomes: Completed 2, Carried fwd 1; Review: REVIEWED by Dana Okafor, note "Solid week."; Calendar: ✓ Planning synced, ✓ Recon synced). Right: a planned-vs-actual list where each commitment is a card split into a LEFT "Planned baseline (frozen)" column and a RIGHT "Actual outcome" column. Card 1: left "Lift activation WAU dashboard, P0 · Strategic · High · Aligned, RC ▸ DO-1 ▸ SO-1.1"; right green "✔ COMPLETED" + note "Shipped Tue; 71% WAU hit." Card 2: left "Reconciliation runbook v2, P1 · Strategic · Med · Needs-Review, RC ▸ DO-2 ▸ SO-2.3"; right blue "↻ CARRIED-FORWARD → went to Jun 8–14 [view successor]" + a lineage strip "← in from May 25–31 [view] · → out to Jun 8–14 [view]". Non-completed outcome cards get a thin colored left-border accent on the right column so they pop on a scan. Calm, flat, dense, Inter, semantic color only.

---

**Source files referenced** (absolute): `/Users/dreddy/Documents/GauntletAI/Projects/ST6/ARCHITECTURE.md` (Exec summary, §1, §3, §7, §9, Appendix A/B/C/E/F), `/Users/dreddy/Documents/GauntletAI/Projects/ST6/docs/planning/USER_FLOWS.md`, `/Users/dreddy/Documents/GauntletAI/Projects/ST6/docs/planning/15five-research.md`.
