# 025 — Infra: Deploy-2 federation portal (Option B, same-origin `/portal/`)

- **Date:** 2026-06-06
- **Phase:** P-deploy (Deploy 2 — Module-Federation portal) · area: `infra/`
- **Predecessor:** [023 — backend deploy dominoes](023-2026-06-06-backend-deploy-dominoes-worker-concurrency-graph-polish.md) · cycle handoff [team-handoffs/020](../team-handoffs/020-2026-06-06-infra-impl-cycle-deploy-handoff.md)
- **Successor:** none (terminal close-out for this track)

## Why this session existed
Cycled-in infra impl to execute the demo deploys: ship the week-parameterized demo-seed Job, assemble + run the **polish deploy** (core demo), then stand up **Deploy 2** (the federation portal that embeds the Weekly Commit micro-frontend). Deploy 2's original cross-origin design hit an AWS account gate mid-apply and was pivoted to a same-origin path topology.

## What was built

**Files created**
- `infra/k8s/job-seed-demo.yaml` (`7625c27`) — week-parameterized one-shot demo-seed Job (`--app.job=seed-demo --week=${SEED_WEEK}`, `--spring.main.web-application-type=none` §22). Manual/off-chain (mirrors `job-perf-seed`); week baked into the Job name → distinct per-week runs, fail-closed on unset `SEED_WEEK`.

**Files modified**
- `.github/workflows/deploy.yml` — federation publish steps. First authored cross-origin (`5bee05e`: standalone main-site `VITE_BUILD_TARGET=standalone` + MF remote `--base=…/remote/` + host→portal bucket), then **reworked to Option B** (`da6f29c`): **f1 main-site build SKIPPED** (live root left byte-identical = the recorded core), f2 remote → wc. bucket `/remote/`, f3 host built `--base=/portal/` → wc. bucket `/portal/`.
- `infra/terraform/s3_cloudfront.tf` (`da6f29c`) — additive `/portal/*` cache behavior on the **existing** wc. distribution (same `s3-assets` origin) + a `portal-spa-rewrite` CloudFront **Function** (viewer-request: extension-less `/portal/*` → `/portal/index.html`). Default + `/remote/*` behaviors byte-identical.
- `infra/terraform/outputs.tf` + `infra/terraform/portal.tf` (`da6f29c`) — **dropped `portal.tf`** (the separate portal distribution/cert/bucket) + its outputs; next apply destroyed the orphaned partials from the failed apply.

**Ops performed (not code):** assembled the `polish-deploy` cherry-pick branch (off `1abdeda`, excluding the Deploy-2 trio); ran the Jun 1–7 seed Job (`wc-seed-demo-2026-06-01`, 8 plans = the 7-persona matrix); rebuilt the frontend `/remote/` dists across the post-deploy MF bug-fix loop for the user-run `s3 sync` republishes.

## Decisions made
- **Deploy 2 → Option B (same-origin `/portal/` on the wc. distribution).** Deploy 2's first attempt (separate `portal.st6weeklycommit.com` distribution) **failed at terraform apply**: `403 AccessDenied — "account must be verified before you can add new CloudFront resources"` (an AWS **account-level** gate, not our code). Pivoted to serve the host from a path on the existing distribution → **no new distribution, no new cert, no account gate**. Same-origin also moots the cross-origin CORS.
- **Path-scoped SPA fallback via a CloudFront Function.** CloudFront custom-error responses are distribution-wide (→ the main `/index.html`), so `/portal/callback` would load the wrong app and break Auth0. The viewer-request rewrite Function is the only way to do per-path fallback — built it proactively; both the lead and frontend independently flagged it as the #1 gotcha.
- **Main-site build SKIPPED in Deploy 2 (not "remove the standalone var").** wc-web's `vite.config` is fail-safe-to-remote (LESSON 28) — a no-`VITE_BUILD_TARGET` build emits the **remote**, so "remove the var" would have shipped the remote to the main-site root and broken the live site. Skipping the f1 step entirely keeps the live root **byte-identical to the recorded core** during the review window.
- **Prod writes stay user-controlled.** The auto-mode classifier blocked direct `aws s3 sync --delete` + invalidation to the prod bucket (production write bypassing the user-push+approval gate). Honored it — the post-deploy MF-fix republish loop ran as **user-executed** `s3 sync` (Option A); a standing allow-rule (Option B) was declined to keep the project's USER-controlled-prod-write posture.

## Decisions explicitly NOT made (deferred)
- **The real cross-origin portal** (`portal.st6weeklycommit.com`, separate distribution) — deferred until the AWS account is CloudFront-verified. The ISSUED portal cert + empty portal bucket partials remain dormant (~$0) for a fast clean re-run if pursued.
- **The standalone main-site switch** — Option B left the live main site as the existing build; switching it to the architecturally-correct standalone artifact is deferred to a deliberate, browser-verified change (not mid-review-window).

## TDD compliance
**N/A (infra is not red-green TDD)** — verified via the validate/policy path per `infra/CLAUDE.md`: `terraform fmt -check` + `init -backend=false` + `validate` + `tflint` clean on every terraform change; `actionlint` clean on `deploy.yml`; `kubeconform -strict` clean on `job-seed-demo.yaml`; confirmed by the deployed-smoke + curl plumbing checks. No violations.

## Reachability (wiring audit — all LIVE + referenced, no orphans)
- **`/portal/*` behavior** — live on the wc. distribution (`E1JEDK39FNL8JC`), references `arn:…:function/wc-aws-portal-spa-rewrite`. ✓
- **`/remote/*` behavior** — live (serves the MF remote). ✓
- **`portal-spa-rewrite` Function** — published (LIVE stage, `cloudfront-js-2.0`). ✓
- **CORS allow-list** (`remote_cors` policy / api `CORS_ALLOWED_ORIGINS` portal entry) — live + referenced; now moot under same-origin but harmless (left). ✓
- **`job-seed-demo.yaml`** — manifest present; the Jun 1–7 Job ran (`wc-seed-demo-2026-06-01`, COMPLETIONS 1). Manual/off-chain by design (run per target week), not orphaned. ✓
- Curl plumbing verified post-deploy: `/portal/` → host index 200, `/remote/assets/remoteEntry.js` → 200, `/portal/callback` → host `/portal/index.html` (rewrite works), root `/` → main WC unchanged.

## Open follow-ups (carry-forward → orchestrator)
- **Future TODO — operational:** **Jun 8–14 seed run** before the external review (`SEED_WEEK=2026-06-08` via the documented `job-seed-demo.yaml` envsubst+kubectl recipe).
- **Future TODO — infra/phase:** **AWS CloudFront account-verification** to enable the real cross-origin `portal.` distribution (re-run is fast — cert already ISSUED).
- **Future TODO — frontend/infra:** **standalone main-site switch** (browser-verified) to serve the architecturally-correct standalone artifact at the wc. root.
- **Future TODO — infra hardening (pre-existing carry-forward):** KMS-pin on the S3/secrets; apex → `wc.` redirect.
- **Convention candidate (lesson):** Deploy-2 Option-B pattern — same-origin federation via an additive path behavior + a viewer-request rewrite Function for per-path SPA fallback; the fail-safe-to-remote main-site build trap; the prod-write-is-user-gated republish loop. (Orchestrator writes any LESSONS entry — not edited here.)

## How to use what was built
- **Run the seed for a week:** `SEED_WEEK=<YYYY-MM-DD> ECR_API_IMAGE=$(kubectl get deploy wc-api -n wc -o jsonpath='{.spec.template.spec.containers[0].image}') AWS_REGION=us-east-1 envsubst '$SEED_WEEK $ECR_API_IMAGE $AWS_REGION' < infra/k8s/job-seed-demo.yaml | kubectl apply -f -` then `kubectl wait --for=condition=complete job/wc-seed-demo-<week> -n wc`.
- **Republish a frontend `/remote/` fix (user-run, prod-gated):** `aws s3 sync apps/wc-web/dist s3://wc-aws-assets-554608989058/remote/ --delete` + `aws cloudfront create-invalidation --distribution-id E1JEDK39FNL8JC --paths "/remote/*"`.
