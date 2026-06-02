# LESSONS.md — ST6 Weekly Commit Module (frontend)

> Full prose for every lesson logged during work in `apps/wc-web/`. The compact index lives in `apps/wc-web/CLAUDE.md` "Lessons logged" table.
>
> **Lesson numbers are stable IDs.** New lessons get the next sequential number. Numbers may be referenced from code comments, commit messages, and cross-references between lessons. **Don't reorder; don't reuse a deleted number's slot.**
>
> **Lessons start at §1.** Each code area has its own lesson sequence — lessons don't carry across code areas.

---

## Lesson format

```markdown
## <a id="N"></a>N. <Short topic> — <one-line rule>

**Date:** YYYY-MM-DD.
**Source slice:** <slice-id or commit hash>.

<2-5 paragraphs explaining: what was discovered, why it matters, how to
apply the rule, what edge cases are still open. Cite file:line references
where applicable.>

**Rule:** <one-sentence summary, same as the heading subtitle>.
```

---

## <a id="1"></a>1. JS monorepo-root toolchain pin — pin Yarn via Corepack `packageManager`, use the node-modules linker, commit `yarn.lock`, and treat `yarn install --immutable` as the CI idempotency contract

**Date:** 2026-06-02.
**Source slice:** 0.1 (monorepo-root).

Task 0.1 stood up the JS monorepo root (root `package.json` + `nx.json` + `apps/*` Yarn Workspaces). The reproducible-toolchain decisions that every later JS slice (wc-web 0.6, wc-e2e, the 0.8 CI workflow) inherits:

- **Yarn version is pinned via the `packageManager` field + Corepack**, not via a committed `.yarn/releases/*` binary. The dev box had no global `yarn` but Corepack 0.30.0 was present and activates `yarn@4.5.3` from `packageManager: "yarn@4.5.3"`. Because there's no committed release binary and we use the **node-modules linker** (`.yarnrc.yml`: `nodeLinker: node-modules`, no zero-installs), the entire `.yarn/` dir is safe to `.gitignore` wholesale — there's nothing in it that must be tracked.
- **`yarn.lock` is committed and NOT git-ignored** — it is the reproducible-install contract. `nx` is pinned by caret (`nx@^20`, resolved to 20.8.4) and locked by `yarn.lock`.
- **`yarn install --immutable` is the canonical idempotency / drift check** (Berry's `YN0028` fails the build if the lockfile *would* change). Use `--immutable` in CI and in any "no lockfile drift" verification gate — it's stronger and more CI-accurate than re-running a plain `yarn install` and diffing.
- **`engines.node` is documentary, not enforced, during local dev.** We pin Node 20 LTS in `engines` (`">=20"`) + `.nvmrc` (`20`), but do NOT set `engines-strict` — the dev box runs Node 22 and a hard fail would block local installs. CI (0.8) is responsible for explicitly pinning Node 20.
- **Nx stays assessment-light (ASM-001 / REQ-I-008):** no PA-shell plugins/targets, no global routing / LogRocket / Loki ownership in `nx.json`. Nx discovers `wc-web` + `wc-e2e` as package-based projects via their `package.json`; the Gradle-only `apps/wc-api` (no `package.json`) is correctly skipped by the `apps/*` glob.

Workspace-verification gates live in `scripts/verify-workspace.sh` (re-runnable, reused by 0.8 CI): install-clean, `nx show projects`, `--immutable` idempotency, malformed-member-fails-fast, and exact-membership (`yarn workspaces list --json` resolving to exactly root + wc-web + wc-e2e).

**Rule:** Pin Yarn via Corepack `packageManager`, use the node-modules linker, commit `yarn.lock`, and treat `yarn install --immutable` as the CI idempotency contract.

---

## <a id="2"></a>2. Shell launcher footgun — resolve executables with `type -P`, not `command -v`, when a shell function shares the binary's name

**Date:** 2026-06-02.
**Source slice:** 0.1 (monorepo-root).

While writing `scripts/verify-workspace.sh`, a "command not found" failure traced to a non-obvious Bash behavior: when a **shell function named `yarn`** exists in the environment, `command -v yarn` reports the *function* (it resolves functions, aliases, and builtins, not just executables). The script then branched as if a real `yarn` executable was on `PATH` and invoked `command yarn ...`, which bypasses the function and finds no executable → "command not found".

The fix is to resolve the launcher with **`type -P yarn`**, which returns a path **only for a disk executable** (ignoring functions/aliases/builtins). The verification gate now picks `yarn` if `type -P yarn` finds it, else falls back to `corepack yarn`, and binds the launcher per-case rather than assuming the bare name is callable.

This generalizes to **any shell gate / CI script** that probes for a tool that might be shadowed by a wrapper function (common with version-manager shims and Corepack setups). It is directly relevant to the 0.8 CI workflow, which reuses this script. (Cross-area note: if a future infra/CI script hits the same class of bug, reference this lesson — the rule is toolchain-agnostic.)

**Rule:** Resolve executables with `type -P`, not `command -v`, when a shell function may share the binary's name.

---

## <a id="3"></a>3. Multi-theme via CSS-vars → Tailwind theme + `[data-theme]` flip — bind design tokens into the Tailwind theme as `var(--…)`, never author your own Tailwind `dark:` utilities

**Date:** 2026-06-02.
**Source slice:** 0.6/ST.1/ST.2 (brief 006 — styling foundation).

The Cadence design system ships as CSS custom properties (`docs/design/cadence-design-system/colors_and_type.css`), but the stack mandate is Tailwind utilities + Flowbite React (no CSS Modules/styled-components). The reconciliation that became the project's **canonical multi-theme pattern** (user-approved Fork 1 = approach **A**, Tailwind/Flowbite-native):

- **One CSS-var token layer is the single source of truth.** `src/styles/theme.css` holds the token blocks under `:root,[data-theme="dark"]` (the dark default) + a complete `[data-theme="light"]` override; `tailwind.config.ts` `theme.extend` maps **every** semantic scale (`colors` surface/ink/border/brand/`tone-*`, plus `spacing`/`borderRadius`/`fontFamily`/`fontSize`/`boxShadow`/`transitionDuration`/`transitionTimingFunction`) to `var(--…)` — **never a hardcoded hex**. A unit test walks every extended leaf and asserts `^var(--…)$` so a future hardcode regresses loudly.
- **Theme switching is a `[data-theme]` attribute flip, not Tailwind's `dark:` variant.** Tailwind's `dark:` is a single binary axis and fights a dark-default + light-toggle model. We author **zero** `dark:` utilities of our own; flipping `data-theme` on the root re-points every utility through the vars. `brand-600` (`#5E6AD2`) is declared theme-stable across both blocks (Fork 3 — brand identity doesn't change with theme).
- **Theme preference resolution + persistence is `localStorage` → `prefers-color-scheme` → dark**, set on `documentElement[data-theme]` by a `ThemeProvider`, with a pre-paint inline resolver in `index.html` to avoid FOUC and a declarative `@media (prefers-reduced-motion: reduce)` rule (not a JS-written inert attribute).
- **The token-var stylesheet is the one approved exception to forbidden-pattern #3** (see `CLAUDE.md`): it carries token vars + `@tailwind` directives + a var-driven `body{}` base rule + the reduced-motion rule — **no `.wc-*` component classes, no second stylesheet**. Everything visual otherwise goes through Tailwind utilities + the Flowbite theme. Confirmed in-slice that nothing beyond the token file needed CSS.

**Rule:** Bind design tokens into the Tailwind theme as `var(--…)` and switch themes by flipping a `[data-theme]` attribute; never author your own Tailwind `dark:` utilities, and keep CSS to the single token-var stylesheet.

---

## <a id="4"></a>4. flowbite-react 0.10.2 theming — skin via `createTheme` + `<Flowbite theme={{ theme }}>`, and bind `darkMode` to `[data-theme]` so Flowbite's baked-in `dark:` can't ride OS media

**Date:** 2026-06-02.
**Source slice:** 0.6/ST.1/ST.2 (brief 006 — styling foundation).

Two version- and behavior-specific gotchas pinned while wiring the Cadence skin onto Flowbite React (Context7-confirmed against the installed versions: `flowbite-react@0.10.2`, `flowbite@2.5.2`, `tailwindcss@3.4.19`):

- **The provider is `<Flowbite>`, not `ThemeProvider`.** flowbite-react `0.10.x` (the classic Tailwind-3 plugin line) exports `createTheme` + the `Flowbite` provider; the `ThemeProvider`/CLI theming surface belongs to the newer Tailwind-4 line and is **absent** here. Apply one custom theme object via `<Flowbite theme={{ theme: flowbiteTheme }}>`, themed with the token utilities — **not** per-instance `theme={}` props scattered across call sites. (Our own dark/light context lives in a separately-named `src/app/theme/ThemeProvider` — no collision.)
- **Bind `darkMode` to the `[data-theme]` attribute or Flowbite's components will theme off OS media.** flowbite-react components ship baked-in `dark:` classes. With Tailwind's **default `media` darkMode**, those activate on the OS `prefers-color-scheme: dark` **independently of our toggle** — so an OS-dark user who toggles the app to light gets light token vars but Flowbite's `dark:` slots still firing on any un-overridden primitive surface → broken rendering. Set `darkMode: ['selector', '[data-theme="dark"]']` (Tailwind 3.4 custom-selector strategy) so Flowbite's `dark:` variants fire **only** under our attribute — consistent with our tokens, never driven by OS media. Pinned by a test asserting `config.darkMode` deep-equals that tuple.

**Rule:** On flowbite-react 0.10.2, skin via `createTheme` + `<Flowbite theme={{ theme }}>` (not `ThemeProvider`), and set `darkMode: ['selector','[data-theme="dark"]']` so Flowbite's built-in `dark:` binds to your theme attribute, not OS media.
