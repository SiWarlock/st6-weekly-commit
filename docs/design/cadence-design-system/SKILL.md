---
name: cadence-design
description: Use this skill to generate well-branded interfaces and assets for the ST6 Weekly Commit Module (Cadence design system), either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping — a Linear.app dark aesthetic applied to a strategy-enforced weekly-alignment enterprise tool.
user-invocable: true
---

Read the `README.md` file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets
out and create static HTML files for the user to view. If working on production code,
you can copy assets and read the rules here to become an expert in designing with this
brand.

Key files:
- `README.md` — product context, content fundamentals, visual foundations, iconography, index.
- `colors_and_type.css` — design tokens (Linear-dark palette, six-tone status taxonomy, type, spacing, radii, elevation, motion).
- `components.css` — token-driven atoms (badges, status pills, chess-layer chips, buttons, cards, breadcrumb).
- `assets/icons.js` — Heroicons-family outline icon set + `wcIcon()` helper.
- `preview/*.html` — design-system specimen cards.
- `ui_kits/weekly-commit/` — interactive recreation of the IC workspace, manager command center, and RCDO heatmap (its own README explains the parts).

Hard rules to honor:
- Dark, dense, calm enterprise surface. Color carries **status meaning only**; indigo
  `#5E6AD2` is the single interactive brand accent and never rides on status.
- **No gamification** — the prioritization metadata is quiet enterprise metadata; never
  use chess/game/trophy/star/streak imagery, and never surface the term "chess layer".
- Sentence case; calm, accountable copy that explains why a control is disabled; errors
  render the server's `safeMessage` verbatim. No emoji in product UI.

If the user invokes this skill without any other guidance, ask them what they want to
build or design, ask a few questions, and act as an expert designer who outputs HTML
artifacts _or_ production code, depending on the need.
