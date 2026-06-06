# /tdd brief — human-friendly Graph event subject + the §10 deep-link body (close the ARCH:1012 spec gap) — demo-polish, rides the next polish deploy

## Feature
Make the synced Outlook calendar event presentable: a **human-friendly subject** ("Weekly Commit — Week of Jun 1–7") and the **deep-link body the architecture already specifies but was never implemented** (`ARCHITECTURE.md:1012` — `app.outlook.frontend-base-url`/`WC_FRONTEND_BASE_URL` → IC events `{base}/weekly-commit[/history/{planId}]`, review-block `{base}/manager/command-center`). Closing a real §10 spec gap, NOT gold-plating.

## Use case + traceability
- **Task ID:** graph-event-content-polish (lead-approved demo-polish; the proven live sync produced a bare `IC_PLANNING — week of 2026-06-01` subject + empty body — see the live `340d7e3b` re-drive success). NOT demo-blocking (the headline sync is proven); rides the **next "polish" deploy** alongside the chevron fix — NOT federation Deploy 2.
- **Architecture:** **§10** (Outlook sync) + **`ARCHITECTURE.md:1012`** (the `WC_FRONTEND_BASE_URL` deep-link contract — currently spec'd-but-unimplemented). **LESSONS §45** (the GraphCalendarAdapter/MsGraphEventGateway seam) + the rule-#7 posture.
- **Safety:** rule #7 (no PII/secrets/notes/OKR/commitment text in the Graph payload). The new content stays PII-free by construction (generic label + a URL of `{base}` + the planId UUID) — **pin it with a leak test** (the teeth).

## Current state (the gap)
- `GraphCalendarAdapter.buildSpec`: `subject = eventKind + " — week of " + weekStartDate` (= "IC_PLANNING — week of 2026-06-01", technical). **No body built.**
- `CalendarEventSpec(String subject, Instant start, Instant end, UUID recordId)` — **no `body` field.**
- `MsGraphEventGateway`: `event.setSubject` + `setStart` + `setEnd` + `setTransactionId` — **no `setBody`.**
- The worker carries only the sync record (`ownerEmployeeId`, `eventKind`, `weekStartDate`, `relatedType`, `relatedId`=planId for IC) + the employee email — **no plan/commitment/SO repos** (deliberately thin; do NOT add them — that's the rejected commitment-listing tier).

## Design
1. **Human subject (per eventKind):**
   - `IC_PLANNING` → "Weekly Commit — Week of `<Mon D–D>`" (e.g. "Jun 1–7").
   - `IC_RECONCILIATION` → "Weekly Commit — Reconciliation — Week of `<…>`".
   - `MANAGER_REVIEW_BLOCK` → "Manager Review — Week of `<…>`".
   Human-format the week range (`weekStartDate` … `weekStartDate+6`). **No owner name/email/commitment/OKR text** — rule #7 holds (still generic-by-construction).
2. **`CalendarEventSpec` gains a `body` field** → `record CalendarEventSpec(String subject, String body, Instant start, Instant end, UUID recordId)` (update the javadoc — it now carries a generic body + a deep-link URL, still no PII/commitment text).
3. **`MsGraphEventGateway.setBody`** — `event.setBody(...)` with an `ItemBody` (HTML or Text content type; the deep-link as a clickable link if HTML, a plain URL if Text — your call, HTML is nicer for the demo).
4. **The deep-link (per ARCH:1012), by eventKind:**
   - IC (`IC_PLANNING`/`IC_RECONCILIATION`) → `{base}/weekly-commit/history/{relatedId}` (relatedId = planId; the worker has it). (`/weekly-commit` plain is acceptable if `/history/{planId}` routing isn't confirmed — pick + justify.)
   - `MANAGER_REVIEW_BLOCK` → `{base}/manager/command-center`.
   - `{base}` = the new config (below). A short generic line is fine ("Open your weekly plan: <link>").
5. **Config `app.outlook.frontend-base-url`** (`WC_FRONTEND_BASE_URL`) injected into the adapter (`@Value`/`@ConfigurationProperties`); add to the worker's `application.yml` with a **resolvable default** (`${WC_FRONTEND_BASE_URL:https://wc.${ROOT_DOMAIN:localhost}}` or a literal default) — **§48/§7 discipline: a no-default placeholder crashes the non-serving Jobs that load the profile.** Confirm the worker has `ROOT_DOMAIN` (it may not — the infra companion sets `WC_FRONTEND_BASE_URL` explicitly, so the yaml default is the local-dev fallback only).

## Infra companion (paired — lead-directed)
Wire **`WC_FRONTEND_BASE_URL`** (= `https://wc.${ROOT_DOMAIN}`) on the **worker Deployment** env (`infra/.../deployment-worker.yaml`) so it's set with the polish deploy. I'll route a small paired infra item to st6-main-infra-implementer (held until the polish deploy, brief-105 pattern). Until set, the yaml default applies (so the worker still boots — §48).

## Acceptance criteria
- [ ] The IC_PLANNING event has a human subject ("Weekly Commit — Week of …") + a body containing the `{base}/weekly-commit…/{planId}` deep-link; the review-block → `/manager/command-center`.
- [ ] **Rule-#7 leak test (teeth):** the subject + body contain **no** owner name, owner email, commitment title, or SO/OKR text — only the generic label + the week + the `{base}`-URL + the planId UUID. Assert against a record/employee seeded with sentinel PII (the subject/body `doesNotContain` the sentinels).
- [ ] No new worker repos / no plan-commitment-SO loading (the worker stays thin — the rejected tier).
- [ ] The config has a resolvable default (no non-serving-Job boot crash — §48); `./gradlew check` green all 3.

## Things to flag at Step 2.5
1. **HTML vs Text body** (HTML = a clickable link, nicer demo; Text = a plain URL). Pick.
2. **`/weekly-commit/history/{planId}` vs plain `/weekly-commit`** — confirm the frontend route exists for the history form, else use plain.
3. **Does the worker have `ROOT_DOMAIN`?** If not, the yaml default must be a literal or the infra `WC_FRONTEND_BASE_URL` is required at deploy (it is anyway). Confirm the §48 no-crash default.
4. Security: this is rule-#7-adjacent (Graph payload) but PII-free by construction (config + UUID + generic). The **leak test is the teeth**; I judge **no full security-reviewer needed** — confirm your read at Step-2.5 (if any data-loading creeps in, flag it).

## Cross-doc invariant impact
- **`CalendarEventSpec` gains a `body` field** — it's an internal worker DTO (not a contract/Appendix-B DTO), so no Appendix edit; update its javadoc. **Orchestrator doc routing (Step 9):** a LESSONS note / **§45-addendum** — *the §10 deep-link body (ARCH:1012 `WC_FRONTEND_BASE_URL`) realized; the event content stays rule-#7-clean (generic + URL + planId UUID, no commitment/OKR text); confirm the spec'd-but-unimplemented config was the gap.* I'll route at Step 9. Confirm whether `app.outlook.frontend-base-url` was already defined (unused) or net-new.

## Dependencies + sequencing
- **Depends on:** the deployed worker (104, live). **Pairs with:** the infra `WC_FRONTEND_BASE_URL` companion. **Rides:** the next polish deploy (with the chevron fix), NOT federation Deploy 2. **After it lands:** the lead re-fires a sync (re-drive or a chosen beat) to produce a clean linked event (the existing sam.carter event is bare/pre-fix).

## Estimated commit count
**1.** Adapter subject+body + `CalendarEventSpec.body` + gateway `setBody` + config + tests (incl. the leak test). **No security-reviewer** (rule-#7-clean by construction; the leak test is the teeth — confirm at Step-2.5). Conventional commit (e.g. `feat(wc-api): human Graph event subject + §10 deep-link body (ARCH:1012)`). Don't push. Hash to me at done-with-slice.

## How to invoke
1. **Read this brief + `ARCHITECTURE.md:1012` (the deep-link contract) + LESSONS §45.**
2. Pre-flight: `GraphCalendarAdapter.buildSpec`, `CalendarEventSpec`, `MsGraphEventGateway`, the worker's config (`application*.yml`), confirm `relatedId`=planId for IC + whether `app.outlook.frontend-base-url` is defined.
3. **Run `/tdd graph-event-content`.**
4. Step 0/1 → **Step 2.5** (the eventKind→subject+deep-link mapping + HTML/Text + the §48 default + the leak-test design; wait for my header).
5. GREEN → Step 9 commit-message-first → hash. Flag the §45-addendum + confirm the infra-companion need.
