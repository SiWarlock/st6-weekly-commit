# st6-main backend orch+impl cycle handoff — 2026-06-06 (HARD-STOP)

> Written by the outgoing `st6-main-orchestrator` (HARD-STOP 82%) + `st6-main-wc-api-implementer` (75% ACTION) cycling together at a clean 106-seal boundary. The fresh backend pair picks up the **urgent demo-seed job (brief 107)**. Companion to the MVP_TASKS **🔁 ORCH-CONTINUITY** block (`672e09d`) — this doc is the load-bearing detail; that block is the in-tree pointer.

## State (one paragraph)
Backend deploy-support. **The first live Outlook sync is PROVEN in production** (the `340d7e3b` re-drive through the deployed 104 worker created a real event on sam.carter@dreddy817's Outlook — `Deleted=1`/no-error-logs/no-stale-exception confirmed 104's `claimForSync` reclaimed the stale row + the clean success path). Deploy 1 (104 + 90s SQS visibility-timeout + worker IRSA) is LIVE. All backend work is sealed **locally** through `672e09d` (the lead pushes). No slice in flight.

## ⭐ NEXT (URGENT, time-sensitive): the week-parameterized demo-seed job = brief 107
**Timeline:** recording is **THIS WEEKEND** (run seed for week **Jun 1–7**) + a live external review **week of Jun 8–14** (run seed for Jun 8–14). The app is single-week, so each week needs its own seeded matrix. Move fast.

**DESIGN (lead-APPROVED verbatim — brief 107 against this):**
- **Runner:** a `DemoSeedRunner` keyed on `--app.job=seed-demo` + `--week=YYYY-MM-DD` (target Monday week-start, default current week), mirroring `PlanShellGenerationRunner`/`ProjectionRebuildRunner` — a one-shot k8s Job (`spring.main.web-application-type=none`, terminates naturally), one `@Transactional` run.
- **Reset-then-seed (idempotent):** for the target week, DELETE the week's derived data in **FK-safe order** (disputes → manager-review rows → projections [`manager_plan_summary` + `manager_heatmap_cell`] → sync records [incl. the spent `340d7e3b`] → commitments → plans, all scoped to `weekStartDate`=target across the 7 personas), THEN re-insert the matrix. Re-runnable between takes + for slippage.
- **7-persona matrix (parameterized replica of V5/V6):** Priya LOCKED-SYNCED · Marco LOCKED-FAILED-overdue · Aisha LOCKED-OPEN-dispute · Tomas LOCKED-RESOLVED-dispute · Grace RECONCILING(+carry-forward, prior RECONCILED) · **Sam DRAFT(unlinked commitment — the fresh lock→sync fixture)** · Dana manager command-center roll-up. Each persona's plan + commitments + (sync/dispute/review rows) for the target week.
- **Temporal correctness:** compute `reviewDueAt` etc. RELATIVE to the target week (weekStart + N weekdays, **injectable `Clock`**) so OVERDUE (now>reviewDueAt, **derived not stored**) + reconciling/reconciled render right for current (Jun 1–7) AND next (Jun 8–14) week.
- **Projections:** after seeding source, **reuse `ProjectionRefresher.recomputeForPlan` per plan** (the §9 rebuild path) so projections==source — do NOT hand-insert projection rows. Keeps Dana's command-center honest.
- **Invariants/rule #7:** insert valid LOCKED baselines (≥1 linked planned commitment — lock guard), ≤1 unresolved dispute/commitment (partial-unique-safe), OVERDUE derived-not-stored, locked-baseline-immutability, fixed non-PII sync `failureCode`/`safeMessage` (rule #7). **TDD over Testcontainers** (seed a week → assert the 7 states + projections + invariants; re-run → idempotent).
- **Infra-trigger (companion):** a `job-seed-demo.yaml` k8s Job manifest parameterized with `--app.job=seed-demo --week=<date>` (like `job-migration`/`job-rebuild-projections`); st6-main-infra-implementer `kubectl apply`s it per target week.
- **SIZE:** biggest backend slice in a while — **likely splits** (runner+reset / matrix+temporal+projections). **FIRST ACTION: investigate the V5/V6 seed (`db/demo-seed`, the V5 persona seed + V6 fixture matrix) for the exact matrix to replicate** before briefing 107.

## Polish-deploy bundle (ONE deploy makes the site demo-ready)
Ahead of `github/main` (=`5822dea`, the Deploy-1 ref) the bundle is:
- `1952762` — the **104b reaper** (held; inert until the worker `sqs:SendMessage` grant `ea04616` + it deploys).
- `0451d9f` — **106** (human Graph subject + §10 deep-link body).
- **[chevron fix]** — FRONTEND, currently **uncommitted** (`apps/wc-web/src/standalone/shell/PrimaryNav.tsx` modified in the tree) — the frontend track holds it.
- **[demo-seed runner + `job-seed-demo.yaml`]** — **107, pending** (this handoff's slice).
- **[`WC_FRONTEND_BASE_URL` infra env]** — **pending** (`=https://wc.${ROOT_DOMAIN}` on the worker Deployment; route to st6-main-infra-implementer at polish-deploy assembly; the worker yaml nested default `${WC_FRONTEND_BASE_URL:https://wc.${ROOT_DOMAIN:localhost}}` boots local/CI until then).
All ride ONE polish deploy; the seed Job is then **run per week** post-deploy.

## Open threads
- **WC_FRONTEND_BASE_URL infra companion** — route to st6-main-infra-implementer at polish-deploy assembly (specced in brief 106 + the §48 nested default in `0451d9f`).
- **Marco E23 retry** — the in-UI demo-beat option; gated on whether Marco's record is FAILED (retryable → in-UI failed→retry→SYNCED) or stuck-SYNCING (E23 409s). User checking. (The seed job's Marco fixture should land him LOCKED-FAILED-overdue → retryable, which also serves this.)
- **Deploy 2 = federation host + standalone-build switch (Option B)** — separate from the polish deploy; isolates the cert-SAN re-validation risk.
- **Deploy-2 wc-api CORS change** — add `https://portal.${ROOT_DOMAIN}` to the allow-list (lead-queued, not yet dispatched).
- **Re-fire a clean sync post-polish-deploy** — once 106 deploys, re-fire a sync (re-drive or a chosen beat) for a clean LINKED event (the current sam.carter event is bare/pre-106).
- **Wholesale close-out** — coming after Deploy 2 + federation verification (the lead's call).

## Re-drive tool (loaded — for re-firing a sync)
The deployed 104 worker lease-reclaims a stale-`SYNCING` record on re-delivery. Re-publish via the production SNS path (infra has AWS access; only `syncRecordId` is load-bearing — the worker reloads the record):
```
aws sns publish --topic-arn arn:aws:sns:us-east-1:554608989058:wc-lifecycle \
  --message '{"syncRecordId":"<id>","eventKind":"IC_PLANNING","env":"aws","traceId":"manual-redrive"}'
```

## Deploy mechanics + §48 caution
- **Deploy = `git push github main`** (user-controlled) → `gh workflow run deploy.yml` (lead-driven) → gates job → production-gate (user approval) → terraform apply + the wc-api/wc-sync-worker docker build/push + kubectl apply (migration Job → rebuild-projections Job → roll deployments) + **`yarn nx build wc-web` → S3** (`deploy.yml:303-319` — so the frontend tree DOES re-deploy; confirm the wc-web standalone build with the frontend impl before a push).
- **§48 runtime-classpath caution (LESSONS §48 + §49/§19/§43-addenda):** new worker code that runs at boot in the `aws` profile must (a) ship its runtime deps in the bootJar (`runtimeOnly`, not `testImplementation` — verify via `scripts/verify-gradle.sh` Gate 7, which is `PIPESTATUS`-fixed) and (b) give every `${ENV}` placeholder a resolvable `:default` (non-serving Jobs load the `aws` profile but lack the HTTP-serving env). The seed Job is a non-serving Job → same discipline.

## Closed-out artifacts (this cycle's work)
Briefs **099–106** authored; LESSONS **§19/§43/§44/§48-addenda + §49 + §49-addendum + §45-addendum** banked; Log entries through 2026-06-06. The whole worker-concurrency arc (104 claim → `5822dea` regression → 104b reaper/idempotency → the live re-drive proof → 106 polish) is sealed.
