# /tdd brief — Wave-2 s9: real MS Graph calendar adapter (+ app-only auth + fail-safe)

> **Likely a fresh implementer's first slice** (the wc-api-impl is cycling at the s8 boundary). Read this brief + LESSONS §43 + §44 + `docs/planning/025` cold; HEAD = s8 `a9cb841`.

## Feature
The real MS Graph calendar adapter behind the s8 `GraphCalendarPort` seam: `@ConditionalOnProperty("app.graph.mode", havingValue="real")` selects it (the s8 `DemoSuccessGraphCalendarPort` stays the default for `demo-success`); it authenticates **app-only / client-credentials** from the graph secret (`GRAPH_TENANT_ID`/`GRAPH_CLIENT_ID`/`GRAPH_CLIENT_SECRET`), resolves the owner's email (`ownerEmployeeId → Employee.email`), and creates the calendar event on that owner's calendar (returning the real `graphEventId`). **Fail-safe (§10/REQ-I-004/REQ-E-007):** in `real` mode with any of the three creds missing/blank, it **degrades to the demo path** — records a safe failure, never crashes the worker or blocks the lifecycle, never logs a secret.

## Use case + traceability
- **Task ID:** Wave-2 s9 (plan `docs/planning/025` → WAVE 2 row 9 + the Wave-2 scoping addendum). **The LAST Wave-2 backend slice** — after this, Wave-2 (live Outlook sync) is complete.
- **Architecture sections it implements:** §10 (Outlook Graph sync + the non-blocking/fail-safe guarantee), Appendix D (`GRAPH_*` env contract + **the fail-safe at the `GRAPH_MODE=real` row**: "with any of the three missing/blank, the Graph adapter degrades to the demo adapter, records a safe failure, surfaces safe_message + manual-retry, and **never blocks the core lifecycle or crashes the worker**"), Appendix F (Graph adapter).
- **Safety rules touched:** **rule #7** (the `GRAPH_*` creds + any Graph error text must NEVER be logged/persisted; secrets via the CSI mount) + **rule #4-adjacent** (a Graph failure lands as `FAILED` + DLQ, never crashes the worker / blocks the lifecycle — the §10 non-blocking guarantee, NEVER-TRIM). → **ad-hoc security review** (general-purpose agent).
- **Related context:**
  - **LESSONS §43** — the real-adapter-behind-a-shipped-no-op-seam pattern (s7 SNS). s9 is the direct application: the s8 stub `@ConditionalOnProperty("app.graph.mode", havingValue="demo-success", matchIfMissing=true)` is already wired; s9 adds the **mutually-exclusive** `havingValue="real"` real adapter. The adapter PROPAGATES on a genuine Graph failure (s8's `SyncMessageListener` catch records `FAILED` + the cause-less rethrow → DLQ); the **fail-safe degrade is the exception** (a config-degradation, not a runtime failure — see Q3).
  - **LESSONS §44** — the s8 consumer that calls this port (idempotent, cause-less sanitized rethrow, the worker datasource). s9 changes only the port impl; the consumer is untouched.
  - **`GraphCalendarPort`** (`worker/.../sync/`, s8) — `String createEvent(OutlookCalendarSyncRecord record)` (returns the `graphEventId`). The s8 idempotent skip means only **create** is ever called — no update path.
  - **`Employee.email`** (`shared/.../employee/Employee.java:34`) + `EmployeeRepository` (`com.st6.wc.employee.repo`) — the owner-email source. **The worker's `@EnableJpaRepositories` is currently `com.st6.wc.sync.repo` only (s8)** → s9 must widen it to include `com.st6.wc.employee.repo` (see Q4).
  - **`OutlookCalendarSyncRecord`** — carries `ownerEmployeeId`, `relatedType`/`relatedId` (the plan), `eventKind`, `weekStartDate` — what the worker reads to build the calendar event title/time. No new fields.
  - **`docs/runbooks/auth0-tenant-setup.md`** Part 2 — the M365 E5 + Entra app-reg that produces the `GRAPH_*` creds + the `Calendars.ReadWrite` app permission. For a `real`-mode demo, the 7 personas' `Employee.email` must map to the M365 trial users.

## Acceptance criteria (what "done" means)
- [ ] A real `GraphCalendarAdapter implements GraphCalendarPort`, `@ConditionalOnProperty("app.graph.mode", havingValue="real")` — mutually exclusive with the s8 demo stub (exactly one port bean per `app.graph.mode`; `demo-success`/absent → stub, `real` → adapter).
- [ ] App-only client-credentials auth: a `ClientSecretCredential` (or equivalent) built from `GRAPH_TENANT_ID`/`GRAPH_CLIENT_ID`/`GRAPH_CLIENT_SECRET` (bound via the CSI configtree mount the worker already has for graph); a `GraphServiceClient` created from it.
- [ ] `createEvent(record)` resolves `ownerEmployeeId → Employee.email` (via `EmployeeRepository`) and creates a calendar event on that user's calendar (`/users/{email}/calendar/events` or the SDK equivalent), returning the real Graph event id.
- [ ] **Fail-safe (§10/REQ-I-004/REQ-E-007) — load-bearing:** in `real` mode with any of the 3 creds missing/blank, the adapter degrades to the demo behavior (or records a safe failure) — it does NOT crash the worker, does NOT block, does NOT throw an unhandled startup error, and NEVER logs/persists a secret. (See Q3 — degrade-at-startup vs degrade-per-call.)
- [ ] **Rule #7:** the `GRAPH_*` creds never appear in a log, the persisted `failureCode`/`safeMessage`, or an exception message; a Graph API error is caught + mapped to the s8 fixed non-PII `failureCode`/`safeMessage` (never the raw Graph error body, which may carry calendar/attendee PII) — the adapter throws a clean exception that s8's listener sanitizes (the §44 cause-less rethrow handles the DLQ logging).
- [ ] Tests **mock the `GraphServiceClient`** (NO live Graph call in tests): the create path (returns id), the email-resolution, the fail-safe degrade (missing creds), the rule-#7 no-secret-leak.
- [ ] `./gradlew check` clean from `apps/wc-api/`; ad-hoc rule-#7 security review PASS.

## Files expected to touch
**New (worker):**
- `worker/.../sync/GraphCalendarAdapter.java` — the real `GraphCalendarPort` impl (`@ConditionalOnProperty(... havingValue="real")`).
- `worker/.../sync/GraphClientConfig.java` (or similar) — the `ClientSecretCredential` + `GraphServiceClient` bean (also `@ConditionalOnProperty(... havingValue="real")`), reading `GRAPH_*` from config.
- Tests: the adapter (create + email-resolution + the fail-safe degrade + rule-#7 no-leak), mocking the Graph client; a port-selection test (`real` → adapter, `demo-success`/absent → stub).

**Modified (worker):**
- `worker/.../config/WorkerSharedConfig.java` — widen `@EnableJpaRepositories` to add `com.st6.wc.employee.repo` (the owner-email lookup) — e.g. `@EnableJpaRepositories({"com.st6.wc.sync.repo", "com.st6.wc.employee.repo"})` (see Q4).
- `worker/build.gradle` — add `com.microsoft.graph:microsoft-graph` + `com.azure:azure-identity` (verify current versions via Context7 + the registry at author time — greenfield deps; pin in `libs.versions.toml` per the s7/s8 convention).
- `worker/src/main/resources/application-aws.yml` — bind `GRAPH_*` (already on the configtree mount via the graph secret; confirm the property names the adapter reads).
- Possibly `application.yml` — the `app.graph.mode` default already exists (`${GRAPH_MODE:demo-success}`, s8); no change unless the adapter needs more config.

If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline (Step 2)
1. **`realMode_createsEventAndReturnsId`** — `app.graph.mode=real` + a mock `GraphServiceClient` that returns an event with id `evt-123`; `createEvent(record)` resolves the owner email + calls the client + returns `evt-123`.
2. **`resolvesOwnerEmailFromEmployee`** — the adapter looks up `Employee.email` by `record.getOwnerEmployeeId()` and addresses the Graph call to it (mock the repo).
3. **`missingCreds_degradesToDemo_neverThrows`** — `real` mode + a blank `GRAPH_CLIENT_SECRET` → the fail-safe degrades (demo behavior / safe failure), no crash, no unhandled throw, no secret logged. (§10/REQ-I-004 — the load-bearing safety test.)
4. **`graphError_isNonPII`** — the Graph client throws an error carrying PII (`"event for john.doe@acme.com 'Q3 OKRs' rejected"`) → the adapter does NOT log/persist it; it throws a clean exception s8's listener sanitizes (assert no PII propagates; reuse the §44 pattern teeth).
5. **Port selection** — `real` → `GraphCalendarAdapter`; `demo-success`/absent → `DemoSuccessGraphCalendarPort` (exactly one, `ApplicationContextRunner`).

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** **NONE** — `OutlookCalendarSyncRecord`/`Employee`/`SyncJobPointer` unchanged.
- **Orchestrator doc rows (Step 9):** **candidate** — an ARCHITECTURE §10/Appendix-F note that the real Graph adapter + the fail-safe are live (fold with the s7/s8 §10 notes at round-seal). A LESSONS candidate if the client-credentials/fail-safe pattern surfaces something beyond §43/§44.

## Things to flag at Step 2.5
1. **Graph SDK + version.** `com.microsoft.graph:microsoft-graph` (the v6+ Kiota-based SDK) + `com.azure:azure-identity` (`ClientSecretCredential`). **Verify the current versions + the client-credentials + `/users/{id}/calendar/events` create API shape via Context7 at author time** (greenfield; pin in `libs.versions.toml`). Flag if the SDK's API differs from the brief's assumption.
2. **Port selection — mirror §43/s8.** `@ConditionalOnProperty("app.graph.mode", havingValue="real")` on the adapter (the stub is already `havingValue="demo-success", matchIfMissing=true`). Default: yes — mutually exclusive, exactly-one, pinned by a selection test. The `GraphServiceClient` bean is likewise `real`-gated (so `demo-success`/test contexts never construct a real client → no boot-time credential/network need).
3. **Fail-safe: degrade-at-startup vs degrade-per-call?** §10/REQ-I-004 says missing/blank creds in `real` mode → degrade to demo, never crash/block. **My default vote: degrade at bean-creation** — if `real` but any cred is blank, the `GraphClientConfig` logs a safe warning (no secret) + the adapter falls back to the demo behavior (or the worker wires the demo stub instead). The alternative (per-call degrade) keeps the real bean but catches the auth failure each message. Either satisfies §10; pick the one that's cleanest to test deterministically (the startup-degrade is more visible + testable). Confirm.
4. **`@EnableJpaRepositories` widening.** Add `com.st6.wc.employee.repo` to the worker's `@EnableJpaRepositories` (currently `com.st6.wc.sync.repo` only). Default: the explicit 2-package list `{"com.st6.wc.sync.repo", "com.st6.wc.employee.repo"}` (least-beans — only the 2 repos the worker uses) over a broad `com.st6.wc` (which would activate all 14 repos as beans). Confirm.
5. **Testing the Graph call — mock the client, no live call.** Default: inject the `GraphServiceClient` (mockable) so tests verify the create-call + the email-resolution + the fail-safe WITHOUT a live Graph call (the project has no live-Graph test path; the demo runs on `demo-success`). The real Graph call is exercised only in a real `GRAPH_MODE=real` deploy (HITL, post-M365-setup).
6. **Owner-email → Graph user mapping.** `Employee.email` = the M365 user's email/UPN (the runbook's 7 M365 trial users map to the 7 personas' emails for a real demo). For `demo-success` no mapping is needed (synthetic id). Confirm the adapter addresses the Graph call by `Employee.email`.

## Dependencies + sequencing
- **Depends on:** s8 (`a9cb841` — the `GraphCalendarPort` seam + the consumer + the worker datasource); the graph secret + IRSA (already mounted, `adfa639`); the M365/Entra creds (HITL, runbook a Part 2 — only needed for a `real`-mode deploy).
- **Blocks:** nothing — **this completes the Wave-2 live-sync pipeline.** After s9, the deployed worker can do real Outlook calendar sync when `GRAPH_MODE=real` + creds are populated.
- **Infra:** none expected (the graph secret + the worker configtree + IRSA are already in place from `adfa639` + the existing SPC graph object — confirm at Step 2.5; flag if the adapter needs a config not on the mount).

## Estimated commit count
**1 — and do NOT bundle.** Rule-#7 + the §10 fail-safe → its own commit + ad-hoc security review. The adapter + the auth config + the selection + the fail-safe + the email-resolution are one cohesive Graph-integration unit.

## Lessons-logged candidates anticipated
- **Convention candidate** — app-only client-credentials Graph integration behind the §43 seam + the §10 fail-safe-degrade (missing creds → demo, never crash/block, never log a secret); the mock-the-client test approach (no live Graph in tests).
- **Architecture-doc note** — §10/Appendix-F: real Graph adapter + fail-safe live; Wave-2 complete.

## How to invoke
1. Read this brief + LESSONS §43 + §44 + plan 025 (cold-start if you're the fresh impl).
2. `/tdd wave2-graph-calendar-adapter`.
3. Step 0 (Restate) → confirm against the Feature line.
4. Step 1 → confirm files (note the `@EnableJpaRepositories` widening + the new Graph deps).
5. Step 2.5 → the 6 answers (esp. Q1 SDK version, Q3 fail-safe shape) — or defaults.
6. Step 9 → categorized summary + draft commit; I run the ad-hoc rule-#7 + §10-fail-safe security review at Step-7→8.
