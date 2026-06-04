# Lead compaction handoff #4 — ST6 (2026-06-04)

> **Type: COMPACTION handoff (compact-in-place, NOT teardown).** The lead hit WARN (71%) at the clean Wave-2-seal boundary. Per [[lead-context-limit-handoff]]: write this, the user compacts the lead in place (same session continues, compacted), teammates stay ALIVE. This is the lead's 4th compaction (prior: 001, 006, 012). Resume from this doc + git + the runbooks. **The user is mid-flow on the FULL deploy (Wave 1 + live Outlook) and wants step-by-step guidance — that's the immediate post-compaction job.**

## Current state (re-verify via git)
- **HEAD `94257e3`** — Wave-2 live-sync COMPLETE round seal. **Tree clean.** No push (pushes are USER-controlled; the user has pushed to gitlab `origin` + created/pushed github `SiWarlock/st6-weekly-commit`).
- **THE ENTIRE DEPLOYED-DEMO IS BUILT + COMMITTED:** Wave-1 (seed V4→V6, datasource, Dockerfiles, bootstrap, secrets-helper, rebuild-Job, standalone SPA build, Auth0 OAuth login) + Wave-2 (real SNS gateway s7 `82fd2ab` → worker SQS consumer s8 `a9cb841` → real MS Graph adapter s9 `becc22a` + §10 fail-safe, all security-clean). Deploy automation (deploy.yml + OIDC), the 3 runbooks (a/b/c, concretized to `SiWarlock/st6-weekly-commit`), all READMEs (root/infra/wc-api/wc-web), and a local docker-compose are all done.
- **The only thing left = the HITL first deploy** (user-driven), which the user is starting now + wants guided.

## Team state (all ALIVE through the compaction)
- **`st6-main-orchestrator` (backend orch):** at 70% WARN, **HOLDING for its cycle** (per lead instruction — do NOT self-cycle). It sealed Wave-2 (`94257e3`). **RESUME ACTION #1: cycle it to fresh** for the deploy-support / live-demo-QA phase. Continuity for the fresh orch: session doc **021** + plan **025** + handoff **015** + the Wave-2-COMPLETE block in MVP_TASKS + LESSONS §43/§44/§45 + HEAD `94257e3`.
- **`st6-main-wc-api-implementer` (backend impl):** FRESH (~31%), persists — deploy support / any deploy-fix slices.
- **`st6-main-infra-implementer`:** persists — **wrote the 3 runbooks; THE deploy expert. Pull it in for live deploy debugging.**
- **`st6-main-wc-web-orchestrator` (frontend orch):** sealed + holding — for the live-demo QA (after the deploy). Frontend impl was retired (respawn for QA fixes).
- **Lead:** 71% WARN → compacting in place.

## RESUME ACTIONS (in order)
1. **Cycle the backend orch to fresh** (it's holding at WARN) for the deploy-support phase — respawn from session 021 + plan 025 + handoff 015 + this doc. (Registry-lifecycle/ghost-cleanup is the LEAD's job — see [[cycle-cleanup-stale-registry]]; a recent glitch had the orch delete a live impl's registry by sid-confusion — orchs FLAG, don't rm.)
2. **GUIDE THE USER THROUGH THE FULL DEPLOY (Wave 1 + live Outlook), step-by-step.** The runbooks are the authoritative guide: **(a)** `docs/runbooks/auth0-tenant-setup.md` (Auth0 + M365/Entra/Graph), **(b)** `fresh-aws-account-to-deploy-ready.md` (bootstrap → domain/Route53 → first `terraform apply` → GitHub `production` Environment + 7 vars), **(c)** `deploy-and-smoke.md` (populate secrets → `gh workflow run deploy.yml`/release tag → approve the gate → smoke). Pull in the **infra impl** for live debugging.
   - **User has:** AWS account ✓, github repo `SiWarlock/st6-weekly-commit` + pushed ✓.
   - **PREREQS to confirm with the user:** ① a **DOMAIN** (hard req — Route53 zone + delegation; I asked, awaiting answer) · ② the **Auth0 tenant** · ③ the **M365 E5 trial + Entra Graph app** (Calendars.ReadWrite + admin consent + client secret) for live Outlook.
   - **The full-deploy outline** (already given to the user): the 8 Wave-1+OAuth steps (github remote → bootstrap → domain/zone → `terraform apply` admin → GitHub Environment+vars → populate auth0 secret → `deploy.yml`+approve → smoke) PLUS the Outlook additions: populate the **`graph`** secret (`populate-secrets.sh --only graph`), set the worker to **`app.graph.mode=real`**, and the **`@st6demo.com` mailbox-domain consideration** (to see real calendar events the 7 personas need real M365 mailboxes — add `st6demo.com` as a verified domain OR point the seed at `<tenant>.onmicrosoft.com`; confirm the cleanest path from runbook a).
3. After a successful deploy: the **live-demo QA** (frontend orch + a fresh frontend impl, real-browser against the deployed app).

## STANDING PROCESS FACTS (in MEMORY.md)
- Deploy is HITL/user-driven; **pushes are USER-controlled** (teammates commit locally, never auto-push). Deploy triggers on a `release-*` tag / `workflow_dispatch` (deliberate). [[lead-handle-is-team-lead]] (teammates address the lead as `team-lead`). [[spawned-teammates-launch-in-home]] (manual-command pattern). Cycle discipline: /session-end SAFE at WARN≤74, SKIP ≥75 (orch captures continuity). [[user-prefers-best-practice-over-pragmatic]] (away-mode tie-breaker when the user's out).
- Next-free handoff after this = 017. Briefs through 095.

## RESUME PROMPT (user sends after compacting)
> Resume the ST6 team lead from `docs/team-handoffs/016-2026-06-04-lead-compaction-handoff-4.md`. The whole deployed-demo (Wave 1 + Wave 2 live Outlook) is built + sealed (HEAD `94257e3`). Resume actions: cycle the backend orch (holding at WARN) to fresh for deploy-support, then GUIDE me through the FULL first deploy (Wave 1 + live Outlook) step-by-step per runbooks a/b/c, pulling in the infra impl (the runbook author) for live debugging. Confirm my domain / Auth0 / M365 status first. Then thin-lead monitoring + the live-demo QA after deploy.
