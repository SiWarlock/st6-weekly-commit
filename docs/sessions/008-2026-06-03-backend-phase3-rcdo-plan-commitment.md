# Session 008 — Backend Phase 3: RCDO read → generation → plan reads → commitment create (3.1–3.4a)

- **Date:** 2026-06-03
- **Track:** st6-main (backend, `apps/wc-api/`)
- **Authored by:** st6-main-orchestrator (the implementer was shut down at HARD-STOP without `/session-end` — see note — so the orchestrator, who reviewed every slice, captured this doc on its behalf as part of `/orchestrate-end`).
- **Predecessor:** `006-2026-06-03-phase2-identity-authz-chain.md` (Phase 2 close).
- **Successor:** _(next backend session — fresh impl takes 3.4b + 3.5)_

> **Why orchestrator-authored:** the backend implementer hit HARD-STOP (81%) at the clean 3.4a→3.4b boundary. The lead shut it down **directly** (no `/session-end`) to avoid the HARD-STOP confabulation that bit the prior impl (a fabricated doc/hash at ~86%). All 3.1–3.4a code is committed + each slice was green/security-reviewed, so nothing is lost; the orchestrator persisted (52%) and authored this doc faithfully from the slices it reviewed.

## What was built (5 slices, all `./gradlew check` green, 100% line on new types)

| Slice | Commit | What landed |
|---|---|---|
| **3.1** | `3990513` | RCDO read service + `GET /api/rcdo` (E2) returning the nested `RcdoTreeDto` object-wrapper (RC→DO→SO); records-not-entities; org-wide read (authenticated-only, no authorizer); `V4__seed_rcdo.sql` (1/3/9, sequential UUIDs → logical wire order via `findAllByOrderByIdAsc`); structural no-mutation-endpoint reflection test (REQ-D-003); `RcdoReadService.findSupportingOutcome` seam. |
| **3.2** | `f66c9cf` | Weekly-shell generation job — one-shot `--app.job=generate-plan-shells` (2-class split: `PlanShellGenerator` logic + thin `@ConditionalOnProperty` `PlanShellGenerationRunner`; web-type=none termination); SYSTEM null-actor audit (`PLAN_SHELLS_GENERATED`); idempotent existence-pre-filter + unique backstop; zero sync records / zero commitments (REQ-I-002); org-tz fail-safe reused. |
| **3.3a** | `157f634` | Plan DTO contract — `WeeklyPlanDto` (B.5) + nested `WeeklyCommitmentDto` (B.6-minus-dispute) + `RcdoBreadcrumbDto` + `ManagerReviewDto` (B.7 type) + `AllowedAction` (neutral `com.st6.wc.action`) + `AllowedActionResolver` + `PlanMapper`; **E3 `GET /api/plans/current`** (IC self-scoped, no authorizer); absent shell → `404 PLAN_NOT_FOUND`. |
| **3.3b** | `ce39d3e` | **E4 `GET /api/plans/{id}`** + the first per-resource IDOR-authz consumer (`authorizePlanAccess` chokepoint, codeless-404 existence-hiding, denial-audit-on-genuine-denial-only). **Security-reviewer PASS (0/0/0/0).** |
| **3.4a** | `20a103d` | **E5 commitment create** + the Appendix-E Part-1 validation suite (`TextNormalizer` strip/collapse/NFC + `@CodePointSize` code-point-not-UTF-16 + `@NoControlChars`; enum/malformed-JSON → `400 VALIDATION_ERROR` via `HttpMessageNotReadable` with safe-field-only extraction §15; store-raw §16); parent-plan authz; forced `commitmentKind=PLANNED`; `ErrorCodes`. **Security-reviewer PASS (0/0/0/0).** |

**Lessons banked:** §22 (nested read-DTO / RCDO tree), §23 (one-shot `--app.job` runner), §24 (plan-DTO + server-authoritative `allowedActions`), §25 (per-resource read via the authorizer chokepoint), §26 (server-side input validation). **Cross-doc rows:** 8 new DTO/vocab rows in `apps/wc-api/CLAUDE.md` (`RcdoTreeDto`↔B.4, `WeeklyPlanDto`↔B.5, `WeeklyCommitmentDto`↔B.6-minus-dispute, `RcdoBreadcrumbDto`↔B.6, `ManagerReviewDto`↔B.7, `AllowedAction`↔B.1, `CreateCommitmentRequest`↔B.6, `ErrorCodes`).

## Decisions made
- **RCDO wire order via sequential seed UUIDs** (`findAllByOrderByIdAsc`), not a `display_order` column — zero schema change for permanently read-only seeded data; logical order pinned by a title assertion (3.1 TWEAK).
- **One-shot job pattern** = property-gated `ApplicationRunner` + `web-application-type=none` (no stored context → SpotBugs-clean), NOT `SpringApplication.exit` on a held context (3.2).
- **3.3 split** into 3.3a (DTO + E3 self-read) / 3.3b (E4 + IDOR) — isolate the rule-#3 surface per the standalone-authz pitfall + size.
- **`allowedActions[]` "no affordance without enforcement"** — emit only `LOCK` at 3.3a (its predicate `AllowedActionResolver.canLock` is the SAME logic 3.5 will enforce — §15 single-source).
- **`AllowedAction`/`ErrorCodes` are computed/free-string vocab, NOT `enums/` members** (preserves `EnumVocabularyTest`'s 16-enum pin).
- **`WeeklyCommitmentDto` omits the dispute field** — intentional B.6-minus transitional subset; the approved Option-A (`dispute?: AlignmentDisputeDto`, drop `hasUnresolvedDispute`) lands fresh at the disputes slice (zero throwaway).
- **3.4 split** into 3.4a (create + validation) / 3.4b (patch/delete + gates).
- **Validation:** normalize once in the request record's compact constructor + custom `@CodePointSize` (code points); unknown-enum → `HttpMessageNotReadable` 400 extracting only the safe field name (§15); honor Appendix-E control-char rules (title rejects C0/C1, description strips except `\n`/`\t`).
- **404 flavors:** self-scoped reads (E3) may use a named/coded 404 (`PLAN_NOT_FOUND`); per-resource reads (E4) MUST use the codeless IDOR 404 (existence-hiding); genuinely-missing = 404 no-audit, cross-owner = 404 + denial audit, identical body.

## Decisions explicitly NOT made (deferred)
- **3.1b V5/V6 demo seed** (personas/relationships + fixture plans) — separate slice; V5 unblocks the running demo + demo-auth, V6 after 3.5 (so fixtures match real lock output). Tracked as task 3.1b.
- **Projection upserts for commitment mutations** — deferred to 3.5's `ProjectionService` (DRAFT commitments aren't projected, §9).
- **Commitment semantic ordering** (`createdAt`-asc) — interim is `OrderByIdAsc`; follows the JPA-auditing populator (Carry-forward).
- **Disputes Option-A B.6 edit** — orchestrator-owned, lands at the Phase-3 disputes slice (frontend mirror follows).
- **`display_order` column for RCDO** + **hard one-shot `System.exit` guard** — Carry-forward candidates, only if needed.

## Open follow-ups (next session)
- **3.4b (brief 036)** — E6 PATCH + E7 DELETE + `LOCKED_BASELINE_EDIT` (rule #2) + field-level/state gates. Reuses 3.4a's `CommitmentService`/`CommitmentMapper`/`ErrorCodes`/`TextNormalizer`. Security-reviewer.
- **3.5 (brief 037) — E8 LOCK, the demo core + safety culmination** (rule #1 required-SO-at-lock, rule #2 baseline immutability, rule #4 non-blocking sync). **Reuse-don't-recreate:** `AllowedActionResolver.canLock`, the `ManagerReviewDto` type, `ClockConfig` (all exist). Standalone safety commit + ad-hoc security-reviewer.
- **Carry-forward (backend):** JPA-auditing populator (+ commitment-ordering consumer), `MANAGER_ROLE_REQUIRED` guard (Phase-3 manager API), `DEMO_AUTH_ENABLED` prod pin (infra), `cronjob-generation.yaml` launch contract (infra), filter-path `ObjectMapper` hardening (low).

## Reachability
Every Phase-3 endpoint is proven reachable **E2E** by its own `@SpringBootTest` test through the 2.6 security chain → controller → service → mapper → DTO: `RcdoEndpointTest` (E2), `PlanCurrentEndpointTest` (E3), `PlanByIdEndpointTest` (E4, full IDOR matrix), `CommitmentCreateEndpointTest` (E5). The rule-#3 chokepoint is additionally pinned by `verify(repo, never()).findById/save` unit proofs on a denied authorize (3.3b/3.4a). The generation job is reachable via `--app.job` gating (proven both ways with `ApplicationContextRunner`). No separate `/wired` run was needed — the per-slice E2E tests ARE the reachability proof.
