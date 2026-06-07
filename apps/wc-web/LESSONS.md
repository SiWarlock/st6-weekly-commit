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

---

## <a id="5"></a>5. Testing the RTK Query base — a `prepareHeaders` mode-XOR harness + the undici relative-`baseUrl` gotcha

**Date:** 2026-06-02.
**Source slice:** 9.1 (RTK Query base + auth header XOR).

Two reusable test patterns surfaced standing up `app/baseApi.ts` + `app/store.ts` (`@reduxjs/toolkit@2.12`, `react-redux@9.3`) — both recur in every later RTK Query slice (9.5+), so bank them now.

- **The `prepareHeaders` mode-XOR harness.** To prove the `VITE_AUTH_MODE` header XOR (`auth0` ⇒ `Authorization: Bearer` ONLY; `demo` ⇒ `X-Demo-Employee-Id` ONLY; never both — §7), drive `prepareHeaders` directly: `vi.stubEnv('VITE_AUTH_MODE', …)`, inject **both** accessor-seam providers (`setAccessTokenProvider` + `setDemoEmployeeIdProvider`), call `prepareHeaders(new Headers(), …)`, and assert exactly one header is present and the opposite is `null` — **including the adversarial case where the opposite provider is set but must be ignored**. Mirror it end-to-end through the store (fresh store per mode + a throwaway injected endpoint + mocked `fetch`). Also assert the **no-leak** property (safety rule #7): an `auth0` accessor failure normalizes to a generic `Error('Failed to acquire access token', { cause })` — the raw cause text must NOT appear in `.message` (kept in `.cause` for logs).

- **The undici relative-`baseUrl` gotcha (load-bearing for store-level integration tests).** Under Node/undici (Vitest jsdom), `fetchBaseQuery` constructs a `new Request(url)` **before** it calls `fetchFn` — and a *relative* `baseUrl` (e.g. the production `?? '/'` fallback) makes `new Request()` **throw** ("Invalid URL"), so the mocked `fetch` is never reached and the failure is confusing. Two fixes, both applied: (1) set an **absolute** `VITE_API_BASE_URL` (e.g. `http://localhost/api`) in the Vitest `env` (`vite.config.ts` `test.env`); (2) **late-bind** `fetchFn: (input, init) => globalThis.fetch(input, init)` in `baseApi.ts` so the global mock (`vi.stubGlobal('fetch', …)`) is reliably used rather than a `fetch` captured at module-eval time. Any 9.5+ slice that integration-tests a real endpoint through the store needs both.

**Rule:** Test the `prepareHeaders` XOR by injecting both accessor-seam providers and asserting exactly-one-header (+ the no-leak property); and for store-level integration tests give `fetchBaseQuery` an absolute `VITE_API_BASE_URL` + a late-bound `fetchFn` so undici's `new Request()` doesn't throw before the mocked `fetch`.

---

## <a id="6"></a>6. The REQ-I-008 boundary proof — a fail-OPEN static import-graph guard + a fail-CLOSED auth0 build-output grep (with a positive control)

**Date:** 2026-06-02.
**Source slice:** 9.3 (MFE boundary + tree-shaken demo/persona).

REQ-I-008 (the frontend mirror of root-`CLAUDE.md` safety rule #5 — demo identity env-gated, no production backdoor) requires the **exposed Module-Federation remote build to contain no demo/persona code path** — no `PersonaSwitcher`, `DemoIdentityProvider`, `ThemeToggle`, or `X-Demo-Employee-Id`. Prove it with TWO complementary checks, because neither alone is sufficient:

- **Static import-graph assertion (fast, in-suite, but fail-OPEN).** Walk the transitive relative-import closure from `src/remote/WeeklyCommitApp.tsx` (comments stripped — `test/util.ts` `importGraph`) and assert no `src/standalone/` module is reachable, no graph file references `BrowserRouter`, and a literal scan finds no `X-Demo-Employee-Id`/`demo-token`. Runs every unit test; catches the most likely regression (someone imports standalone chrome into a remote-reachable module). **Caveat — fail-OPEN:** a dropped/missed import edge means a leak slips through, and it scans SOURCE, so it cannot prove a *shared* module's unused demo exports (e.g. `authAccessor`'s demo getter) or a DCE'd demo branch are actually gone from the BUILT bundle.
- **auth0 build-output grep (slow, fail-CLOSED, the backstop).** `VITE_AUTH_MODE=auth0 vite build`, then grep the **federation-exposed chunk + its transitive sub-graph** (NOT the standalone index bundle — that legitimately contains the chrome) for the demo/persona/ThemeToggle identifiers. Include a **positive control** (assert the standalone bundle DOES contain them) so a grep that finds nothing proves *detection works*, not that the pattern was wrong. This proves the real guarantee: Vite's `import.meta.env` replacement DCEs the demo branch and tree-shakes `authAccessor`'s unused demo exports out of the auth0 bundle.

**Durability gotcha (drove the 9.4 demo-branch split):** keeping both auth0+demo branches in a *shared* `baseApi.prepareHeaders` is fine ONLY while baseApi is not remote-reachable. Once the store/route-tree wires baseApi into the remote graph, the source-level literal scan false-positives — so **split the demo-header attach into a standalone-only injected seam** (header name + attach in the standalone provider; `prepareHeaders` calls an injected seam) to keep baseApi source demo-literal-free. Promote the build-output grep to a CI-enforced guard (Phase 11) as the fail-closed backstop to the fail-open static walker.

**Refinement (9.5 — the positive-control string must survive auth0 DCE).** When the build-output grep runs in **auth0** mode (`VITE_AUTH_MODE=auth0`), Vite treats the demo branch as dead code and DCEs `X-Demo-Employee-Id` out of **every** bundle — including the standalone one (the `demoAuthHeaderApplier` closure becomes write-only and tree-shakes away). So a positive control keyed on `X-Demo-Employee-Id` **fails in an auth0 build** (it's absent from the standalone bundle too) → a false "detection broken" signal. Key the positive control on a demo string that **survives auth0 DCE** — `demo-token` (the standalone `getAccessToken` closure) or a persona seed id (`demo-employee-*`) referenced by the always-rendered persona UI. Carry this into the Phase-11 CI guard.

**Rule:** Prove REQ-I-008 with BOTH a fast fail-open static import-graph assertion AND a fail-closed auth0 build-output grep over the federation-exposed chunk (with a positive control keyed on an auth0-DCE-surviving demo string, e.g. `demo-token`/persona seed — not `X-Demo-Employee-Id`); keep demo-header source out of shared/remote-reachable modules via a standalone-only injected seam.

---

## <a id="7"></a>7. One status-taxonomy map is the single source of visual truth — never re-map an enum to a tone/icon/label inline

**Date:** 2026-06-02.
**Source slice:** 9.2 (view-state primitives + themed status/risk atoms; ST.3 fold-in).

Every status/risk/chess enum in WC (`PlanState`, `ReviewStatus`, `RiskBadge`, `ReconciliationOutcome`, `WorkType`, `AlignmentStatus`, `SyncStatus`, `Priority`) maps to a fixed `{ tone, icon, label, ring? }` per Cadence `UI_UX_SPEC §4.2/§4.3`. **Port those maps once** into `src/shared/lib/statusTaxonomy.ts` (verbatim from the Cadence `atoms.jsx` `PLAN_STATE`/`REVIEW`/`RISK`/… maps) and have every atom + every later view consume them — `StatusBadge`, `RiskBadge`, the chess atoms (9.7), the heatmap legend, etc. **Never re-derive a tone/icon/label inline at a call site** — that is exactly how a `MISALIGNED` renders red in one place and violet in another (the spec inconsistency Cadence resolved to red).

- Make the maps `Record<string, {…}>` so an unknown/absent value returns `undefined` → the atom renders **nothing** (no throw) rather than a broken pill.
- The taxonomy renders the **B.1 wire values verbatim** (render-only — no enum/Appendix-A change). Guard drift with a fidelity check against `atoms.jsx` (tones/icons/labels/ring flags all match).
- `OVERDUE` is a **derived overlay** the badge renders when handed a `derivedOverdue` flag — never a stored taxonomy entry on its own (it only ever derives from `NOT_REVIEWED`: `isReviewOverdue = now > reviewDueAt AND status = NOT_REVIEWED`, §3).
- Status/risk is **never color-only** — every badge carries glyph + text label + color (grayscale/colorblind legible, REQ-S-005); the token-driven `Badge` atom (a thin Tailwind-utility span — design-faithful to Cadence's own `.wc-badge`, still approach-A) bakes that in.

**Rule:** Port the §4.2/§4.3 enum→`{tone,icon,label,ring}` maps once into `statusTaxonomy.ts` and consume them everywhere; never re-map a status inline; unknown value → render nothing; `OVERDUE` is a derived overlay, not a stored entry; every badge is glyph + text + color.

---

## <a id="8"></a>8. The applier-seam pattern — keep a safety-mirror literal out of any shared/remote-reachable module by injecting it from `src/standalone/`

**Date:** 2026-06-02.
**Source slice:** 9.4 (lazy route tree + demo-header split).

9.4 wires the lazy route tree into the federation-exposed `WeeklyCommitApp`, bringing `app/baseApi.ts` + `app/authAccessor.ts` (the shared RTK Query base + auth seam) one import away from the remote graph. The REQ-I-008 fail-open static guard (LESSONS §6) scans the remote import closure's **source** for demo/persona literals (`X-Demo-Employee-Id`, `demo-token`); the moment a shared module carrying such a literal becomes remote-reachable, the guard false-positives (and a real demo path could leak). The durable fix is the **applier-seam pattern**, the generalization of the §1/§5 accessor seam from credential *resolution* to header *attachment*:

- **The shared module exposes only a typed no-op-default seam, never the literal.** `authAccessor.ts` adds `type DemoAuthHeaderApplier = (headers: Headers) => void`, a `setDemoAuthHeaderApplier(applier | null)` injector, and an `applyDemoAuthHeader(headers)` dispatcher that **no-ops when unset and try/catch-degrades on throw**. `baseApi.prepareHeaders`'s demo branch becomes a single `applyDemoAuthHeader(headers)` call — the `X-Demo-Employee-Id` string is gone from every shared/remote-reachable module. (The old `getDemoEmployeeId`/`DemoEmployeeIdProvider` were deleted, not layered over — no dead prod surface.)
- **The literal lives only in `src/standalone/`.** `DemoIdentityProvider` (standalone-only, tree-shaken from the remote) registers the applier closure that does `headers.set('X-Demo-Employee-Id', personaId)`, and **preserves the falsy-persona truthy guard** (`if (id)`) so an empty persona degrades to no header — parity with the pre-split branch (the backend `DEMO_AUTH_ENABLED` 403 handles an unidentified demo request).
- **Split it in its own bisectable, behavior-preserving commit** (the safety-mirror seam is REQ-I-008 territory). Prove it with (a) a source-literal scan over `baseApi.ts` (`not.toMatch(/X-Demo-Employee-Id/)`), (b) the §7 XOR still holding through the injected applier, (c) the no-applier / throwing-applier / empty-persona degrades, and (d) the §6 boundary test extended with a **positive control** (assert the new mount, e.g. `AppRoutes.tsx`, IS in the remote closure) so the fail-open walker can't silently no-op if the mount is later removed.

This is the canonical move whenever a shared module would otherwise carry a demo/persona/secret literal that must not reach the remote build. The fail-closed auth0 build-grep (§6) stays the backstop: re-run it when the first feature slice actually pulls `baseApi` into the remote **build** closure (≥9.5 — 9.4's placeholder pages don't), and promote it to a CI guard at Phase 11.

**Rule:** Keep a safety-mirror literal (`X-Demo-Employee-Id`, `demo-token`) out of any shared/remote-reachable module by injecting it from `src/standalone/` via a typed no-op-default applier seam (`apply*` dispatcher + `set*Applier` injector, try/catch-degrade); the literal lives only in the standalone closure (with its falsy-value guard); split it in its own commit and pin with a source-literal scan + a positive-controlled §6 boundary walk.

---

## <a id="9"></a>9. Query slices — read-only tags, the §5 store harness, and the `application/problem+json` parsing gotcha

**Date:** 2026-06-02.
**Source slice:** 9.5 (me/rcdo read slices).

Three conventions every RTK Query read slice (`injectEndpoints` into `baseApi`) follows:

- **Read-only domains tag once and are NEVER invalidated.** `RCDO` (the RC→DO→SO hierarchy — REQ-D-003, no mutation endpoint exists) and `Me` (`MeDto` — relationship-driven, per-session-static in MVP) `providesTags` their read tag and appear in **no** mutation's `invalidatesTags`. That's the opposite of the plan/commitment/manager tags (which mutations invalidate to force refetch). The "never invalidated anywhere" invariant **can't be fully unit-pinned from the slice module** (future cross-module mutations don't exist yet); the deterministically-enforceable half is: assert the read module exports a query hook and **no `*Mutation`** export, plus a documenting comment that no later mutation may invalidate the tag. The full guarantee is this convention.
- **Reuse the §5 store-integration harness** for the happy/error test of every slice: absolute `VITE_API_BASE_URL` + late-bound `fetchFn` + mocked `fetch`, driven through a real store; assert the request path + the single auth header (the slice rides `prepareHeaders`).
- **`fetchBaseQuery` won't parse `application/problem+json` (RFC-7807) by default.** Its default `isJsonContentType` predicate matches `application/json` (and `vnd.api+json`) but **not** `problem+json`, so a 4xx/5xx error body arrives as a raw **string** and `transformErrorResponse`→`parseProblemDetail` (the 9.1 parser) silently degrades to a generic message. Fix once at the base: `fetchBaseQuery({ isJsonContentType: (h) => /application\/(problem\+)?json/.test(h.get('content-type') ?? '') })`. Pin it with an error test asserting a `problem+json` body parses to `{ safeMessage, code }` and never leaks `detail`/`type`/`traceId` (safety rule #7). (Aside: under `tsc` `exactOptionalPropertyTypes`, type an always-present hook result's optional fields as explicit `| undefined`, not `?:`, so they can hold `undefined`.)

**Rule:** Read-only query domains (`RCDO`/`Me`) tag-once-never-invalidate (pin the no-`*Mutation` half + a documenting comment); reuse the §5 store harness; and give `fetchBaseQuery` an `isJsonContentType` that matches `application/problem+json` or RFC-7807 error bodies won't parse.

---

## <a id="10"></a>10. Mutation cache-invalidation — per-id tags, success-only `invalidatesTags`, and the `endpoint.select()` test gotcha

**Date:** 2026-06-02.
**Source slice:** 9.6 (plans + commitments slices).

The cache-invalidation harness every mutation slice (9.8/9.9/9.11/9.12) reuses, plus two RTK Query gotchas pinned standing up `plansApi`/`commitmentsApi`:

- **Per-id provides/invalidates tags.** Read endpoints `providesTags` `[{type:'plans', id}]` (+ a `{type:'plans', id:'CURRENT'}` sentinel on `getCurrentPlan`); mutations `invalidatesTags` the affected plan id + `'CURRENT'` (+ the general `'manager'` tag, no id, since the §9 manager projections — command-center summary + heatmap cell — always co-change, so one tag covers both; there is **no `heatmap` tag**). The mutation **arg carries `planId`** as the invalidation key even when the URL is `/commitments/{id}` (E6/E7), because the tag is plan-scoped.
- **`invalidatesTags` must be guarded `(_r, error) => error ? [] : tags` for success-only invalidation.** In this setup a static `invalidatesTags` array fires **even when the mutation errors** — so a failed `deleteCommitment` (a `409`) would wrongly invalidate + refetch. Guard every mutation's tags with the error-callback form so only a SUCCESSFUL mutation invalidates. Pin it with a test asserting a failed mutation does **not** trigger a refetch.
- **`api.endpoints.X.select()(state)` returns the RAW cache entry** (`.status === 'pending'` during a refetch), **not** the hook-derived `.isFetching`. For a store-level (non-render) cache test, read the raw entry's `.status` + `.data`.
- **No-optimistic proof is two-pronged:** behavioral (hold the refetch open at a gated mock `fetch`; assert the cache still shows the OLD count while `status==='pending'`, the new count only after release, and **exactly 2** read calls) + structural (source-scan the slice for no `onQueryStarted`/`updateQueryData`/`patchQueryData`). §7 forbids optimistic updates. Reuse the §5 store harness throughout.

**Rule:** Mutation slices use per-id `{type:'plans', id}` + `'CURRENT'` tags with the `planId` arg as the invalidation key; guard `invalidatesTags` with `(_r, error) => error ? [] : tags` for success-only invalidation; assert cache state via `endpoint.select()`'s raw `.status` (not `.isFetching`); and prove no-optimistic both behaviorally (gated refetch) and structurally (no `updateQueryData`).

---

## <a id="11"></a>11. Server-authoritative control gating — never re-derive eligibility/authz; render via `can(action, allowedActions[])`

**Date:** 2026-06-02.
**Source slice:** 9.7 (IC workspace + lifecycle/lock bar).

The frontend NEVER re-derives lock eligibility, authorization, or lifecycle legality client-side — the server is the source of truth (e.g. rule #1 lock enforcement lives in `PlanLifecycleService`). The UI's whole job is to render the affordance and surface the server's verdict:

- **Gate every action control ONLY on the server-provided `allowedActions[]`,** through a single `shared/lib/allowedActions.ts` `can(action, list)` helper consumed by every call site (lock, lifecycle, carry-forward, comment, …). Never inline-re-derive "this plan can lock because it has linked commitments" — render `LockButton` enabled iff `LOCK ∈ plan.allowedActions[]` and let the server reject. For actions that aren't `AllowedAction` enum values (e.g. DRAFT edit/delete — there is **no** `EDIT`/`DELETE` action), gate on server *state* (`plan.state==='DRAFT'`), still server-enforced via the relevant `409` (`LOCKED_BASELINE_EDIT`), never a client authz computation.
- **Surface the server's `409`/`safeMessage`/`fieldErrors[]` verbatim** (e.g. `UNLINKED_PLANNED_COMMITMENT`/`EMPTY_PLAN_LOCK` for lock — rule #1) as stable, Cypress-assertable text; never leak `detail`/`traceId` (safety rule #7).
- **A lifecycle transition is a cache-invalidation refetch into the new state, NOT an optimistic flip** — `lockPlan` (E8) invalidates the plan tag (success-only, §10) so the view refetches `DRAFT→LOCKED`; the pinned test holds the refetch open and asserts the state is still `DRAFT` while `status==='pending'`. Lifecycle mutations live on `plansApi`; the shared `planTags(planId)` lives in `app/tags.ts` so commitment + lifecycle mutations share one invalidation key.
- **§3 read-only-post-lock is a render decision, not an edit-guard:** a field frozen by state (`alignmentStatus` when `plan.state!=='DRAFT'`) renders as static labelled text, not a disabled input.

**Rule:** Never re-derive eligibility/authz/lifecycle-legality client-side; gate controls only on the server's `allowedActions[]` (via one `can()` helper) or server state, surface its `409`/`safeMessage`/`fieldErrors[]` verbatim, and treat every lifecycle transition as an invalidate→refetch-into-new-state (no optimistic flip). Recurs for 9.8/9.9/9.11/9.12.

---

## <a id="12"></a>12. `exactOptionalPropertyTypes` + clear-via-patch — declare a clearable optional field `field?: T | undefined`

**Date:** 2026-06-03.
**Source slice:** 9.9 (`CommandCenterParams`).

Under TS strict's `exactOptionalPropertyTypes` (on in this project), an optional field `field?: T` accepts *presence-or-absence* but **not** an explicit `undefined` assignment — `{ field: undefined }` is a type error. This bites the moment a controlled value is *cleared by patching the property to `undefined`*: a filter/query-param object whose optional members are reset via `onChange({ reviewState: undefined })`, a partial-update spread that re-sets a removed key, etc. The `CommandCenterParams` filter object hit exactly this — clearing a filter by setting it to `undefined` failed to typecheck.

- **Declare any optional field that is *assigned* `undefined` (not merely omitted) as `field?: T | undefined`.** That widens the field to accept the explicit clear while staying optional. Reserve the bare `field?: T` form for fields that are only ever omitted, never set-to-undefined.
- This is distinct from "make it nullable" — `null` is a different wire value; the clear here is *absence*, expressed as `undefined`, so the union is `T | undefined`, not `T | null`.
- Recurs across every params/patch interface (the manager filters, future form-patch shapes). Pure TS-strict ergonomics; no runtime effect — but it blocks GREEN until fixed, so bake it into the type when authoring the interface.

**Rule:** Under `exactOptionalPropertyTypes`, any optional field that gets *cleared by assigning `undefined`* (params objects, patch shapes) must be typed `field?: T | undefined`, not `field?: T`.

---

## <a id="13"></a>13. Manager read-surface conventions — `PageEnvelope<T>`, Pageable query, the shared `manager` tag, and reaching an action via the aggregate root

**Date:** 2026-06-03.
**Source slice:** 9.9 / 9.10 (command center + heatmap + drilldown).

The manager read surfaces (command-center E13, heatmap E14, drilldown E15) share a set of conventions worth reusing for any future paginated/manager read (9.11+):

- **One generic `PageEnvelope<T>` in `dtos.ts`** mirrors Appendix B.20 verbatim (`{ content: T[]; page: {number,size,totalElements,totalPages}; sort: {property,direction}[] }`) and is reused by command-center, drilldown, comments. Pageable queries take a single typed params object (`weekStart` required where the contract says so), **omit `undefined` filters** from the query string (clean cache keys), and apply the F.5 default sort client-side only when the caller sends none.
- **There is no `heatmap` tag.** Command-center + heatmap project off the same §9 read models that co-change, so both queries `providesTags: ['manager']` and every reconciliation/dispute/review/mark-reviewed mutation that invalidates `manager` refetches both. (See §10 for the plan-side tags; `manager` is the general one.)
- **When a row DTO lacks the id/`allowedActions` an action needs, reach it via the aggregate root — don't invent a read endpoint.** `ManagerCommandCenterRowDto` (B.11) carries no `reviewId`/`allowedActions`, so mark-reviewed (E16) is driven from the plan's `managerReview` (B.7) via a lazy `getPlanById` (E4, which authorizes the direct manager) on a per-row expand — skip-until-expanded. The action component (`MarkReviewedAction`) is self-contained + `can()`-gated, like `LockButton`. (This is the *frontend* mirror of the dispute-gap resolution: when the wire model omits an action's handle, expose it through the aggregate it belongs to.)
- **A single `page`/`size` that paginates grouped sub-lists (E15 drilldown) must drive the pager off `max(totalPages)` across groups,** not the first group — else a deeper group's items become unreachable. The server paginates every group at the same page; the client pager spans the max so nothing is hidden.

- **A read-thread at a *list* grain mounts its query lazily.** When a paginated read-surface sits at a per-row grain (e.g. a comment thread per commitment), make it collapsed-by-default and mount the `getX` query only on open — otherwise a list of N rows fires N queries on render. The 9.11b `CommentThread` does this (COMMENT-gated + per-target `{type:'comments', id:'${targetType}:${targetId}'}` tag); same skip-until-open as the 9.10 drilldown.

**Rule:** Reuse one generic `PageEnvelope<T>` (B.20) + a typed omit-undefined Pageable params object; tag manager reads `['manager']` (no `heatmap` tag); reach an action's missing id/allowedActions via the aggregate root (lazy `getPlanById`, not a new endpoint); span a grouped-drilldown pager off `max(totalPages)` so no group is hidden; and lazy-mount any per-row-grain read-thread's query (collapsed-by-default) to avoid N-queries-per-list.

---

## <a id="14"></a>14. Expand-in-place → themed Flowbite `Drawer` — always-renders-children means conditional-mount the inner data-component

**Date:** 2026-06-03.
**Source slice:** ST.6c (manager review + drilldown Drawers).

Swapping a per-row inline-expand / inline-panel to a `flowbite-react` `Drawer` (right-slide + scrim) has a load-bearing gotcha: **the `Drawer` ALWAYS renders its children** — `open` only toggles an off-screen `translate` class, it does NOT conditionally mount. So putting a lazy/skip-until-open data-component directly inside the always-rendered `Drawer` fires its fetch while "closed" (a silent skip-until-selected regression) and makes open/close non-deterministic to assert.

- **Conditionally mount the inner data-component on the trigger state** (`{expandedPlanId && <ManagerRowReview .../>}` inside the Drawer), not the Drawer itself. Preserves the lazy `getPlanById` / skip-until-selected fetch AND makes the surface assertable.
- **Hand-roll the Drawer header** (token-native + a `react-icons/hi` close button) rather than `Drawer.Header` — Flowbite's header ships Material `MdHome`/`MdClose` icons that clash with the project's Heroicons.
- The prop is **`position`**, not `placement`; `data-cy` rides the `...props` spread onto the dialog div.
- The Drawer container's `data-cy` is present in the DOM even when closed (always-rendered) — assert *visibility/open-state*, not mere presence.

**Rule:** A `flowbite-react` `Drawer` always renders its children (open = off-screen translate) — conditionally mount the inner lazy/data component on the trigger state (preserves skip-until-open + makes it assertable); hand-roll the header (Material-icon clash); the prop is `position` not `placement`.

---

## <a id="15"></a>15. Backend-less standalone via a standalone-only MSW layer — contract-typed fixtures, persona-driven, boundary-proven tree-shaken

**Date:** 2026-06-03.
**Source slice:** ST.7a (MSW mock-data layer).

To render populated data surfaces without a backend (for real-browser QA + the demo-video deliverable), a Mock Service Worker (MSW v2) layer lives **only in `src/standalone/`** and is **tree-shaken from the Module Federation remote** (REQ-I-008), mirroring the demo-identity boundary (§6/§8):

- **Fixtures are typed as their Appendix-B DTOs** (`const plan: WeeklyPlanDto = {…}`) so TS compile-enforces contract fidelity — the mock is a faithful double, not drifting from the wire contract. A fixture that won't type is a contract-drift Finding (flag it; don't patch the DTO).
- **Persona-driven via the `X-Demo-Employee-Id` seam** — handlers route GETs off the demo header the `DemoIdentityProvider` applies; one IC persona per lifecycle state + a manager whose command-center/heatmap are those ICs as direct reports = one coherent demo org.
- **Dynamic-imported + `await worker.start()` before `createRoot().render()`** in `standalone/main.tsx` (dev/`VITE_USE_MOCKS`-gated) so no request fires before the SW intercepts.
- **Extend the 9.3 REQ-I-008 boundary proof** with a fail-open import-graph assertion + a fail-closed literal scan (`msw`/`setupWorker`/`mockServiceWorker`) over the remote closure **plus a positive control** that the standalone graph DOES contain it (so the scan isn't vacuous).
- **Gotcha:** a `*/api` route glob inside a JSDoc block comment closes the comment (`*/`) — keep route globs out of block comments.

**Rule:** A backend-less standalone uses a standalone-only MSW v2 layer (contract-typed fixtures compile-enforced vs `dtos.ts`; persona-routed via `X-Demo-Employee-Id`; dynamic-imported + awaited in `main.tsx`); prove it tree-shaken from the remote with a fail-closed literal scan + a positive control; keep `*/api` globs out of JSDoc block comments.

---

## <a id="16"></a>16. An identity/persona switch must `resetApiState()` — argless identity-scoped queries don't auto-invalidate on a header change

**Date:** 2026-06-03.
**Source slice:** ST.7d (persona-switch stale-data bug).

Switching the demo persona updated the `X-Demo-Employee-Id` the accessor seam sends, but `/api/me` + `/api/plans/current` + the manager reads are cached under **argless** RTK Query keys — a header change does NOT change the cache key, so nothing refetched and **the previous identity's data stayed on screen** (a real stale-data bug, behaviorally confirmed in the connected browser). Tag invalidation doesn't help: no mutation fired, and the cache key is identical across identities.

- **On identity change, `dispatch(baseApi.util.resetApiState())`** (in a `personaId`-keyed effect, skipping the initial mount) — drop the whole cache so every active query re-issues with the new identity. This is the idiomatic "user switched identity" reset, distinct from per-tag invalidation (§10).
- **Standalone-only** — the exposed remote gets identity from the host, so this lives in `DemoIdentityProvider` (REQ-I-008 intact). Any future real auth-identity switch needs the same reset.

**Rule:** An identity/persona switch must `dispatch(baseApi.util.resetApiState())` (skip first mount) — argless identity-scoped queries (`/api/me`, `/api/plans/current`) don't auto-invalidate on a header/identity change, so a tag-invalidate is insufficient; reset the whole cache.

---

## <a id="17"></a>17. Flowbite theme-mode is a second source of truth — drive `useThemeMode().setMode()` from our `[data-theme]`

**Date:** 2026-06-03.
**Source slice:** ST.7d (#6 theme desync).

`flowbite-react` 0.10.2's `<Flowbite>` mounts its own `useThemeMode()`, which **independently persists `flowbite-theme-mode` (default `light`) and toggles the `.dark` class on `<html>`** — a second theme source that drifts from our `ThemeProvider`'s `[data-theme]` (QA caught `wc-theme=dark` vs `flowbite-theme-mode=light`). Binding `darkMode: ['selector','[data-theme="dark"]']` (§4) makes Flowbite's `dark:` *read* our attribute, but it doesn't stop Flowbite's own mode tracker from running.

- **Make our `ThemeProvider` the single source:** a standalone-only `FlowbiteThemeSync` null-component mounted inside `<Flowbite>` drives `useThemeMode().setMode(ourTheme)` on every theme change, so `data-theme` / `flowbite-theme-mode` / `html.dark` always agree.

**Rule:** `flowbite-react`'s `<Flowbite>` runs its own `useThemeMode()` (persists `flowbite-theme-mode` + toggles `.dark` independently) — drive it from our `[data-theme]` single source via a sync component inside `<Flowbite>`, else Flowbite primitives (Drawer/Modal) drift from the app theme. (Extends §4.)

---

## <a id="18"></a>18. A partially-overridden Flowbite primitive silently loses its default classes — they live in `.mjs`/`.cjs` outside the content glob

**Date:** 2026-06-03.
**Source slice:** ST.7e (manager Drawers rendered top-left, not right-slide).

The manager Drawers rendered as a small top-left content-sized panel instead of the right-slide full-height overlay — even though both passed `position="right"` and `setTheme`'s `mergeDeep` preserved `root.position`. **Root cause: `flowbite-react`'s default theme classes (the Drawer's `right-0 top-0 h-screen w-80 transform-none translate-x-full` positioning + the `bg-gray-900/50` backdrop) live only in the library's `.mjs`/`.cjs` dist**, which the `tailwind.config` content glob (`node_modules/flowbite-react/**/*.{js,jsx,ts,tsx}`) **never scans** → those utilities are referenced-but-never-generated (inert) → a Drawer we'd only *partially* overridden (`root.base` skin only) fell through to a bare `fixed` element. Every *fully*-overridden primitive (badge/button/table/modal) worked because its classes live in our scanned `flowbiteTheme.ts`.

- **Own the full slot in the scanned theme** (`root.base` + `root.position.<side>` on/off + `root.backdrop`), token-native — do NOT widen the content glob to `.mjs`/`.cjs` (that emits ALL of Flowbite's defaults → CSS bloat, REQ-NF-005).
- **jsdom/Vitest pins a Drawer/overlay's open-state + content + gating, but NOT its computed position/size** — a right-slide/full-height/backdrop regression sails past unit tests. A real-browser pass (gstack `/connect-chrome` — a real Chromium, which also drives a React controlled `<select>` that headless synthetic events can't) is required to catch overlay positioning. Pin the deterministic boundary in Vitest (the theme config-shape carries the position/size/backdrop classes); confirm the visual in the real browser.

**Rule:** A *partially*-overridden `flowbite-react` primitive silently loses its default positioning/backdrop — those classes live in `.mjs`/`.cjs` outside a `*.{js,jsx,ts,tsx}` content glob (inert). Own the full theme slot in the scanned config (don't widen the glob → CSS bloat); and real-browser QA, not jsdom, catches overlay computed-position regressions.

## <a id="19"></a>19. A control can gate on an `allowedAction` the backend doesn't emit YET — dormant-until-emitted, never re-derived client-side

**Date:** 2026-06-03.
**Source slice:** 9.11a (alignment-disputes UI, built ahead of the backend `5.5b` affordance-emission slice).

The disputes UI (manager-open / IC-respond / manager-resolve) had to ship before the backend emitted `OPEN_DISPUTE`/`RESPOND_DISPUTE`/`RESOLVE_DISPUTE` on `allowedActions` — the read contract (B.6 `dispute?`) had landed (5.3b) but the capability emission was a later slice (5.5b). The backend's interim suggestion was "gate respond/resolve on the dispute's `status` + actor role meanwhile." **That is exactly the client-side authz/lifecycle re-derivation §11 forbids** — it duplicates the server's authorization logic in the client, where it silently drifts from the server's real rule.

The correct posture: **gate every control on `allowedActions` via the one `can()` helper (§11), even when the backend emits an empty array today.** The control renders dark (the action is absent) until the backend ships the emission, at which point it **auto-activates with zero frontend change** — the next read simply carries the action. Unit tests exercise the active path by providing the action in the mocked `allowedActions` (the contract is frozen; only the live emission lags). This lets the frontend *lead* the backend on a feature without ever re-deriving server authority.

- **Never substitute `status`+role gating for an absent `allowedAction`.** "Dark until the server says so" is the feature, not a gap — it is the §11 invariant holding under a backend-lag.
- **Mock the action present in tests** so the component is fully covered now; the live activation is a backend concern, verified at the affordance-emission slice (here, real-browser QA at 5.5b).
- **Pair the dependency explicitly** (brief Dependencies + a Carry-forward marker) so the activation slice is tracked — a dormant control is invisible until then and easy to forget.

**Rule:** Gate a control on a server `allowedAction` even when the backend emits it empty today — built + mock-tested, it stays dark until the backend ships the emission, then auto-activates with zero frontend change. Never gate on status/role as an interim (that re-derives server authority client-side — the §11 violation this corollary exists to prevent).

## <a id="20"></a>20. One `allowedActions`-gated list component serves ALL actor roles — zero client-side role branching (the §11 dividend)

**Date:** 2026-06-03.
**Source slice:** 9.14 (manager plan-detail dispute surface — the IC `CommitmentList` rendered verbatim for the manager).

The manager needed a surface to open/resolve disputes on a direct-report's commitments. Rather than build a manager-specific commitment renderer, we rendered the **same `CommitmentList`** the IC view uses, fed by the manager's E4 (`getPlanById`) read. Because every control inside it gates purely on the server's per-actor `allowedActions` (§11), the component is **automatically role-correct**: the IC sees respond + edit/reconcile/carry-forward (their `allowedActions`); the manager sees open/resolve (theirs); neither sees the other's — with **no `if (isManager)` anywhere in the component**. The whole 9.14 slice was ~15 lines (render the existing list in the existing Drawer) precisely because of this.

- **Don't fork a component per actor role.** If a component gates every control on `allowedActions`, the server's per-actor computation makes one component correct for all roles. A per-role fork duplicates logic + re-derives authority client-side (the §11 violation).
- **The read endpoint is the per-actor differentiator**, not the component: the IC reads via E3 (`getCurrentPlan`), the manager via E4 (`getPlanById` of a report) — same component, different `allowedActions` in the payload. (Extends [[11]] server-authoritative gating + [[13]] reach-the-aggregate-root-lazily; the IDOR scoping is the backend's, §6.)
- **Caveat:** the shared component must be presentation + gating only (no actor assumptions in its own logic). `CommitmentList` qualified because 9.7/9.11a kept it purely `allowedActions`-driven.

**Rule:** Render one `allowedActions`-gated component for every actor role instead of forking per role — the server's per-actor `allowedActions` make it role-correct with zero client-side role branching (the §11 dividend). The read endpoint differentiates the actor (E3 own vs E4 report), not the component.

## <a id="21"></a>21. The standalone MSW mutable-db pattern — deep-clone-from-fixtures seed + write-through mutations + live read-selectors (the §15 mutable extension)

**Date:** 2026-06-04.
**Source slice:** 9.15 (stateful MSW — live dispute loop + demo interactivity).

§15 shipped the static-coherent MSW layer (fixtures typed as Appendix-B DTOs, persona-routed, tree-shaken). The static layer can't transition state — a resolve POST `404`s — so the live dispute loop (open→respond→resolve) couldn't be QA'd or demoed. 9.15 makes the layer **mutable** without breaking any of §15's guarantees:

- **A mutable in-memory `db`** (`src/standalone/mocks/db.ts`) seeded once at module init by a **deep clone** (`structuredClone`) of the canonical fixtures — **never alias the exported fixtures** (a write must not mutate `ALL_PLANS`; pin it with a `seed_is_deep_cloned_not_aliased` test).
- **Write-through mutations** (E17/E18/E19) mutate the db; a `MockDbError{status, code}` maps to an RFC-7807 body, mirroring the backend's named codes (`SECOND_OPEN_DISPUTE` 409, ≥1-field 400, unknown-id 404-never-leak). The read selectors (`getPlanForPersona`/`getPlanById`/`getCommitment`/`findDispute`) read the **live db** so re-reads reflect the mutation — the B.6 dispute nest, the §3 review re-derivation (REVIEWED↔REVIEWED_WITH_DISPUTES, and the NOT_REVIEWED-guard so resolve never falsely promotes an unreviewed plan), and the per-actor affordances over live state (incl. the IC_RESPONDED middle state).
- **Persist across persona switches, seed once** — the db is the shared demo world (a manager's open is visible when you switch to the IC = the whole point of the loop). `resetDb()` re-clones the seed for **test isolation only**.
- **Targeted overlay, not a full rollup** — when a read surface needs to reflect a mutation (here the command-center row's `unresolvedDisputeCount`/`reviewStatus`), overlay **only** the affected derived fields from the db; keep hand-tuned styling counts seeded. A full recompute perturbs the styling-coverage fixtures + balloons scope — defer the full rollup (and the non-dispute mutations) to a follow-up (9.15b).
- **REQ-I-008 stays green** — db/handlers/boot-helper stay in `src/standalone/`; the `dtos` import is **type-only** (erased — no runtime edge into the remote graph), so the boundary import-graph + literal-scan guards still pass.

**Rule:** Take a standalone MSW layer mutable via a deep-cloned-from-fixtures in-memory `db` (persist + seed-once + `resetDb()` for tests) with write-through mutations (`MockDbError`→RFC-7807, backend codes mirrored) + live read-selectors behind the existing handlers; overlay only the affected derived fields on read surfaces (never a full rollup mid-slice); keep it standalone-only with a type-only `dtos` import so REQ-I-008 holds. (Extends [[15]]; the gated affordances ride [[19]]/[[20]].)

## <a id="22"></a>22. MSW cold-install boot — await SW *control* before the first query, with a timeout backstop (real-browser SW timing)

**Date:** 2026-06-04.
**Source slice:** 9.15 (real-browser QA Finding #1).

In a **fresh** browser the standalone hung on a loading skeleton: the app's first RTK Query requests fired before the Service Worker **controlled** the page, so they bypassed MSW, hung, and didn't recover (a fresh tab with a warm SW cleared it). The trap: `worker.start()` resolves when the SW is **registered**, not when it **controls** the page — §15's "await `worker.start()` before render" is necessary but not sufficient on a cold install.

- **Await SW control before the first query** — after `worker.start()`, `await waitForServiceWorkerControl(navigator.serviceWorker)` before `createRoot().render()`. The helper resolves **immediately** if a controller is already present (warm), else on the `controllerchange` event (cold).
- **Timeout backstop** — the helper also resolves after a timeout, so a missing/never-controlling SW can **never deadlock** the boot forever (degrade to rendering rather than hang).
- **Extract the await as a pure helper** (`swControl.ts`, **no `msw/browser` import**) so it's unit-testable in jsdom (jsdom can't run `setupWorker`) — pin warm-resolves-immediately / cold-resolves-on-`controllerchange` / never-deadlocks-on-timeout (fake timers). The deterministic boot ordering is the testable core; the live cold-install behavior is confirmed in the real browser ([[18]] — real-browser SW timing is invisible to jsdom).
- **The config half (Finding #2):** the demo must use a **relative** base URL — an absolute `VITE_API_BASE_URL` makes requests cross-origin → bypasses the same-origin SW → hang. Default the demo to relative; only a real-backend dev sets the env (`.env.example` config/docs, REQ-I-008-safe).

**Rule:** A standalone MSW boot must await SW *control* (not just `worker.start()`'s registration) before the first query — `waitForServiceWorkerControl()` (resolve-now-if-controlling / on-`controllerchange` / on-timeout-backstop), extracted as a pure helper for jsdom unit-testing; and the demo must use a relative base URL (an absolute base bypasses the same-origin SW). (Adjacent to [[18]] real-browser SW timing; extends [[15]]'s boot ordering.)

## <a id="23"></a>23. The standalone demo app-shell mirrors the production host chrome — tree-shaken from the remote (the §7/REQ-I-008 corollary)

**Date:** 2026-06-04.
**Source slice:** ST.8a (the missing global chrome — doc 024 §A/S1).

Per §7 (the MFE boundary), the production **host** owns `BrowserRouter` + the app-bar / nav / identity chrome; the exposed remote (`WeeklyCommitApp`) renders only the routed subtree. So in standalone mode there is **no chrome** — the demo rendered bare (a thin "PERSONA / Light mode" header, no app-bar/nav/breadcrumb) against the composed-app mockup, which depicts the **host + remote** together. The user's mockup-vs-as-built QA flagged this as the biggest gap.

- **Don't add chrome to the exposed remote** (that leaks host responsibilities + breaks REQ-I-008). **Simulate the host chrome in the standalone shell** instead: build the app-bar + persona-gated sub-nav + breadcrumb as **standalone-only** components (`src/standalone/shell/*`), mounted by `StandaloneShell` around `<WeeklyCommitApp/>`. The nav drives the **existing `AppRoutes`** via the standalone-owned `BrowserRouter` — **no new routes**, no router in the remote.
- **Tree-shaken automatically** — `boundary.test.ts`'s import-graph walk covers `shell/*` via the existing "no `src/standalone/` module reachable from the remote" assertion (a standalone module is only a violation if a *remote-reachable* module imports it; the shell is mounted by `StandaloneShell` only). **No new boundary test needed.**
- **Keep role-differentiation server-authoritative** — the sub-nav gates on `useIsManager` (the `/api/me` read), and the persona-switch reroutes through **`navigate('/')` → `RootRedirect`** (the existing persona-aware redirect) rather than reading a persona's role in the switcher to pick a landing route. This avoids the transient-`isManager`-false `*`-bounce AND keeps the route guards the single source of role-landing (the §11 spirit — don't re-derive role client-side, even for nav).
- **Token-native even in demo chrome** — no hex (the brand mark uses `currentColor` + `text-white`); add the shell files to `surfaceSkin.test.ts`'s `TOUCHED_SURFACES` scan. Pixel fidelity vs the mockup is a real-browser gate (gstack canon-compare), not a unit concern ([[18]]).

**Rule:** When the production host owns the chrome (§7 MFE), DON'T add chrome to the exposed remote — simulate the host chrome in a standalone-only app-shell (`src/standalone/shell/*`) mounted around the remote, driving the EXISTING routes via the standalone-owned router; it's tree-shaken automatically (`boundary.test.ts` covers it via the standalone-dir assertion). Keep role-landing server-authoritative (gate nav on `useIsManager`; reroute via `'/'`→`RootRedirect`, no role read in the switcher) + token-native (no hex). (The §7/REQ-I-008 corollary; extends [[6]]/[[15]] the standalone-only boundary.)

## <a id="24"></a>24. Client-computed "at a glance" summary — a pure unit-tested derivation over the loaded rows, backend field as the production follow-up (the D-1 pattern)

**Date:** 2026-06-04.
**Source slice:** ST.8b (the command-center "At a glance" summary strip).

The command-center mockup shows a summary strip (reports / review-overdue / open-disputes / reconciling / not-locked / reviewed-clean counts). The **production-correct** source is a backend §9 `summary` field — accurate under server-pagination, where a client roll-up sees only the current page. But for the **demo** (the MSW returns all reports on one page), the strip is computable client-side, giving full mockup fidelity now without blocking on the backend. The lead/user-approved resolution (**decision D-1**):

- **A pure `summarizeRows(rows): GlanceCounts` function** (no component, no hooks) is the testable core — counts derived over the **full loaded `data.content`** (not a sliced view). Unit-test the derivation directly, including the **load-bearing discrimination edges**: e.g. a `REVIEWED_WITH_DISPUTES` row is NOT "reviewed clean" but DOES feed the open-disputes total (the exact green-vs-amber distinction the strip visualizes; the 9.15 mutable-db overlay makes that state reachable on a live row).
- **The strip renders from the derivation; the backend field stays the queued production follow-up.** This is the D-1 shape: client-compute now for demo fidelity, queue the backend field for production accuracy. Don't compute the roll-up inline in the component (keep it a pure, testable module).

**Rule:** For a roll-up/summary that's backend-field-correct but demo-computable, build a PURE unit-tested derivation over the full loaded rows now (D-1) — pin the discrimination edges (e.g. reviewed-clean vs reviewed-with-disputes) — and queue the backend field as the production follow-up; never compute the roll-up inline in the component. (Pairs with [[21]] the live mutable-db that feeds it.)

## <a id="25"></a>25. Per-surface tone divergence → a distinct named map in the single-source file, NOT an inline re-map (the §7 clarification)

**Date:** 2026-06-04.
**Source slice:** ST.8b (`CC_RISK_CHIP_TAXONOMY`).

The command-center risk chips tone the risk kinds per the CANON mockup — **misaligned=accent, needs-review=info** (+ dispute=failure, blocked=failure-ring, carry-fwd=warning-ring, resolved=neutral, unlinked=warning) — which **diverges** from the heatmap's `RISK_TAXONOMY` (misaligned=failure, needs-review=warning). §7 says "never re-map an enum inline; one source of visual truth." The resolution when two surfaces genuinely tone the same kinds differently **per canon**:

- **Add a SECOND named map** (`CC_RISK_CHIP_TAXONOMY`) in the single-source file (`statusTaxonomy.ts`), consumed once by the surface's component — NOT an inline hand-toning, and NOT a mutation of the shared `RISK_TAXONOMY` (which would regress the heatmap). Zero blast radius on the other surface.
- **"Single source of visual truth" (§7) means the FILE, not one map per enum** — distinct visual atoms (CC count-chips vs heatmap badges) are distinct maps that coexist in the one file, the same way `PRIORITY_TAXONOMY`/`WORKTYPE_TAXONOMY` do.
- **Confirm the divergence is intentional CANON** (the per-surface mockups tone it differently on purpose), not a mockup inconsistency to harmonize — if the mockups agree, use one map; if they diverge, two. (The heatmap's own tones vs ITS mockup is a separate canon-compare check — ST.8d.)

**Rule:** When two surfaces tone the same enum differently per the canon mockups, add a DISTINCT named map in the single-source `statusTaxonomy.ts` (consumed once by the surface) — NOT an inline re-map and NOT a mutation of the shared map; "single source of visual truth" (§7) is the file, not one-map-per-enum. Confirm the divergence is intentional canon first. (Refines [[7]].)

## <a id="26"></a>26. The standalone demo needs a gitignored `.env.local` (`VITE_AUTH_MODE=demo`) — an undefined mode hangs the app, and inline env doesn't reach Vite

**Date:** 2026-06-04.
**Source slice:** ST.8d (QA finding F1).

A fresh automated QA browser hung on the loading skeleton with no surfaced error. Root cause: `prepareHeaders` (`baseApi.ts`) reads `import.meta.env.VITE_AUTH_MODE` and **throws `Unsupported VITE_AUTH_MODE` when it's undefined** → no RTK Query request ever fires → the app hangs on the skeleton (the throw is swallowed in the header builder, not rendered). Vite only exposes env from `.env*` files (+ the shell at process start), so an **inline `VITE_AUTH_MODE=demo yarn dev`** passed at the wrong layer / to an already-running dev server does NOT reach `import.meta.env`. The standalone demo therefore needs a **gitignored `.env.local` with `VITE_AUTH_MODE=demo`** — the predecessor session had one (gitignored → absent from the shared tree → it bit the fresh QA session). Documented in `.env.example` (ST.8d).

**Rule:** The standalone demo requires a gitignored `.env.local` with `VITE_AUTH_MODE=demo` — an undefined `VITE_AUTH_MODE` makes `prepareHeaders` throw → the app hangs on the loading skeleton with no surfaced error; inline `yarn dev` env doesn't reach `import.meta.env`. Documented in `.env.example`. (Demo/dev-only — the exposed remote gets its mode from the host.)

## <a id="27"></a>27. Solo headless `agent-browser` QA now drives the persona switch + drawers — the §18 native-`<select>` constraint is obsolete

**Date:** 2026-06-04.
**Source slice:** ST.8d (the gstack canon-compare, driven solo/headless).

§18 held that real-browser persona-switch QA needed a **headed** browser (gstack `/connect-chrome`) because the persona switcher was a native `<select>` that headless synthetic events couldn't drive (and historically wanted a user present). **ST.8a replaced the native `<select>` with a clickable custom dropdown** (the identity-slot PersonaSwitcher). Consequence: the gstack **`agent-browser` (headless)** now drives the persona switch + the manager drawers + the theme toggle **directly, no user-present** — the entire ST.8d canon-compare AND the live open→respond→resolve disputes loop were driven solo/headless. The deferred "connected-browser both-persona QA (pending user availability)" carry-forward is **discharged**: solo headless QA is the path for persona/drawer/theme interaction flows going forward.

**Rule:** Solo headless gstack `agent-browser` now drives the persona switch + drawers + theme toggle (ST.8a's custom dropdown replaced the native `<select>`) — the §18 "headed-only / user-present to drive the persona `<select>`" constraint is obsolete; drive persona/drawer/theme interaction QA solo + headless. (A real-browser spot-check still warrants §18's computed-position caveat, but the interaction-driving blocker is gone. Updates [[18]].)

## <a id="28"></a>28. Two build targets (MF remote vs standalone SPA) from one Vite config via a pure env resolver — fail-safe-to-remote; + the `.gitignore`-`build/` test-dir gotcha

**Date:** 2026-06-04.
**Source slice:** 9.16 (standalone static-SPA deploy build).

The demo deploys as a standalone static SPA, but `vite build` by default emits the **Module Federation REMOTE** (`remoteEntry.js`, a library — the production PA-host path), NOT a servable SPA. Both artifacts come from **one `vite.config.ts`**:

- **A pure env-injected resolver** `vite.buildTarget.ts` (`resolveBuildTarget(env)` / `shouldEnableFederation(env)`, mirroring `resolveApiBaseUrl`'s injectable-env shape) gates the federation plugin: `build` (= `build:remote`) keeps federation **ON** (the remote, REQ-I-008 demo-free); **`build:standalone`** (`VITE_BUILD_TARGET=standalone`) flips it **OFF** → Vite uses `index.html` as the entry → a static SPA (index.html + hashed assets) that DOES include the standalone/demo layer (the deployable demo).
- **Fail-safe-to-remote:** ONLY the exact `'standalone'` string opts out; any **unknown/unset/typo'd** `VITE_BUILD_TARGET` defaults to the production remote (federation ON). A build-target switch must never silently ship a federation-less remote artifact. **Unit-test the resolver** (the decision is a pure seam — default-remote / standalone-opt-in / VITEST-forces-off / unknown→remote); **build-verify the dist shape** (a Vitest pin that spawns a real Vite build is too heavy — run `build:standalone` + assert `dist/index.html` + no `remoteEntry`).
- The remote stays the **unchanged default + REQ-I-008-guarded** (`boundary.test.ts`); the standalone build is purely additive. (The standalone SPA's auth/base-URL/MSW are env-baked at build — see the deploy assessment.)
- **⚠️ Gotcha — a committed dir/file named `build/…` is silently swallowed by the repo `.gitignore` `build/` rule.** A test at `src/build/*.test.ts` is un-committable (silently absent from `git add`, no error). Name it otherwise (here `src/vite.buildTarget.test.ts`); always `git status`-confirm a new test file is actually tracked before assuming it'll commit.

**Rule:** Build BOTH the MF remote (default `build`/`build:remote`, federation ON, REQ-I-008 demo-free) AND the deployable standalone SPA (`build:standalone` → `VITE_BUILD_TARGET=standalone` → federation OFF, `index.html` entry) from one `vite.config.ts` via a pure env-injected `shouldEnableFederation` resolver — **fail-safe-to-remote** (only the exact `'standalone'` opts out). Unit-test the resolver; build-verify the dist. And never name a committed dir/file `build/…` — the repo `.gitignore` `build/` rule swallows it silently.

## <a id="29"></a>29. Wire the auth-accessor seam SYNCHRONOUSLY (a `useState` lazy-initializer) before children that read it on first render — a mount `useEffect` is too late

**Date:** 2026-06-04.
**Source slice:** 9.17 (Auth0 OAuth login producer).

`DemoIdentityProvider` registers its accessor-seam providers in a mount `useEffect` (§8/§16) — fine, because nothing reads the seam *during the provider's first render*. The Auth0 path broke that assumption: `WeeklyCommitApp` reads `hasAccessTokenProvider()` on its **first render** to decide its auth0-readiness gate, and the provider's children render in the **same commit** as the provider — so a mount `useEffect` (which runs *after* the commit's paint, child effects first) leaves the seam still `null` when the child checks it → a spurious "no access-token provider configured" alert on the critical path. Fix: register the provider **synchronously during render** via a `useState(() => { setAccessTokenProvider(...); return ... })` lazy-initializer (runs once, before children), and keep a separate `useEffect` purely for the unmount **cleanup**. The §16 reset-on-identity-change stays an effect (it's a post-render reaction, correctly timed).

This was caught by the Step-2.5 fold-in **authenticated-`/*`-nest render test** (StandaloneShell → Routes → gate → AppShell → WeeklyCommitApp) — the isolated gate/provider unit tests passed; only the composed render through the real nest exercised the first-render seam read. A reachable-but-silently-broken critical path that per-component tests miss.

**Rule:** If any child reads an injected seam (`hasAccessTokenProvider()`, a context value, a global registry) **on its first render**, register that seam **synchronously during the provider's render** (a `useState` lazy-initializer), NOT in a mount `useEffect` — effects run after the commit, too late for same-commit children. Keep cleanup in an unmount effect; keep post-render reactions (e.g. §16 reset) in effects. Pin the composed render through the real provider→children nest, not just the pieces in isolation. (Refines [[8]]/[[16]].)

## <a id="30"></a>30. The REQ-I-008 boundary build-grep must scope to the federation-EXPOSED chunk, not all of `dist/`

**Date:** 2026-06-04.
**Source slice:** 9.17 (Auth0 OAuth login producer).

`vite build` (the default `build:remote`) emits BOTH `remoteEntry.js` + the `__federation_expose_WeeklyCommitApp` chunk (the production remote the PA-host loads — must be demo/auth-free, REQ-I-008) **and** the standalone `index.html` → `main.tsx` bundle (the deployable SPA, which legitimately bears the demo layer AND now `@auth0/auth0-react`). A whole-`dist/` literal grep for an auth/demo marker therefore **false-alarms** on the standalone bundle even when the remote is clean. The host never loads `index.html` (only `remoteEntry.js`), so the SDK in the standalone bundle is not a leak. `boundary.test.ts` already scopes correctly — it walks the static import-graph **from `WeeklyCommitApp`** (the exposed entry), not from `main.tsx` — so the fail-closed scan asserts auth0/demo-absence over the *remote-reachable* graph only; a real-build verification must grep the `remoteEntry` + `__federation_expose_*` artifacts specifically, not the full dist.

**Rule:** Scope any REQ-I-008 build-output grep to the **federation-exposed chunk** (`remoteEntry.js` + `__federation_expose_*`), never all of `dist/` — the same `vite build` also emits the standalone SPA bundle, which carries the demo + auth0 SDK by design. Drive the source-side proof from the **exposed entry's import-graph** (`WeeklyCommitApp`), not the standalone entry (`main.tsx`). (Refines [[6]]/[[28]]; the auth0 SDK is the second standalone-only dep, after the demo layer, the boundary now guards.)

---

## <a id="31"></a>31. Wire the host accessor seam SYNCHRONOUSLY across the FEDERATION boundary — register it in the EXPOSED module's render, because the remote's child effects run before any host effect

**Date:** 2026-06-06.
**Source slice:** 024 (Acme Portal Module-Federation host integration; live-portal bug 1).

[[29]] established that an injected seam read on first render must be registered synchronously (a `useState` lazy-initializer), not a mount `useEffect`. Across the **federation boundary** the trap is both sharper and relocated. In remote mode the **host** provides `getAccessToken`, but the exposed `WeeklyCommitApp`'s child `<AppRoutes/>` fires its **eager first RTK Query** (`prepareHeaders → getAccessToken()`) during the remote's first render — which happens *inside* the host's render tree, **before any host mount effect runs**. So the host CANNOT register the accessor in its own `useEffect` (too late), and it cannot rely on the remote to read it from an effect either. The fix: the **exposed module itself** registers the host-passed accessor **synchronously during its own render** (`useState(() => { setAccessTokenProvider(hostGetToken); … })`), so the seam is live before the child's first-render query. Live symptom on the deployed portal: a spurious "No access-token provider configured" until the registration moved into `WeeklyCommitApp`'s render. Pinned by `WeeklyCommitApp.accessor-timing.test.tsx` (a probe asserting the seam is wired at the child's first render).

**Rule:** When a host passes an accessor (`getAccessToken`) into a federation-exposed module, register it **synchronously in the exposed module's render** (`useState` lazy-init), NOT in a host or remote mount effect — the remote's child first-render readers (RTK Query `prepareHeaders`) run before any effect in the host tree. Keep cleanup in an unmount effect; pin with a first-render seam-read probe. (Extends [[29]] to the host↔remote boundary; composes with [[33]].)

## <a id="32"></a>32. @originjs federated CSS auto-injection is broken under an absolute `--base` (drops `assetsDir` → 404) — inject the stylesheet via a `?url` base-resolved import + a manual `<link>`

**Date:** 2026-06-06.
**Source slice:** 024 (live-portal bug 4).

`@originjs/vite-plugin-federation`'s automatic remote-CSS injection concatenates `base + bare-filename` and **drops the `assets/` directory** when the remote is built with an absolute `--base` — it requests `https://wc.…/remote/theme-<hash>.css` instead of `…/remote/assets/theme-<hash>.css` → **404**, so the embedded remote renders unstyled. Fix: import the stylesheet as a **base-resolved `?url`** (`import themeHref from '…/theme.css?url'` — Vite resolves the correct `…/remote/assets/theme-<hash>.css`) and inject it as a `<link rel="stylesheet">` on mount inside the exposed module; keep the federation expose's CSS list (`y([])`) **empty** so no competing (broken) auto-link fires. Pinned by the `<link>`-injection test (asserts the base-resolved href + cleanup).

**Rule:** Don't rely on @originjs auto CSS-injection for a remote built with an absolute `--base` (it drops `assetsDir` → 404). Import the stylesheet via a `?url` base-resolved import and inject a manual `<link>` on mount in the exposed module; keep the expose's CSS array empty. Federation styling is a live-only behavior — verify against the deployed embed, not jsdom. (Composes with [[31]]/[[33]].)

## <a id="33"></a>33. @originjs host↔remote cascade — fold state into the SINGLE exposed-component ensure (a separate `import('remote/store')` deadlocks shareScope), keep ALL React-coupled deps shared (unshare → dual-React), and let the remote self-provide its store

**Date:** 2026-06-06.
**Source slice:** 024 (live-portal bugs 2 + 3; the realized federation surface).

Driving `wc-web` as a real MF remote inside a host surfaced three coupled facts, each revealed only after the prior cleared:

- **(a) No separate pre-component remote-store import — it deadlocks shareScope init.** A `import('wc_web/store')` done *before* mounting the component runs a top-level `await importShared('@reduxjs/toolkit')` **before** the `WeeklyCommitApp` ensure → the shared-scope initialization deadlocks and the host hangs on "Connecting…". Fix: **don't expose `./store`**; fold the store creation into the single exposed-component ensure (`./WeeklyCommitApp`).
- **(b) Keep ALL React-coupled deps shared — unsharing redux → dual-React.** An interim attempt to unshare `@reduxjs/toolkit`/`react-redux` made the remote bundle its own copy that linked against a **second React** → `Cannot read properties of null (reading 'useRef')` (the classic dual-React hooks crash). The shared singleton set must be **all five** React-coupled deps: `react`, `react-dom`, `react-redux`, `@reduxjs/toolkit`, `react-router-dom`.
- **(c) The remote may SELF-PROVIDE its store while redux stays shared — independent concerns.** `WeeklyCommitApp` wraps itself in `<Provider store={…}>` (self-provided store — fixes the (a) deadlock) AND redux remains a shared singleton (fixes the (b) dual-React). These are orthogonal: self-providing the store does not require unsharing redux.

Realized federation surface: **1 expose** (`./WeeklyCommitApp`, no `./store`), the remote **self-provides its store** and **injects its own CSS** ([[32]]), **5 shared singletons** (the React-coupled set above), mounted same-origin under `/portal/` ([[31]] handles the host accessor). The four bugs were **live production incidents** (federation runtime isn't reproducible in jsdom) — diagnosed against the deployed portal + build artifacts; regression-pinned where the unit env permits ([[31]]/[[32]]).

**Rule:** For an @originjs MF remote: expose ONE component and fold its store into that ensure (a separate `import('remote/store')` deadlocks shareScope); keep ALL React-coupled deps shared singletons (unsharing any → dual-React `useRef`-null); the remote may self-provide its `<Provider store>` while redux stays shared (orthogonal). Verify the federation surface live — it's not jsdom-reproducible. (Composes with [[31]]/[[32]]; the realized contract is 1 expose + 5 shared singletons + same-origin `/portal/`.)
