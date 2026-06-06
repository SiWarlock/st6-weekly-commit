# Frontend (wc-web)

## Executive summary

This layer is the browser-side application that ICs and managers actually click: a React 18 / Vite 5 single-page UI built as a **Module-Federation remote** so a parent "platform host" app can mount it as one component, plus a standalone build that runs the same UI on its own for the deployed demo and local dev. It owns nothing about business rules — it renders what the backend says is allowed, posts user actions to the API, and shows loading / empty / error / success states. Its data layer is **RTK Query**: a thin set of typed "API slices" (one per domain — plans, commitments, disputes, manager, sync, etc.) whose TypeScript DTOs in `shared/lib/dtos.ts` are the executable mirror of the backend's wire contract (ARCHITECTURE Appendix B). A hard safety boundary (REQ-I-008) keeps all demo/persona/Auth0 login code out of the exposed remote, enforced by import-graph and source-literal tests. Authorization, lifecycle legality, and which buttons appear are never re-derived here — the UI gates purely on the server's `allowedActions[]`. The sibling `apps/wc-e2e` package holds the Cypress + Cucumber acceptance suite: seven `.feature` files, each tagged 1:1 to a REQ ID, asserting the end-to-end flows.

## Responsibilities

- **Render the IC + manager UI** — the weekly-plan workspace, the manager command center, the alignment heatmap, dispute/comment/sync surfaces — consuming server data via RTK Query and rendering the §7 view-state contract (loading / empty / error / success / partial). `apps/wc-web/src/features/**`.
- **Be the Module-Federation remote** — expose exactly one mountable module (`./WeeklyCommitApp`) that consumes a host-provided router + Redux store + auth accessor, creating none of its own. `apps/wc-web/src/remote/WeeklyCommitApp.tsx:26`.
- **Run standalone** — a separate entry (`standalone/main.tsx` → `StandaloneShell`) owns the router, store, theme, identity provider, demo chrome, and an MSW mock backend, so the same UI runs without the host or even without the real API. `apps/wc-web/src/standalone/StandaloneShell.tsx:34`.
- **Hold the typed API contract** — the per-domain `*Api.ts` slices + `shared/lib/dtos.ts` are the TypeScript mirror of ARCHITECTURE Appendix B; a field change here is a cross-doc invariant. `apps/wc-web/src/shared/lib/dtos.ts:1`.
- **Gate controls server-authoritatively** — render/enable an action iff it is in the server's `allowedActions[]` via one `can()` helper; surface the server's RFC-7807 `safeMessage`/`fieldErrors[]` verbatim. `apps/wc-web/src/shared/lib/allowedActions.ts:10`.
- It is **NOT** accountable for: enforcing lock/authorization/lifecycle rules (delegated to the backend application/authz layers — see [03-application-lifecycle.md](03-application-lifecycle.md) / [04-authorization-identity-audit.md](04-authorization-identity-audit.md)); the wire DTOs' authoritative definitions, endpoints, and error codes (the backend API layer — [02-api-web.md](02-api-web.md)); the comment feature's deep behavior (deferred to [08-comments-collaboration.md](08-comments-collaboration.md)); and hosting/CloudFront/S3 delivery (see [10-infrastructure-deployment.md](10-infrastructure-deployment.md)).

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `WeeklyCommitApp` (default export) | The single MF-exposed module: registers host `getAccessToken` into the seam, renders `<AppRoutes/>` inside the host router | `apps/wc-web/src/remote/WeeklyCommitApp.tsx:26` |
| `vite.config.ts` federation block | Declares remote `wc_web` / `remoteEntry.js` / `./WeeklyCommitApp`; shares React/ReactDOM/RTK/react-redux | `apps/wc-web/vite.config.ts:21` |
| `shouldEnableFederation` | Pure env resolver: federation ON for the default remote build, OFF for `VITE_BUILD_TARGET=standalone` and under Vitest | `apps/wc-web/vite.buildTarget.ts:43` |
| `StandaloneShell` | Standalone provider tree: `BrowserRouter` + store + theme + identity provider (Auth0 vs Demo) + app-shell chrome | `apps/wc-web/src/standalone/StandaloneShell.tsx:34` |
| `main.tsx` | Standalone entry; starts MSW (if enabled) then mounts `StandaloneShell` | `apps/wc-web/src/standalone/main.tsx:30` |
| `baseApi` | The RTK Query root: base URL resolution, `prepareHeaders` auth XOR, `application/problem+json` parsing, late-bound fetch | `apps/wc-web/src/app/baseApi.ts:67` |
| `authAccessor` | Injectable token/demo-header seam (no demo literal lives here — REQ-I-008) | `apps/wc-web/src/app/authAccessor.ts:15` |
| `store` | Redux store wired with the single `baseApi` reducer + middleware | `apps/wc-web/src/app/store.ts:9` |
| `TAG_TYPES` + `planTags` | The 9 cache tags + the per-id plan invalidation set | `apps/wc-web/src/app/tags.ts:6` |
| `dtos.ts` | The typed API contract mirroring Appendix B (enums + DTOs) | `apps/wc-web/src/shared/lib/dtos.ts:1` |
| `can()` | The single `allowedActions[]` gating predicate (F.4 wiring) | `apps/wc-web/src/shared/lib/allowedActions.ts:10` |
| `parseProblemDetail` | RFC-7807 parser → `{safeMessage, code, constraint, fieldErrors}` | `apps/wc-web/src/shared/lib/problemDetails.ts:45` |
| `statusTaxonomy.ts` | Single source of visual truth: enum → `{tone,icon,label}` maps | `apps/wc-web/src/shared/lib/statusTaxonomy.ts:48` |
| `AppRoutes` | Lazy route tree mounted inside the host router; manager-gated routes | `apps/wc-web/src/routes/AppRoutes.tsx:53` |
| `useIsManager` / `useCurrentUser` | Fail-closed manager gating off `MeDto.isManager` | `apps/wc-web/src/routes/isManager.ts:11` / `apps/wc-web/src/features/me/useCurrentUser.ts:22` |
| `WeeklyPlanView` | IC weekly workspace (plan + commitments + sync + comment) | `apps/wc-web/src/features/plan/WeeklyPlanView.tsx:20` |
| `CommandCenter` | Manager direct-report command center (table + drawer review) | `apps/wc-web/src/features/manager/CommandCenter.tsx:113` |
| `DemoIdentityProvider` / `Auth0IdentityProvider` | Standalone identity, both feed the one accessor seam + reset cache on identity change | `apps/wc-web/src/standalone/DemoIdentityProvider.tsx:23` / `apps/wc-web/src/standalone/Auth0IdentityProvider.tsx:60` |
| `PersonaSwitcher` | Demo-only "viewing as" persona menu (standalone-only) | `apps/wc-web/src/standalone/PersonaSwitcher.tsx:28` |
| MSW `handlers` / `db` / `browser` | Standalone mock backend (intercepts the RTK Query endpoints) | `apps/wc-web/src/standalone/mocks/handlers.ts:82` |
| wc-e2e `.feature` files (7) | Cypress+Cucumber acceptance specs, 1:1 to REQ IDs | `apps/wc-e2e/cypress/features/*.feature` |

## Interfaces & contracts

**MF remote surface (vite.config.ts:21 + WeeklyCommitApp.tsx).** The host imports `wc_web/WeeklyCommitApp` from `remoteEntry.js`. The exposed component's prop contract:

```ts
interface WeeklyCommitAppProps {
  getAccessToken?: AccessTokenProvider; // () => Promise<string>
}
```

Shared singletons the host + remote must agree on (`vite.config.ts:32`): `react ^18.3.1`, `react-dom ^18.3.1`, `@reduxjs/toolkit ^2.3.0`, `react-redux ^9.1.2`. The remote **hard-requires** a host-provided Redux `<Provider>` and (in `auth0` mode) an accessor before mount — the eager `getMe` gating query and `useStore` reads have no fallback. This contract is documented in `apps/wc-web/README.md` and anti-drift-tested in `apps/wc-web/src/remote/host-integration.test.ts:29` (README ↔ vite.config ↔ authAccessor must match).

**Auth header XOR (`baseApi.ts:17`).** `prepareHeaders` attaches exactly one header per `VITE_AUTH_MODE`:
- `auth0` → `Authorization: Bearer <jwt>` from `getAccessToken()` (throws normalized on failure).
- `demo` → the demo persona header, attached only by the injected standalone applier (no-op in the remote).
- any other value → `throw new Error('Unsupported VITE_AUTH_MODE')` (fail-loud).

**Accessor seam (`authAccessor.ts`).** Two injectable globals — `setAccessTokenProvider()` / `setDemoAuthHeaderApplier()` — set by `WeeklyCommitApp` (host token), `DemoIdentityProvider`, or `Auth0IdentityProvider`. `getAccessToken()` throws if unset; `applyDemoAuthHeader()` is a degrade-safe no-op if unset.

**RTK Query endpoints (the per-domain slices).** Each slice `injectEndpoints` into `baseApi`. Hooks generated per endpoint. Mutations invalidate via `planTags(planId)` (`{plans,id}` + `{plans,'CURRENT'}` + `manager`), guarded `error ? [] : tags` so a failed mutation invalidates nothing. Endpoint → URL map (matches Appendix F.4 verbatim):

| Action gate | Endpoint | Slice |
|---|---|---|
| (read) | `GET /api/me` | `meApi.ts:27` |
| (read) | `GET /api/rcdo` | `rcdoApi.ts:46` |
| (read) | `GET /api/plans/current`, `GET /api/plans/{id}` | `plansApi.ts:16,27` |
| `LOCK` | `POST /api/plans/{id}/lock` | `plansApi.ts:39` |
| `START_RECONCILIATION` | `POST /api/plans/{id}/start-reconciliation` | `plansApi.ts:50` |
| `CLOSE_RECONCILIATION` | `POST /api/plans/{id}/close-reconciliation` | `plansApi.ts:63` |
| `ADD_UNPLANNED` | `POST /api/plans/{id}/unplanned-commitments` | `commitmentsApi.ts:36` |
| `CARRY_FORWARD` | `POST /api/commitments/{id}/carry-forward` | `commitmentsApi.ts:79` |
| (create/patch/delete) | `POST/PATCH/DELETE /api/.../commitments` | `commitmentsApi.ts:23,49,62` |
| `OPEN/RESPOND/RESOLVE_DISPUTE` | `POST /api/commitments/{id}/disputes`, `/api/disputes/{id}/{respond,resolve}` | `disputesApi.ts:26,39,52` |
| `MARK_REVIEWED` | `POST /api/manager/reviews/{reviewId}/mark-reviewed` | `reviewApi.ts:21` |
| (manager reads) | `GET /api/manager/{command-center,heatmap,heatmap/{cellId}/drilldown}` | `managerApi.ts:48,82,109` |
| `RETRY_SYNC` | `POST /api/outlook-sync/{syncRecordId}/retry` | `syncApi.ts:21` |
| `COMMENT` | `POST /api/comments` (read `GET /api/comments`) | see [08-comments-collaboration.md](08-comments-collaboration.md) |

**Error contract.** Every slice sets `transformErrorResponse: (r) => parseProblemDetail(r.data)`, so components read `(error as {safeMessage?}).safeMessage`. `baseApi`'s `isJsonContentType` accepts `application/problem+json` so RFC-7807 bodies parse rather than arriving as raw text (`baseApi.ts:79`).

## Data & state

- **Where state lives.** A single Redux store with one reducer slice (`baseApi.reducer`); all server state is RTK Query cache. No domain reducers, no thunks/sagas (forbidden). Local UI state (form-open toggles, expanded row, persona id) is component `useState`. `store.ts:9`.
- **The typed contract (`dtos.ts`).** B.1 enum unions (`PlanState`, `CommitmentKind`, `Priority`, `WorkType`, `Confidence`, `AlignmentStatus`, `ReconciliationOutcome`, `ReviewStatus`, `AllowedAction`, `RiskBadge`, `SyncStatus`, `EventKind`, `SyncRelatedType`, `CommentTargetType`, `DisputeStatus`, `FlagType`) + DTOs (`WeeklyPlanDto`, `WeeklyCommitmentDto`, `ManagerReviewDto`, `AlignmentDisputeDto`, `ManagerCommandCenterRowDto`, `HeatmapCellDto`/`HeatmapResponseDto`/`HeatmapDrilldownDto`, `OutlookSyncRecordDto`, `CommentDto`, `MeDto`, `RcdoTreeDto`) + request DTOs (`Create/PatchCommitmentRequest`, `Open/Respond/ResolveDisputeRequest`, `MarkReviewedRequest`, `CreateCommentRequest`) + the `PageEnvelope<T>` Spring `Page<T>` shape. `MeDto` lives in `meApi.ts:8`; the RCDO node types in `rcdoApi.ts:9`.
- **Cache tags (`tags.ts:6`).** 9 lowercase tags: `me, rcdo, plans, commitments, review, disputes, manager, comments, sync`. `me`/`rcdo` are tagged-once-never-invalidated (static/seeded). Plans use per-id tags + a `'CURRENT'` sentinel. The `manager` tag covers **both** command-center and heatmap projections (no separate `heatmap` tag).
- **Visual taxonomy (`statusTaxonomy.ts`).** Named `Record<string, {tone,icon,label,ring?}>` maps per enum (`PLAN_STATE_TAXONOMY`, `REVIEW_STATUS_TAXONOMY`, `RISK_TAXONOMY`, `CC_RISK_CHIP_TAXONOMY`, `SYNC_STATUS_TAXONOMY`, `DISPUTE_STATUS_TAXONOMY`, `WORKTYPE/ALIGNMENT/PRIORITY/CONFIDENCE/RECONCILIATION_OUTCOME`). Unknown key → `undefined` → atom renders nothing (no throw). `OVERDUE` is a derived read-time overlay, never a stored status.
- **Design tokens.** `tailwind.config.ts:25` maps every semantic scale to a `var(--…)` CSS custom property; `darkMode: ['selector','[data-theme="dark"]']`. The token stylesheet (`src/styles/theme.css`) is the only permitted CSS file. Flowbite primitives are skinned via `flowbiteTheme.ts:9` (`createTheme`).
- **Demo personas (`demoIdentity.ts:14`).** Five seeded personas (4 ICs spanning DRAFT/LOCKED/RECONCILING/RECONCILED + 1 manager) whose ids must match the V5 seed UUIDs.

## Dependencies

- **Depends on (inward):**
  - The **backend API + DTO/endpoint/error contract** — every slice URL and DTO shape mirrors it. See [02-api-web.md](02-api-web.md).
  - The **server's `allowedActions[]` and lifecycle/authz decisions** — the UI is a pure consumer; it re-derives nothing. See [03-application-lifecycle.md](03-application-lifecycle.md) and [04-authorization-identity-audit.md](04-authorization-identity-audit.md).
  - The **manager read projections** (command-center rows + heatmap cells) it renders. See [05-manager-projections.md](05-manager-projections.md).
  - The **Outlook-sync records** surfaced read-only with manual retry. See [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md).
  - Auth0 tenant config (`VITE_AUTH0_*`) for the deployed real-OAuth demo; CloudFront/S3 to serve the standalone build. See [10-infrastructure-deployment.md](10-infrastructure-deployment.md).
- **Used by (who calls in):**
  - In production, the **PA platform host** mounts `wc_web/WeeklyCommitApp` and supplies router + store + `getAccessToken`.
  - In standalone/demo, `main.tsx` self-mounts via `StandaloneShell`.
  - The **wc-e2e Cypress suite** drives the rendered UI (the standalone build, ports 5173/8080) via `data-cy` selectors.

## How it works (flow)

**Two build targets, one config.** `vite build` (default) emits the federation remote (`remoteEntry.js`); `build:standalone` sets `VITE_BUILD_TARGET=standalone` → `shouldEnableFederation` returns false → Vite uses `index.html` → `main.tsx` as the entry and emits a static SPA (`vite.buildTarget.ts:43`, `package.json:9`). Fail-safe: only the exact string `'standalone'` opts out of the remote.

```
   HOST (prod)                          STANDALONE (demo/dev)
   host router + store + token          main.tsx → StandaloneShell
        │                                    │ (owns BrowserRouter+store+theme+identity)
        ▼                                    ▼
   wc_web/WeeklyCommitApp  ◀── exposes ──  <WeeklyCommitApp/>
        │  setAccessTokenProvider(host)        ▲ Demo/Auth0 provider feeds the same seam
        ▼                                       │
   <AppRoutes/> (lazy, host-router-consuming) ─┘
```

1. **Remote mount.** `WeeklyCommitApp` registers the host's `getAccessToken` into the accessor seam in a `useEffect`, computes readiness (`auth0` mode requires an accessor), and renders `<AppRoutes/>` — never a `BrowserRouter` (`WeeklyCommitApp.tsx:29`).
2. **Route gating.** `AppRoutes` reads `useIsManager()` (fail-closed off `MeDto.isManager`); manager routes are **not registered** for an IC (`AppRoutes.tsx:65`). `/` is a persona-aware `RootRedirect` → command center (manager) or weekly-commit (IC) (`AppRoutes.tsx:25`). Routes are `React.lazy` dynamic imports under one `Suspense` + a `RouteErrorBoundary`.
3. **A query.** A view calls a generated hook (e.g. `useGetCurrentPlanQuery`). RTK Query builds the request; `prepareHeaders` attaches the one auth header per mode (`baseApi.ts:17`); `fetchFn` late-binds `globalThis.fetch`. Success caches under tags; error runs `parseProblemDetail`.
4. **A mutation.** e.g. `LockButton` (rendered iff `can('LOCK', plan.allowedActions)`, `LockButton.tsx:24`) calls `lockPlan(id).unwrap()`. On success `planTags(id)` invalidates → `getCurrentPlan` refetches into `LOCKED` (no optimistic flip). On error the parsed `safeMessage` + `fieldErrors[]` render verbatim (`LockButton.tsx:47`).
5. **Standalone identity.** `StandaloneShell` branches on `VITE_AUTH_MODE` (`StandaloneShell.tsx:35`): `auth0` → `Auth0IdentityProvider` (PKCE login, `/callback` outside the gate) + `Auth0IdentitySlot`; else → `DemoIdentityProvider` + `PersonaSwitcher`. Both wire the accessor seam **synchronously** via a lazy `useState` initializer (so first-render children see it, `Auth0IdentityProvider.tsx:32`) and `dispatch(baseApi.util.resetApiState())` on identity change (`DemoIdentityProvider.tsx:39`).
6. **Standalone mock backend.** `main.tsx` optionally `await`s `startMockWorker()` (`browser.ts:24`) before mount; the MSW worker intercepts the RTK Query endpoints, routes by the `X-Demo-Employee-Id` persona header, and serves contract-typed fixtures from a mutable `db` (the dispute loop writes through). It awaits SW *control* on cold install to avoid a hung skeleton (`swControl.ts:37`).
7. **E2E.** Cypress (port 5173 UI / 8080 API) loads a `.feature`, runs its step definitions, which drive the UI via `cySel(...)` selectors and assert the rendered `safeMessage`/state — not raw HTTP status.

## Design decisions & rationale

- **Server-authoritative gating (`allowedActions[]`).** Every action control renders/enables solely on `can(action, allowedActions)` — the UI never re-derives lock eligibility, authz, or lifecycle legality (ARCHITECTURE §7 last paragraph; safety rules #1/#3/#11). This lets the frontend ship a control *dormant* (gated on an action the backend currently emits empty) that auto-activates when the backend ships the emission, with zero frontend change — e.g. the dispute controls (`DisputePanel.tsx:129`, dormant until backend 5.5b).
- **No optimistic updates.** Mutations invalidate-then-refetch; views change only after the server's new state arrives. Failed mutations invalidate nothing (`error ? [] : tags`). This keeps the UI a faithful mirror of server truth and makes the error path Cypress-assertable.
- **The DTOs are the contract (§7).** RTK Query slice types + `dtos.ts` mirror Appendix B exactly; a field change is a cross-doc invariant requiring an Appendix B + § edit the same round. Drift is "silent disagreement," so it's pinned.
- **REQ-I-008 boundary (§7 host-integration).** Demo/persona/Auth0/MSW code lives only in `src/standalone/`, injected into the shared layer via no-op-default seams (the demo header literal and Auth0 SDK never enter the remote closure). Proven with import-graph + fail-closed literal scans + positive controls in `remote/boundary.test.ts`.
- **One Vite config, two artifacts, fail-safe (§7 9.16/9.17).** The remote stays the default; only the explicit `'standalone'` flips federation off — a typo can't silently disable the production artifact.
- **Single source of visual truth (`statusTaxonomy.ts`, §7).** Enum→tone/icon/label is mapped once and consumed everywhere; per-surface differences get a distinct named map (e.g. `CC_RISK_CHIP_TAXONOMY` vs `RISK_TAXONOMY`) rather than an inline re-map.
- **Tailwind/Flowbite-native theming via `[data-theme]` (§7 styling source of truth).** Tokens are CSS custom properties; dark is default; a single attribute flip switches themes with no rebuild and no bespoke `dark:` utilities.

## Gotchas & sharp edges

- **The remote needs a host store + accessor *before* mount.** The eager `getMe` gating query means a host that mounts `WeeklyCommitApp` without a Redux `<Provider>` (or, in `auth0` mode, without an accessor) breaks. `WeeklyCommitApp` only soft-fails the missing-accessor case (renders a `role="alert"` notice, `WeeklyCommitApp.tsx:41`); a missing store is a hard crash.
- **`useIsManager` is fail-closed.** It returns `false` while `getMe` is pending or errored (`isManager.ts:13`), so manager routes/nav never flash to an unconfirmed actor — but an errored `getMe` also silently demotes a real manager to IC routing until refetch.
- **Identity switch must reset the whole cache, not just invalidate.** Argless identity-scoped queries (`/api/me`, `/api/plans/current`) don't re-key on a header/token change, so both identity providers call `resetApiState()` (skipping first mount). Forgetting this leaves the prior persona's data on screen.
- **Manager rows carry no `reviewId`/`allowedActions`.** `ManagerCommandCenterRowDto` is a roll-up only; acting on a review lazily fetches the report's plan via `getPlanById` (E4) to get `managerReview` + per-actor `allowedActions` (`CommandCenter.tsx:58`). The same `allowedActions`-gated `CommitmentList`/`DisputePanel` serve both IC and manager with zero role branching.
- **MSW mocks are partly static.** Non-dispute mutations return a static coherent DTO with empty `allowedActions` (the client re-reads the plan tag); only the dispute open→respond→resolve loop writes through the mutable `db` (`handlers.ts:9`, `:198`). So in the demo, e.g., a created commitment won't persist across a manual refetch the way the real backend would.
- **Plan-history route is a placeholder.** `/weekly-commit/history/:planId` renders a "coming in 9.8" stub (`PlanHistoryPage.tsx:5`) — the route is wired and lazy-loaded but the reconciliation/history view is not built.
- **Demo env footgun.** The standalone demo needs a gitignored `.env.local` with `VITE_AUTH_MODE=demo`; an undefined mode makes `prepareHeaders` throw and the app hangs on the loading skeleton with no surfaced error (LESSONS §26). On a cold MSW install the boot must await SW control or the first fetches bypass the worker and hang (LESSONS §22 / `swControl.ts`).
- **DRIFT — route count.** ARCHITECTURE §7 (line 173) lists **five** routes and says `AppRoutes` "registers the five routes." The realized `AppRoutes.tsx:58-76` registers four feature routes (`/weekly-commit`, `/weekly-commit/history/:planId`, manager command-center, heatmap) plus `/` (`RootRedirect`) and a catch-all `*`. Counting `/` it is five route *paths*; the catch-all is a sixth `<Route>`. The realized manager routes are conditionally registered (not always present), which matches §7's "manager routes are not registered when `isManager` is false." Low severity — substance matches; only the literal "five" count is loose.
- **DRIFT — e2e `data-cy` selectors are unreconciled.** `apps/wc-e2e/cypress/support/selectors.ts` declares a proposed `data-cy` vocabulary (e.g. `command-center` matches, but `plan-view` / `lock-button` / `access-denied` / `persona-switcher` do **not** match the realized wc-web attributes, which use `weekly-plan-view`, `lock-error`, no `access-denied`, etc.). The file itself flags this as "authored-not-green ... RECONCILE (origin 11.7)." So the Cypress suite is well-formed and compiles but is not yet a green run against the real UI. Medium severity for anyone assuming the E2E suite currently passes. `selectors.ts:8`, `cypress.config.ts:13`.
- **No production demo backdoor.** The demo header is accepted by the backend only when `DEMO_AUTH_ENABLED=true`; an empty persona sends no header and the backend returns 403 (safety rule #5). The frontend side keeps the header literal and its truthy guard exclusively in the standalone applier (`DemoIdentityProvider.tsx:53`).

## Connects to

- **[02-api-web.md](02-api-web.md)** — the backend endpoints + DTO/error wire contract this layer's slices mirror; handoff is each `*Api.ts` URL + `dtos.ts` shape ↔ the controller/DTO definitions and the RFC-7807 `safeMessage`/`fieldErrors` body parsed by `problemDetails.ts`.
- **[03-application-lifecycle.md](03-application-lifecycle.md)** — emits the `allowedActions[]` and the lock/reconcile state transitions the UI gates on and refetches into; handoff is `can()` + `planTags` invalidation.
- **[04-authorization-identity-audit.md](04-authorization-identity-audit.md)** — the identity (`/api/me` → `MeDto`) and the self/direct-report authz (404 IDOR-safe) the UI surfaces; handoff is `useCurrentUser`/`useIsManager` and the `X-Demo-Employee-Id` / Bearer header attached by `prepareHeaders`.
- **[05-manager-projections.md](05-manager-projections.md)** — the command-center rows (B.11) + heatmap cells (B.12) rendered by `CommandCenter`/`HeatmapGrid`; handoff is `managerApi` + the `manager` tag.
- **[06-calendar-sync-messaging.md](06-calendar-sync-messaging.md)** — the `OutlookSyncRecordDto` shown read-only with manual `RETRY_SYNC`; handoff is `syncApi` + `SyncRetryAction`, surfacing only `safeMessage` (rule #7).
- **[08-comments-collaboration.md](08-comments-collaboration.md)** — the `COMMENT`-gated `CommentThread`/`CommentList`/`CommentForm` mounted in `WeeklyPlanView` and `CommitmentList`; deep behavior deferred there.
- **[10-infrastructure-deployment.md](10-infrastructure-deployment.md)** — the standalone SPA build served from S3/CloudFront, the `VITE_*` build env (Appendix D.1), and the Auth0 tenant the deployed demo logs into.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
