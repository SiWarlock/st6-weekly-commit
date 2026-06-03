# Frontend track — resume handoff (for the fresh `st6-main-wc-web-*` successor)

> **Status:** frontend track **CYCLED 2026-06-02** after slice 9.7 (impl hit 68% team-max at the clean `5ff60d2` boundary; lead cycled for a fresh full-budget pair before the substantial 9.8). Read this alongside `docs/sessions/005-2026-06-02-frontend-phase9-ic-workspace.md` and the `MVP_TASKS.md` Frontend-round-3 Log entry. Backend track (`st6-main-orchestrator` + `st6-main-wc-api-implementer`, fresh pair on 2.4/2.5) runs in parallel.

## What landed (this session = 9.4–9.7; prior = spine 0.6/ST.1/ST.2 + 9.1/9.2/9.3)
- `8fa030c` (+`e9ee230`) — **9.4** lazy route tree + the REQ-I-008 **demo-header applier-seam split** (LESSONS §8).
- `87cade1` (+`03c5821`/`3b662c4`) — **9.5** `meApi`/`useCurrentUser` + `rcdoApi` + `RcdoBrowser`/`SupportingOutcomePicker` + **real `isManager` gating** + persona-aware `/` redirect. `baseApi` entered the eager remote closure → **REQ-I-008 auth0 build-grep PASS**.
- `d0ef176` — **9.6** `plansApi`/`commitmentsApi` + **per-id `Plans` cache-invalidation** + `shared/lib/dtos.ts` (the typed Appendix-B contract: B.1 enums + B.3–B.7 DTOs + E5/E6/E11 requests).
- `58d8c5e`/`029ff53`/`5ff60d2` — **9.7** IC workspace: `WeeklyPlanView`/`CommitmentForm`/`ChessLayerFields`/`CommitmentList`/`PlanLifecycleBar`/`LockButton` (E8 `lockPlan`) + the `can()` gating helper. `/weekly-commit` is live (resolves the 9.4 `WeeklyCommitPage` placeholder).
- `6844a20` impl session doc 005. **85 Vitest tests green at cycle.**

## Next slice
- **9.8 — IC reconciliation** (outcome form + unplanned + carry-forward). Extends `PlanLifecycleBar` (`START`/`CLOSE_RECONCILIATION` E9/E10) + `commitmentsApi`; **wires `CommitmentList`'s carry-forward inline handler** (the `can()`-gated control is already built+tested with a handler — just pass the production one). Brief number = **on-disk `ls docs/briefs/` + highest+1** (currently `022` — but **`ls` first**, don't assume).

## Open follow-ups (also in session 005 + the Carry-forward section)
- **9.7b** (new task) — IC commitment **edit/delete UI** (E6/E7, gated on `plan.state==='DRAFT'`; backend + `commitmentsApi` mutations already exist).
- **9.11** — wire `CommitmentList`'s comment inline handler.
- **`RcdoBrowser` consumer surface** — built (9.5) but unwired; needs an RCDO-explorer.
- **9.13 host contract** — the remote hard-requires a host Redux `<Provider>` + `getAccessToken` **before mount** (eager gating `getMe`); document it (OQ-004).
- **Phase-11** — promote the auth0 build-grep to a CI guard; positive control must key on an **auth0-DCE-surviving** string (`demo-token`/persona seed), NOT `X-Demo-Employee-Id` (LESSONS §6 refinement).
- **ST.4/ST.7** — layout tokens (`--reading-col`/`--content-max` → Tailwind `maxWidth`) + the a11y/design-review visual-fidelity pass.

## Locked decisions / conventions (don't re-litigate — wc-web LESSONS)
- §8 **applier-seam** (safety-mirror literals injected from `src/standalone/` only) · §9 **read-only tags + §5 store harness + `isJsonContentType` for `problem+json`** · §10 **per-id `Plans` tags + invalidate-on-success-only (`error?[]:tags`) + `.select().status` + no-optimistic proof** · §11 **server-authoritative gating** (`can()` on `allowedActions[]`, 409 verbatim, no client-side eligibility/authz, lifecycle = invalidate→refetch).
- No separate `heatmap` tag (manager projections co-change). `dtos.ts` = the typed Appendix-B contract (verbatim; no field drift). `planTags` lives in `app/tags.ts`.

## Shared-tree protocol (with `st6-main-orchestrator`, the backend orch)
Frontend owns `§7` + Phase ST/9 frontend tasks + `apps/wc-web/*` + the color taxonomy; backend owns the rest. **Brief numbers: one shared sequence, `ls`+highest+1+mention** (used: backend 015/017/020, frontend 016/018/019/021 → next `022`). **Ping before staging `MVP_TASKS.md`/`ARCHITECTURE.md`; explicit `git add <own paths>`, never `-A`.** `git add -p` is non-TTY-blocked → use `git apply --cached` for hunk-level if the docs carry both parties' uncommitted hunks. `RiskBadge`/enum *values* are a backend invariant (render is frontend).
