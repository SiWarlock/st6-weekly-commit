# Styling deviations punch-list — mockup vs as-built (user QA, 2026-06-04)

> **Source:** user real-browser QA against the Cadence mockup UI kits. 8 screenshots compared (command center mockup vs actual; IC "My Weekly Commit" mockup vs actual). This is the authoritative fix-list for a frontend visual-fidelity round. **All render-only / standalone-demo-shell — no contract/enum change.**
>
> **Severity scale:** **S1** = structural/missing-whole-element · **S2** = significant fidelity gap · **S3** = polish.

> **⚠️ THIS LIST IS A STARTING POINT, NOT EXHAUSTIVE (user directive). THE MOCKUP IS CANON.** The fix-pair MUST do a **direct mockup-vs-real comparison in the gstack browser** — load BOTH the canon mockup (`docs/design/cadence-design-system/ui_kits/weekly-commit/index.html`, served locally) AND the running standalone app, put them **side-by-side per surface** (command center, IC plan view, heatmap, drawers, both themes), and **fix EVERY deviation found — including ones not enumerated below.** Treat any divergence from the mockup as a defect to close. (Example the user flagged: the **filters** render completely differently from the canon — captured in §B.2, but illustrative of why the canon-compare must be exhaustive, not list-bounded.)

> **🎨 AUTHORITATIVE MOCKUP REFERENCE (user directive — reference these directly when fixing each deviation):** the full composed-app UI kit lives at **`docs/design/cadence-design-system/ui_kits/weekly-commit/`** — a complete React mockup. Match against the actual mockup components, not just this text list:
> - **`app.jsx`** → the **app-shell** (top app-bar + primary nav + breadcrumbs) — the **§A S1** reference.
> - **`CommandCenter.jsx`** → command-center fidelity (summary strip, labeled risk chips, filter row, table, avatars/timestamps) — **§B**.
> - **`WeeklyPlanView.jsx`** + **`CommitmentCard.jsx`** → IC page + card (header card, chip/SO order, description line, gold button) — **§C**.
> - **`atoms.jsx`** (chip/badge/meter atoms), **`overlays.jsx`** (drawers), **`kit.css`** / **`colors_and_type.css`** / **`components.css`** (tokens/styles), **`index.html`** (rendered preview), **`data.jsx`** (fixture shapes).
> Plus per-component previews under `docs/design/cadence-design-system/preview/component-*.html` (riskbadges, statuspills, commitmentcard, chesslayer, sync). The fix must stay token-native (no hex/`.wc-*`) — read the mockup for *layout/structure/composition*, map its visuals onto the existing Cadence tokens.

---

## A. APP SHELL / GLOBAL CHROME — **S1, the biggest gap** (affects every surface)

**The mockup shows a full app chrome that the standalone build does NOT render:**
- **Top app bar** (full-width): purple rounded-square ✓ logo + **"ST6 Weekly Commit"** (bold) + **"Demo"** pill · (center) 📅 **"Week of Jun 1–7, 2026"** · (right) avatar circle + **identity** ("DO Dana Okafor / Manager", "PR Priya Raman / IC") with a dropdown chevron.
- **Primary nav row**: segmented **"My Weekly Commit | My Team"** + segmented **"Command Center | Heatmap"** · (right) **"📅 Week of Jun 1–7 ▾"** picker.
- **Breadcrumb**: "My Team › Command Center" / "My Weekly Commit › Week of Jun 1–7, 2026".

**As-built instead:** only a thin top-right **"PERSONA: [Morgan Lee (Manager) ▾]  [Light mode]"** — the bare standalone switcher chrome. No app-bar, no nav, no breadcrumb.

**Root cause (NOT a bug — expected MFE behavior):** per §7, the remote (`WeeklyCommitApp`) renders only the route subtree; the **host** owns `BrowserRouter` + nav + identity chrome. The mockup depicts the **composed host+remote** app. In standalone mode there's no host, so `StandaloneShell` only renders the PersonaSwitcher + ThemeToggle.

**Fix:** build a **demo app-shell into the STANDALONE shell** (`standalone/StandaloneShell.tsx`) that mirrors the mockup chrome — app-bar (logo + product + Demo + week + identity) + primary nav (My-Weekly-Commit/My-Team + Command-Center/Heatmap tabs + week picker) + breadcrumbs. **Standalone-only, tree-shaken from the exposed remote** (REQ-I-008 preserved — the `surfaceSkin`/build-grep guards must still pass). Fold the PersonaSwitcher into the app-bar's identity slot (replace the raw "PERSONA" dropdown look); keep the light-mode toggle, styled into the bar. The nav tabs drive the existing routes (`/weekly-commit`, `/manager/command-center`, `/manager/heatmap`) — wire to the existing `AppRoutes`.

---

## B. MANAGER COMMAND CENTER — **S2**

1. **"At a glance" summary strip — MISSING (S1).** Mockup: `6 reports | 1 review overdue | 2 open disputes | 1 reconciling | 1 not locked | 1 reviewed clean` (colored counts) under the title. **This is the deferred SLA strip.** → see Decision D-1 (backend `summary` dependency).
2. **Filters — wrong treatment (S2).** Mockup: a compact **"FILTERS [Person ▾] [Plan state ▾] [Review state ▾] [Defining objective ▾] [Priority ▾] [Work type ▾] … Clear all"** chip row + an **active-filter chip** ("Review: Overdue ✕") + **"Showing 6 of 6 reports"**. As-built: a heavy 2-row **grid of big labeled `<select>` dropdowns** (Week of / Person / Plan state / Review state / Defining objective / Priority / Work type / Alignment status, all "All"). → Rebuild as the compact dropdown-chip row + Clear-all + active-filter chips + result count.
3. **Risk chips — generic, not labeled (S2).** Mockup: rich labeled pills — **"3 P" "0 U"** (planned/unplanned counts) + **"△ 1 misaligned"** (violet) · **"◎ 1 needs-review"** (blue) · **"⚑ 1 dispute"** (red) · **"○ 1 resolved"** · **"⊘ 1 blocked"** (red) · **"→ 1 carry-fwd"** (gold) · **"○ 1 unlinked"** (gold); **only non-zero chips shown** (+ always the P/U counts). As-built: generic **icon + number, NO label**, all shown even at 0 ("△ 0 | ❓ 1 | ⊘ 0 | → 0 | ⚑ 0"). → Add labels + the P/U count pills + the tone colors; hide zero-count chips.
4. **Avatars — missing (S2).** Mockup: report rows lead with a colored initials avatar (PR/MB/AK/TN/GL/SC). As-built: name only.
5. **Review-status timestamps — missing (S2).** Mockup: badge **+ a sub-line** ("Due Jun 2, 5:00 PM" / "Was due May 29, 5:00 PM" / "SLA met · Jun 1, 3:12 PM" / "Jun 1, 4:40 PM"). As-built: badge only.
6. **RECONCILE column — missing (S2).** Mockup has a RECONCILE column ("In progress · 1 carry-fwd" / "—"). As-built: absent.
7. **Week-range pager — wrong (S2).** Mockup: a **"‹ Jun 1 – Jun 7, 2026 ▾ ›"** pill + **"Updated 2 min ago ↻"**. As-built: a raw date input in the filter grid; no pager, no updated/refresh affordance.
8. **Section header + sort (S3).** Mockup: **"DIRECT REPORTS — WEEK OF JUN 1–7, 2026"** + **"Sort: Week ▼ · Name ▲"**. As-built: none.
9. **Action button (S3).** Mockup: **"Review ›"** (not-reviewed) vs **"Open ›"** (reviewed), with chevron. As-built: "Review" only.
10. **Footer legend (S3).** Mockup: **"P = planned · U = unplanned"** (left) + **"25 / page"** (right). As-built: absent.
11. **Title/copy (S3).** Mockup: **"Alignment Command Center"** + "Your direct reports' weekly alignment at a glance." As-built: "Command center" + "Direct-report alignment at a glance."

---

## C. IC PAGE ("My Weekly Commit") — **S2**

1. **Title + breadcrumb (S3).** Mockup: **"My Weekly Commit"** + breadcrumb "My Weekly Commit › Week of Jun 1–7, 2026". As-built: "Weekly commitments" + raw ISO "2026-06-01 – 2026-06-07", no breadcrumb.
2. **Plan-header card (S2).** Mockup wraps week + stepper + actions in a **bordered header card**: "Week of Jun 1–7, 2026 [🔒 Locked] · 3 planned · 0 unplanned · [👁 Not reviewed]" (right: identity) + 4-node stepper + **"+ Add unplanned work"** + **"🗒 Start reconciliation" (GOLD primary)**. As-built: looser framing, **"Start reconciliation" is PURPLE not gold**, button order differs, no in-card identity, the planned/unplanned/review summary line is less prominent.
3. **Commitment card — description missing + order reversed (S2).** Mockup card: lock icon + **title** + a **plain description line** ("Ship the weekly-active board GTM reviews.") + **chips row** (P0 · ✦ Strategic · ▮▮▮ High · ● Aligned) + **SO-breadcrumb box BELOW**. As-built: lock + title + the **SO-breadcrumb box is ABOVE the chips (reversed)**, **no plain description line**, and the SO objective text renders in **monospace** (should be regular weight). → Add the description line; put chips above the SO box; drop the mono font on the SO/objective text.

---

## Decisions for the user

- **D-1 (summary strip / SLA strip):** the mockup's "At a glance" strip is the SLA strip I'd deferred. For the **demo/standalone** it can be **computed client-side from the MSW-loaded rows** (no backend dep) — gives full mockup fidelity now. The **production-correct** version still wants the backend §9 `summary` field (accurate under real server-pagination). **Recommend: build the client-computed strip now for demo fidelity; queue the backend `summary` field as the production follow-up.** (Confirm.)
- **D-2 (scope):** this is a real visual-fidelity round, not a 1-slice nit pass — realistically **~2–4 slices** (app-shell · command-center fidelity · IC card · final QA). Sequence it WITH the stateful-MSW (9.15) reactivation. (Confirm go.)

## Sequencing note
All render-only / standalone-demo-shell; no enum/Appendix-A change. REQ-I-008 must stay green (the demo shell is standalone-only, tree-shaken from the remote). Pairs with the stateful-MSW build (9.15) — both are the demo-fidelity reactivation of the frontend track. Final gstack `/connect-chrome` QA re-checks every surface against these mockups + runs the live disputes loop.

---

## Decisions resolved (2026-06-04)

> **D-1 / D-2 (original, user-approved 2026-06-04 via the lead):** D-1 = build the client-computed "At a glance" summary strip now for demo fidelity + queue the backend §9 `summary` field as the production follow-up → **shipped (ST.8b)**. D-2 = the ~2–4-slice scope is a go → **realized as ST.8a (app-shell) · ST.8b + ST.8b-2 (command-center) · ST.8c (IC card) · ST.8d (canon-compare QA gate)**, all shipped; 9.15 (stateful MSW) shipped alongside.

> **⚠️ Lead away-mode calls (2026-06-04 — user delegated these to the lead while away; AskUserQuestion off the table; documented here for the user's review/redirect on return).** These resolve the **ST.8d canon-compare findings** — substantial deviations on two surfaces (heatmap, review drawer) that were NOT enumerated in §A/§B/§C above but surfaced under the user's "mockup-is-canon + NON-EXHAUSTIVE" directive:

- **Decision 1 — ST.8e: GO.** Close the **heatmap (~8 gaps)** + **review-drawer (~5 gaps)** canon **visual** deviations as a new slice **ST.8e**, including the deterministic bits (per-cell **volume bars**, the **Row-total column**, **no-coverage 0-cell** styling — all computable from the existing MSW counts; demo-only, **no backend dep**). Render-only / token-native / REQ-I-008 green otherwise. *Rationale: the directive is emphatic + general about canon fidelity, these are real demo surfaces, and it's moderate (not large) effort.*
  - **CARVE-OUT:** **KEEP** the existing inline per-commitment flag UX in the review drawer (functional + passed the disputes QA) rather than reworking to the canon's `FlagModal` — that's an **interaction-pattern preference, not a visual-fidelity defect**. Documented divergence.
  - **BOUND IT:** if any single gap balloons into real structural/feature work beyond a reasonable fidelity fix, **DEFER that item + flag the lead** (no silent scope expansion).
- **Decision 2 — command-center filters: KEEP the 7th (Alignment-status) chip.** The canon shows 6 (omits it); a canon-omission of a **working** filter ≠ remove functionality. "Mockup is canon" governs **visual fidelity**, not feature removal; the filter styling already matches canon (ST.8b-2). No action.
- **Decision 3 — gold/green button a11y: dark ink (not white) on the amber (Start-reconciliation) + green (Close-week) solid tones, AA-verified in BOTH themes.** Folded into ST.8d. *Rationale: accessibility is a non-negotiable best-practice (the user wants best-practices).* If the canon mockup itself uses white-on-tone (non-AA), this is a **deliberate documented a11y deviation** from canon.

> **2 env findings → Carry-forward (no user action):** **F1** — the standalone demo needs a gitignored `.env.local` with `VITE_AUTH_MODE=demo` (inline `yarn dev` env doesn't reach Vite → `prepareHeaders` throws → the app hangs); documented in `.env.example` (ST.8d). **F2** — a 9.15 cold-install residual: the SW-control timeout backstop can fire before SW control in a slow fresh automated browser → the first `/api/me` bypasses the worker + never retries → `/` (RootRedirect) hangs; `pushState`-to-route workaround; the proper fix (retry-the-first-query-on-`controllerchange` / reload-on-cold-install) refines wc-web LESSONS §22. Both **demo/headless-only** (the remote doesn't use MSW).
