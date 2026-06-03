# UI Kit — ST6 Weekly Commit

A high-fidelity, interactive recreation of the **Weekly Commit (WC)** micro-frontend
in the Linear-dark visual language. It is a faithful build of the layouts,
microcopy, status taxonomy, and seed fixtures in `uploads/UI_UX_SPEC.md` — not an
invented design. Functionality is mocked (no real RTK Query / server); the goal is
pixel- and interaction-fidelity for the visible UI.

## Run it
Open `index.html`. It boots into **standalone demo mode** (the `src/standalone/`
shell): a thin demo top bar + a **persona switcher**. In production this chrome is
tree-shaken out and the PA host owns global nav — WC renders only its content +
sub-nav.

## Try these flows
- **Persona switcher** (top-right): switch between **Dana Okafor** (manager + IC) and
  her 6 reports. Managers land on **My Team**; ICs see only **My Weekly Commit**.
- **IC workspace** — view as **Sam Carter** (DRAFT): one commitment is unlinked, so
  **Lock plan** is disabled with a tooltip; click **Link Supporting Outcome** to open
  the searchable RCDO picker, link a leaf, and watch Lock enable. Confirm the
  irreversible **Lock** modal.
- **Reconciliation** — view as **Grace Liu** (RECONCILING): planned baseline is locked
  read-only; set per-commitment outcomes; see the carry-forward chain and a violet
  unplanned card.
- **Dispute response** — view as **Aisha Khan** (LOCKED): the open MISALIGNED dispute
  surfaces a response panel (the IC can revise the SO / add rationale, never resolve).
- **Manager Command Center** — as Dana: the 6-report roll-up with the OVERDUE red
  signal (Marco), dispute chips (Aisha), reconciling (Grace), and "Not started" (Sam).
  Click any row to open the **review Drawer** (Mark reviewed, Flag alignment, Comment,
  Manager note).
- **RCDO Heatmap** — as Dana: reports × Defining-Objectives grid; volume fill (load) is
  decoupled from explicit risk badges. Click a non-empty cell for the drill-down Drawer.

## Files
| File | What |
|---|---|
| `index.html` | Demo shell entry — loads React/Babel + all parts in order. |
| `app.jsx` | Standalone shell: demo top bar, persona switcher, WC sub-nav, routing, toasts. |
| `data.jsx` | Seed fixtures — RCDO tree, personas, plans, command-center rows, heatmap cells, drill-downs. |
| `atoms.jsx` | `Icon`, `Avatar`, `Badge`/status pills, `PriorityTag`, `WorkTypeTag`, `ConfidenceMeter`, `AlignmentChip`, `RiskBadge`, `RcdoBreadcrumb`, `Btn` + enum→pill maps. |
| `CommitmentCard.jsx` | The core molecule — modes draft / locked / reconciling / readOnly + reconciliation control + dispute strip. |
| `overlays.jsx` | `RcdoPicker`, `LockModal`, `MarkReviewedModal`, `FlagModal`, `Toasts`. |
| `WeeklyPlanView.jsx` | IC workspace (§6.1) — lifecycle bar + mode-switched body + sync warning. |
| `CommandCenter.jsx` | Manager roll-up (§6.3) — at-a-glance strip, filters, dense table, review Drawer. |
| `Heatmap.jsx` | RCDO coverage grid (§6.4) — volume + risk cells, legend, drill-down Drawer. |
| `colors_and_type.css`, `components.css`, `kit.css` | Tokens, shared atoms, kit layout. |
| `assets/icons.js` | Heroicons-family outline icon set. |

## Notes / fidelity caveats
- Components share scope via `window` (each Babel `<script>` is isolated); hooks are
  re-declared per file. This is a demo pattern, not production architecture.
- State transitions (lock, start/close reconciliation, link, outcome) mutate local
  React state to show the *visible* result — there is no server, optimistic-update
  model, `allowedActions[]` wiring, or RTK Query cache invalidation behind them.
- Icons are authored in the Heroicons style (see root README ICONOGRAPHY); swap in the
  real `react-icons/hi` package for pixel-exact production glyphs.
