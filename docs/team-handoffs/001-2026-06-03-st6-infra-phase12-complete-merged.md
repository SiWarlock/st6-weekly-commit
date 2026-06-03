# Team Handoff 001 — st6-infra track: Phase 12 complete, audited, merged with main

**Date:** 2026-06-03
**Track:** st6-infra (infra-track branch, worktree …/ST6-infra)
**Predecessor handoff:** first handoff for this track
**Successor handoff:** _(filled in when the next /team-end runs)_
**Tip commit at handoff:** `6654c52` (merge main→infra-track) · round-seal: `5f8e30a`

## Why this handoff exists
Arc complete — the infra track's entire scope (Phase 12) is authored, validated, comprehensively audited, remediated, sealed, and pre-merged with main. Team is winding down; st6-main lands the branch.

## Team composition at close
- **Lead:** this session (track `st6-infra`).
- **Orchestrator:** `st6-infra-orchestrator` (session `b5b6e7d8`, fresh after the mid-phase cycle) — last commit `6654c52` (merge) / round-seal `5f8e30a`.
- **Implementer:** `st6-infra-implementer` (session `b7662e4c`, fresh after the mid-phase cycle) — last code commit `eb60ee3` (audit remediation B) / session-end `b7f480f`.
- _(Round-1 pair `dbbdb2ea`/`77767f8a` cycled out at the 12.7c boundary on context; round-1 seal `82c3cb0`.)_
- All teammates `/session-end` + `/orchestrate-end` closed; merge committed; **clean close, nothing in-flight.**

## Active arc + where it landed
Phase 12 = AWS IaC (Terraform) + EKS/k8s manifests + GitHub Actions OIDC CI/CD. **All tasks done: 12.1–12.11 + 12.2b** (CSI-driver add-ons gap-fix). Posture throughout: **author + validate only** (`fmt`/`validate`/`tflint`/`kubeconform`/`actionlint` all green) — **real `terraform apply`/deploy is HITL** (needs the user's AWS account). 17 `.tf` files, 13 k8s manifests, `.github/workflows/deploy.yml`, deployed-smoke suite.

Then a **comprehensive ultracode final audit** (33 agents: 3 sub-agent reviewers + 8 IaC dimensions + completeness critic, each adversarially verified) → 21 raw → **11 confirmed / 10 refuted** → all remediated + re-verified green. Then a **prep merge of main→infra-track** (`6654c52`), conflicts resolved in `MVP_TASKS.md` (ARCHITECTURE.md auto-merged), infra gates re-validated green.

## In-flight at close
**None — clean close.**

## What's next (NOT this team's job — st6-main owns it)
1. **st6-main lands `infra-track → main`** at a quiet main boundary, with its pairs briefly quiesced (don't race an in-flight slice commit). After landing: confirm `./gradlew check` + the JS build still green, then resume. *(This team did the prep merge of main→infra-track already; the reverse landing is st6-main's.)*
2. **At landing, renumber the infra `docs/briefs/` + `docs/sessions/`** — both teams independently used `001-`, `002-`… so numbers collide (filenames differ, so they coexist cleanly, but numbering is ambiguous). Cosmetic; st6-main's call.

## HITL deploy checklist (for the human, when ready to actually stand it up)
The IaC is authored + validated but **never applied**. To deploy:
1. **Inputs:** AWS account creds; `ROOT_DOMAIN` (required, no default); `github_repo` (`owner/repo`); `rds_minor_version` (latest available 16.x, ≥16.13); `eks_public_access_cidrs` (defaults `0.0.0.0/0` — tighten if desired, see residual below).
2. **Populate the placeholder secrets** in Secrets Manager: `auth0`, `graph`, `demo` (the `db` secret is wired from RDS; the three others are containers with `ignore_changes` — fill post-apply).
3. **Run:** `terraform init` (S3 backend + DynamoDB lock) → `plan` → `apply`.
4. **Pipeline / deploy:** the GitHub Actions `deploy.yml` runs the full §13 chain (gates → build/push → `tf apply` → migration Job → deploy api/worker+cron → S3 sync + CloudFront invalidation → deployed smoke). It's OIDC-federated (no static keys) and gated by a **`production` GitHub Environment requiring reviewer approval** — you approve each deploy.
5. **Verify:** deployed smoke asserts `https://wc.${ROOT_DOMAIN}` (SPA) + `https://api.wc.${ROOT_DOMAIN}/actuator/health/readiness` over ACM TLS.

## Decisions + accepted residuals (durable: docs/decisions/001 + docs/audits/001)
- **D1 — CI deploy-role posture:** full-apply CI, hardened defense-in-depth (IAM permissions-boundary **guardrail** [`Allow:*` + escalation Denies] + `production` Environment reviewer gate + env-scoped OIDC + `github_repo` var).
- **D2 — api.wc DNS:** external-dns (declarative, self-healing) owns the `api.wc` record; scoped IRSA (`route53:ChangeResourceRecordSets` on the project hosted zone only); recorded in Appendix C.7.
- **D3 — permissions-boundary scope:** Option A — boundary threaded through every TF-created role as a guardrail; self-protection denies close the create-then-assume + self-weakening loops.
- **Accepted residuals (reviewer-gate backstopped):** (#1) the irreducible broad account-wide apply-access of any `terraform apply` role; (#2) EKS public API endpoint default `0.0.0.0/0` (scopeable via `eks_public_access_cidrs`).
- **Audit:** 11 confirmed findings, all remediated (C1 Critical = D3 boundary↔node-role PassRole gap, fixed by renaming node role to `wc-aws-node`; H1 High = migration Job re-apply, fixed by delete-before-apply). Full report + resolutions in `docs/audits/001-phase12-audit.md`.

## Open decisions / blockers for the human
**None blocking.** The only pending action is the HITL deploy (above) + st6-main's landing — both outside this team's scope.

## Spawn prompts ready for the next infra team session
_(Use if the infra track needs to resume — e.g. post-landing fixes, deploy-time issues, or Phase-13 infra/observability bits.)_

**Orchestrator:**
```
You are st6-infra-orchestrator on the ST6 Weekly Commit Module agent team.
Track: st6-infra. Team: st6-infra. Talk directly to st6-infra-implementer; escalate to st6-infra-team-lead only for the 4 categories. Ignore peer DMs without the `st6-infra-` prefix (channel-bleed).
Activated because: resuming the infra track after Phase 12 wind-down (handoff 001). Phase 12 is COMPLETE + merged with main (tip 6654c52). Read handoff docs/team-handoffs/001-2026-06-03-st6-infra-phase12-complete-merged.md + docs/decisions/001 + docs/audits/001 first. Posture: author + validate only; real apply/deploy is HITL. Keep MVP_TASKS.md edits fenced to your phase's section + Log; do NOT touch the top "Currently in progress" pointer (st6-main owns it).
FIRST ACTION — register: mkdir -p ~/.claude/team-registry && jq -n --arg sid "$CLAUDE_CODE_SESSION_ID" --arg name "st6-infra-orchestrator" --arg team "st6-infra" --arg cwd "$(pwd)" --arg ts "$(date -u +%s)" '{session_id:$sid,name:$name,team:$team,role:"orchestrator",cwd:$cwd,ts:($ts|tonumber)}' > ~/.claude/team-registry/${CLAUDE_CODE_SESSION_ID}.json
Then run /orchestrate-start. Confirm start command + registry write in your first reply.
```

**Implementer (`infra`):**
```
You are st6-infra-implementer on the ST6 Weekly Commit Module agent team.
Track: st6-infra. Team: st6-infra. cwd/area: infra/. Talk only to st6-infra-orchestrator; ignore non-`st6-infra-` peer DMs.
Activated because: resuming the infra track after Phase 12 wind-down (handoff 001). Phase 12 COMPLETE + merged. Read infra/CLAUDE.md + infra/LESSONS.md (lessons §1–§N incl. the boundary↔node-role-name + guardrail-vs-allowlist lessons). Posture: author + validate only (terraform validate/tflint/kubeconform/actionlint); NO apply/deploy. Await the orchestrator's brief before starting a slice.
FIRST ACTION — register: mkdir -p ~/.claude/team-registry && jq -n --arg sid "$CLAUDE_CODE_SESSION_ID" --arg name "st6-infra-implementer" --arg team "st6-infra" --arg cwd "$(pwd)" --arg ts "$(date -u +%s)" '{session_id:$sid,name:$name,team:$team,role:"implementer",area:"infra",cwd:$cwd,ts:($ts|tonumber)}' > ~/.claude/team-registry/${CLAUDE_CODE_SESSION_ID}.json
Then run /session-start. Confirm start command + registry write in your first reply.
```

## How to resume
Lead runs `/team-start st6-infra`, reads this handoff + (on demand) `MVP_TASKS.md`, spawns teammates with the prompts above, verifies read-backs. **Note:** the team-paused state is captured HERE, not in `MVP_TASKS.md` "Currently in progress" — that pointer is deliberately left as main's to keep the shared file conflict-free for st6-main's landing.
