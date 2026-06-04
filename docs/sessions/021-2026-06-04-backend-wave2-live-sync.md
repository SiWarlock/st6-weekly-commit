# Session 021 — Backend Wave-2: live Outlook-sync pipeline COMPLETE (092 → s7 → s8 → s9) + the infra deploy-docs round

- **Date:** 2026-06-04
- **Phase:** Deploy-demo Wave-2 (live Outlook + SQS/SNS) — **✅ COMPLETE**; + the infra deploy-docs round.
- **Role:** **orchestrator-authored** (`st6-main-orchestrator`). The implementers did NOT run `/session-end` this round — the first backend impl cycled at the s8→s9 boundary (74% WARN, `/session-end` skipped per the lead) + the fresh impl persists for the deploy-support phase; the infra impl persists. This doc is the orchestrator's round-of-record, captured from the slice-by-slice reviews + git.
- **Predecessor:** [019](019-2026-06-04-backend-phase6-complete-manager-reads.md) (backend Phase 6) + [020](020-2026-06-04-frontend-deploy-demo-oauth.md) (frontend deploy-demo). Continuity into this round: handoff `015` + `docs/planning/025`.
- **HEAD:** `becc22a`. **Backend build queue EMPTY** — next is the HITL first-deploy (user-gated) → deploy-support / live-demo QA.

## Why this round existed

The fresh backend+infra orchestrator picked up the Wave-1-code-complete boundary (handoff 015) and drove: the **092 worker-boot fix** (a Wave-1 deploy-completion item), the **Wave-2 live-sync pipeline** (s7 SNS gateway → s8 SQS consumer → s9 Graph adapter), and coordinated the **infra deploy-docs round** (runbooks + READMEs + docker-compose + the worker-db seam) — in parallel with the infra impl.

## What was built (backend, 4 slices — all `./gradlew check` green, all ad-hoc security-reviewed CLEAN)

### 092 — worker boots DB-less in `aws` (`9e5a5d9`, brief 092)
Production `@SpringBootApplication(exclude = {DataSourceAutoConfiguration, HibernateJpaAutoConfiguration})` on `WcSyncWorkerApplication` (the worker was DB-less in Wave-1; a **test-only** `spring.autoconfigure.exclude` was masking a prod crashloop — `:shared`'s JPA starter activates the datasource autoconfig transitively, but the worker mounts graph-only secrets) + the `application-prod.yml`→`application-aws.yml` rename (the §42 dead-profile bug, mirrored on the worker). Pinned by a bean-absence test under `@ActiveProfiles("aws")` + a profile-filename marker test. LESSONS §9-addendum + §42-addendum.

### s7/093 — real SNS lifecycle gateway (`82fd2ab`, brief 093) — security PASS
Replaced the no-op `LoggingLifecycleSnsGateway` behind the 3.5 seam with `AwsSnsLifecycleGateway` (spring-cloud-aws `SnsTemplate.sendNotification` of the pointer-only `SyncJobPointer` to `${SNS_TOPIC_ARN}`). Property-selected (`@ConditionalOnProperty("app.sns.topic-arn")` real / `havingValue=false,matchIfMissing=true` stub — order-independent, NOT `@ConditionalOnMissingBean`). The gateway **propagates** on failure → `SnsLifecyclePublisher` stays the single rule-#4 swallow (record `PENDING_PUBLISH`, never blocks the lock). `app.env: aws` folded into `application-aws.yml` (the pointer `env` correct end-to-end). The SNS starter needed a `spring.cloud.aws.region.static: ${AWS_REGION:us-east-1}` base fallback (clean-CI boot). LESSONS **§43**.

### s8/094 — worker SQS consumer (`a9cb841`, brief 094) — security PASS
`@SqsListener` on `wc-sync` → deserialize the bare `SyncJobPointer` (raw-message-delivery confirmed) → reload `OutlookCalendarSyncRecord` by id → `GraphCalendarPort` seam (a `GRAPH_MODE=demo-success` stub). Idempotent at-least-once (skip on `graphEventId != null`); on failure → `FAILED` + fixed non-PII `failureCode`/`safeMessage` + a **cause-less sanitized `SyncProcessingException`** (ids-only, no cause-chain → the SQS framework's redrive logging stays PII-free) → DLQ. **Reverses the 092 JPA exclude** — the worker now reads the DB (`@EntityScan("com.st6.wc")` + `@EnableJpaRepositories("com.st6.wc.sync.repo")`; a `:worker` Testcontainers PG harness for the bean-presence boot test). LESSONS **§44**.

### s9/095 — real MS Graph calendar adapter (`becc22a`, brief 095) — security PASS
The real `GraphCalendarAdapter` behind the s8 port (`@ConditionalOnProperty("app.graph.mode", havingValue="real")`): app-only client-credentials (azure-identity `ClientSecretCredential` + a v6 `GraphServiceClient`), owner mailbox resolved from `Employee.email` (the `@EnableJpaRepositories` widened to add `com.st6.wc.employee.repo`), calendar-event create. A mockable `GraphEventGateway` seam isolates the lone SDK-touching `MsGraphEventGateway`; a non-PII `CalendarEventSpec` keeps Kiota types + PII out of the testable adapter. **§10/REQ-I-004 fail-safe (degrade AT STARTUP):** `real` + any blank `GRAPH_*` → `DegradedGraphCalendarPort` is wired *before* any client construction (no live client / crash / network; safe-FAILED via s8). Deps: `microsoft-graph:6.65.0` + `azure-identity:1.18.3`. One narrow `EI_EXPOSE_REP2` exclude (the `GraphServiceClient` has no interface to inject — `MsGraphEventGateway` only). LESSONS **§45**.

## Infra (the deploy-docs round + the s8 seam)
- **Worker-db seam** `adfa639`: worker IRSA `GetSecretValue` graph→`[db, graph]` + the SPC db object (the s8 datasource). The DLQ-redrive policy, the api `sns:Publish` grant, and SNS→SQS `raw_message_delivery=true` were all confirmed **already-present** (no gap).
- **Deploy.yml `VITE_AUTH0_*` fix** `dc44544` (a caught deploy-breaker: the SPA's `auth0Config.ts` `requireVar`-fails without the injection). Added `AUTH0_DOMAIN`/`AUTH0_CLIENT_ID` as 2 new HITL GitHub vars.
- **Deploy-docs:** the 3 runbooks (a auth0+M365/Entra · b fresh-AWS→deploy-ready incl. the github-remote Step 0 concretized to `SiWarlock/st6-weekly-commit` · c deploy+smoke), infra/README (`b0298b1`), the local docker-compose (`e5089b0`). Push posture finalized (`302079d`/`770bd83`): `origin`=gitlab + a `github` deploy-CI remote; pushes user-controlled.

## Decisions made
- **s9 Q3 fail-safe outcome = safe-FAILED** (per the binding ARCHITECTURE D.6 "records a safe failure + safe_message + manual-retry"), NOT demo-success (which would mask a real-mode misconfig as fake-SYNCED). **Brief 095's RED#3 wording ("degradesToDemo_neverThrows") was the drift** — caught by the impl at s9 Step-2.5, resolved by conforming to the binding architecture (no code/contract change; D.6 already decided it). The ARCHITECTURE D.6 already documents safe-FAILED; only the brief's wording was imprecise.
- s7/s8/s9 all reuse the **shipped-no-op-seam → real-adapter** pattern (LESSONS §43); the cause-less sanitized rethrow (s8, impl-caught rule-#7-on-framework-logs defense) extends to the s9 adapter boundary (defense-in-depth).
- §12 worker-secret scope graph→**db+graph** (the `adfa639` cross-doc invariant) — a justified least-privilege expansion (the worker genuinely needs db for s8), NOT a safety relaxation; still ARN-scoped (NOT auth0/demo/`*`).

## Decisions explicitly NOT made (deferred — see Carry-forward)
- The `demo-failure` `GRAPH_MODE` value (catalog placeholder, never set) stays unimplemented.
- Calendar-event enrichment (minimal subject = eventKind+week today).
- A "permanent-config-failure → no-redrive" path (the safe-FAILED degrade redrives each attempt — acceptable + visible).
- A worker-specific least-privilege DB role (the worker uses the shared db datasource for MVP).

## TDD compliance
Clean. Each backend slice ran RED→Step-2.5→GREEN; the orchestrator reviewed every Step-2.5 + Step-9 + ran the ad-hoc security pass (general-purpose agent — the `security-reviewer` subagent isn't registered) on every rule-#4/#7 slice (s7/s8/s9 all CLEAN; 092 had no safety surface). One required test-teeth fix at s8 (the rethrow assertion strengthened to pin `.hasNoCause()` + no-PII — the cause-less contract). `./gradlew check` green on each.

## Reachability (Step 7.5)
All reachable: 092 (the deployed worker boots Ready DB-less); s7 (lock → afterCommit → `SnsLifecyclePublisher` → the real gateway in aws); s8 (`@SqsListener` → reload → `GraphCalendarPort`); s9 (the `real`-gated adapter behind the port). Pinned by the selection + boot + listener tests. No tested-but-unwired gaps.

## Security review
s7/s8/s9 each got an ad-hoc rule-#4/#7 (+ §10-fail-safe for s9) review — **all CLEAN, 0 findings** (creds presence-logged only; cause-less ids-only exceptions; the Graph error double-sanitized; the fail-safe builds no live client on blank creds; the SpotBugs exclude one-class-scoped, masks only the SDK transport; pointer-only payloads). 092 had no safety surface.

## Open follow-ups
- **Carry-forward (this tracker):** the 3 s9 low TODOs (demo-failure mode / event enrichment / no-redrive path); the worker-DB-least-privilege role.
- **Next phase:** deploy-support / live-demo QA — the user is starting the full HITL deploy WITH live Outlook (bootstrap → apply → populate secrets → deploy.yml → smoke, per runbooks a/b/c). The orchestrator cycles fresh for this phase (post-lead-compaction).
- **Process note:** one registry-lifecycle correction — the orchestrator must NOT `rm` registry/heartbeat files (the lead's job); flag suspected ghosts. Memory [[cycle-cleanup-stale-registry]] updated by the lead.
