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

**Rule:** Reuse one generic `PageEnvelope<T>` (B.20) + a typed omit-undefined Pageable params object; tag manager reads `['manager']` (no `heatmap` tag); reach an action's missing id/allowedActions via the aggregate root (lazy `getPlanById`, not a new endpoint); and span a grouped-drilldown pager off `max(totalPages)` so no group is hidden.
