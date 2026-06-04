# /tdd brief — Wave-2 s7: real SNS lifecycle gateway

> **Number provisional (093)** — next-free after 092; confirming against the frontend orch's brief-lane reply before dispatch.

## Feature
Replace the no-op `LoggingLifecycleSnsGateway` with a real `LifecycleSnsGateway` that publishes the `SyncJobPointer` to the deployed SNS topic (`${SNS_TOPIC_ARN}`) in the `aws` deployment, while keeping the logging stub for local/demo/test. The publisher orchestration (afterCommit, `REQUIRES_NEW`, swallow-on-failure, `PENDING_PUBLISH → QUEUED`) is **already built** — this slice only supplies the real publish behind the existing seam.

## Use case + traceability
- **Task ID:** Wave-2 s7 (deploy-demo plan `docs/planning/025` → "WAVE 2" table row 7 + the Wave-2 scoping addendum).
- **Architecture sections it implements:** §10 (Outlook sync transport — SNS→SQS), Appendix F.2 (`SyncJobPointer` wire payload), §12/Appendix D (deploy topology + the `SNS_TOPIC_ARN` env).
- **Safety rules touched:** **rule #4** (sync failure never blocks/rolls back the core lifecycle txn) + **rule #7** (pointer-only payload; no bodies/secrets/PII). → **ad-hoc security review** (general-purpose agent — the `security-reviewer` subagent isn't registered).
- **Related context:**
  - **Carry-forward** "Real AWS SNS gateway replaces `LoggingLifecycleSnsGateway`" (origin 3.5) — this slice discharges it.
  - **The seam (verified, scoping pass):** `LifecycleSnsGateway` (interface, `api/sns/`) = `void publish(SyncJobPointer)`. `SnsLifecyclePublisher` (`@Service`) is the single publish path — `@Transactional(REQUIRES_NEW) publish(UUID syncRecordId)` loads the record, calls `gateway.publish(pointer)`, flips `PENDING_PUBLISH → QUEUED` + `queuedAt`, and **catches `RuntimeException` → leaves `PENDING_PUBLISH` (rule #4), logs ids-only (rule #7)**. The afterCommit hook is registered at `PlanLifecycleService:299–308`. **None of this changes.**
  - **`SyncJobPointer`** (`shared/.../sns/payload/`) is already the exact F.2 4-field record `{syncRecordId, eventKind, env, traceId}` — pointer-only by construction. **No payload change.**
  - **Spring Cloud AWS** (confirmed via Context7): `io.awspring.cloud:spring-cloud-aws-starter-sns` → `SnsTemplate.sendNotification(topicArn, object, subject)` JSON-serializes the object (Jackson). Same library family as the worker's future `@SqsListener` (s8) + the same IRSA credential chain.

## Acceptance criteria (what "done" means)
- [ ] A real `LifecycleSnsGateway` impl publishes the JSON-serialized `SyncJobPointer` to the configured topic ARN (via `SnsTemplate.sendNotification`).
- [ ] **Exactly one** `LifecycleSnsGateway` bean is active: the real gateway when the topic ARN is configured (deployed `aws`), the logging stub otherwise (local/demo/test) — proven by an `ApplicationContextRunner` bean-selection test.
- [ ] **Rule #4 — the gateway PROPAGATES on failure (never swallows internally).** An SNS publish failure throws a `RuntimeException` out of `gateway.publish` → the existing `SnsLifecyclePublisher` is the single swallow point → record stays `PENDING_PUBLISH`. (Pin: the gateway does NOT try/catch; the publisher's existing swallow test still holds. Do NOT add a second swallow in the gateway — that would hide failures from the retry posture.)
- [ ] **Rule #7 — pointer-only.** Only the `SyncJobPointer` 4 fields cross the wire; no calendar body, secret, note, or PII. (Pin: assert the published payload is the serialized pointer; no enrichment.)
- [ ] `api/build.gradle` adds the spring-cloud-aws BOM + SNS starter (version-verified at author time — see Q3).
- [ ] `./gradlew check` clean from `apps/wc-api/`; the existing `PlanLifecycleServiceTest`/`StartReconciliationServiceTest`/`CloseReconciliationServiceTest` (which `@MockBean` the gateway) stay green.
- [ ] Ad-hoc security review PASS (rule #4 non-blocking + rule #7 pointer-only).

## Files expected to touch
**New:**
- `api/src/main/java/com/st6/wc/sns/SnsLifecycleGateway.java` (name TBD — e.g. `AwsSnsLifecycleGateway`) — the real impl wrapping `SnsTemplate`; conditional-gated (Q2); reads the topic ARN (Q3).
- `api/src/test/java/com/st6/wc/sns/SnsLifecycleGatewayTest.java` — publishes via a mock `SnsTemplate` (verify `sendNotification(topicArn, pointer, …)`); failure-propagation (template throws → gateway propagates).
- `api/src/test/java/com/st6/wc/sns/LifecycleSnsGatewaySelectionTest.java` — `ApplicationContextRunner` bean-selection (real when ARN present, stub when absent; exactly one bean).

**Modified:**
- `api/src/main/java/com/st6/wc/sns/LoggingLifecycleSnsGateway.java` — make it the fallback (`@ConditionalOnMissingBean(LifecycleSnsGateway.class)` — Q2); drop the bare `@Component`.
- `api/build.gradle` — spring-cloud-aws BOM (`platform("io.awspring.cloud:spring-cloud-aws-dependencies:<3.x>")`) + `io.awspring.cloud:spring-cloud-aws-starter-sns`.
- `api/src/main/resources/application-aws.yml` — bind `app.sns.topic-arn: ${SNS_TOPIC_ARN}` (Q3); the conditional + gateway read this one property.

If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline (Step 2)
1. **`publishesPointerToTopic`** — given a real gateway with a mock `SnsTemplate` + a configured ARN, `publish(pointer)` calls `sendNotification(<arn>, <the SyncJobPointer>, …)`.
   - Asserts: the template is invoked with the exact ARN + the pointer object (Jackson-serialized); no enrichment (rule #7).
2. **`propagatesOnSnsFailure`** — when `SnsTemplate.sendNotification` throws, `gateway.publish` propagates the exception (does NOT swallow).
   - Why: rule #4's single swallow point is `SnsLifecyclePublisher`; a gateway-internal catch would hide failures from the `PENDING_PUBLISH`-retry posture (§28).
3. **`realGatewayActiveWhenArnPresent` / `stubActiveWhenArnAbsent`** (`ApplicationContextRunner`) — exactly one `LifecycleSnsGateway`; the real impl with the ARN set, the logging stub without.
4. **(Publisher regression — already green, assert unbroken)** — `SnsLifecyclePublisher`'s existing swallow test (template/gateway throws → record stays `PENDING_PUBLISH`, no rethrow) stays green with the real gateway wired behind the mock.

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** **NONE** — `SyncJobPointer` (F.2) unchanged; no DTO/enum/entity change.
- **Orchestrator doc rows to write hot (Step 9 routing):** none required. **Candidate (low):** an ARCHITECTURE §10 note that the real SNS gateway is wired (the no-op→real swap) — orchestrator's call at Step 9; likely fold into the Wave-2 §10 narrative rather than a standalone edit.

## Things to flag at Step 2.5
1. **`SnsTemplate` (spring-cloud-aws) vs raw `SnsClient` (AWS SDK v2)?** **Default: `SnsTemplate`.** Auto-configured, built-in Jackson serialization of the pointer record, and consistent with the worker's s8 `@SqsListener` (same library + IRSA credential chain). Raw `SnsClient` is the fallback if we want to avoid the auto-config — but the consistency + serialization win favors `SnsTemplate`.
2. **Bean selection — `@ConditionalOnProperty(app.sns.topic-arn)` on the real + `@ConditionalOnMissingBean` on the stub, vs `@Profile("aws")`?** **Default: property-conditional.** Gate the real gateway on the topic-ARN property being present; make the logging stub `@ConditionalOnMissingBean(LifecycleSnsGateway.class)`. This is more robust than `@Profile("aws")` — a test (or any env) without a real topic keeps the stub automatically, so no `@SpringBootTest` accidentally tries a live publish.
3. **Topic-ARN binding + version.** Bind `app.sns.topic-arn: ${SNS_TOPIC_ARN}` in `application-aws.yml` (keep the env-var name at the config edge; the gateway + conditional read the one property). **Version:** pin the spring-cloud-aws **3.x** BOM compatible with Spring Boot 3.3 — **verify the exact patch at author time** against the awspring compatibility matrix (the 3.2.x/3.3.x line); the BOM makes the starter version-less. Default both.
4. **APP_ENV pointer-`env` gap (INFRA seam — not this code slice).** `SnsLifecyclePublisher` reads `@Value("${app.env:local}")` for the pointer's `env` field, and `deployment-api.yaml` does **not** set `APP_ENV`/`app.env` → the deployed pointer's `env` would be `"local"`. **The fix is an infra wiring item** (set `APP_ENV=aws` in `deployment-api.yaml` + worker), not gateway code. Confirm we leave it to the paired infra seam (I'm tracking it). Default: yes — out of this slice's code scope; flagged to the infra impl.

## Dependencies + sequencing
- **Depends on:** the seam (3.5, shipped) — no dep on 092 (different module: `api/sns` vs `worker`).
- **Blocks:** s8 (worker SQS consumer reads the published pointer) — though s8's queue wiring is independent; s7 makes the publish real end-to-end.
- **Paired infra seam:** `APP_ENV=aws` in `deployment-api.yaml` (Q4); confirm the api IRSA grants `sns:Publish` on the topic ARN (`iam_irsa.tf`).

## Estimated commit count
**1 — and do NOT bundle.** This is a rule-#4/#7-touching slice → its own commit + ad-hoc security review (per the reviewer policy + the "never bundle a safety-critical slice" rule). The dep add + the gateway + the conditional + tests are one logical unit.

## Lessons-logged candidates anticipated
- **Convention candidate** — real-publish-behind-a-shipped-no-op-seam: the impl swaps ONLY the gateway; the non-blocking orchestration (afterCommit + `REQUIRES_NEW` + single-swallow) was built test-first at 3.5 and is reused untouched. The gateway must PROPAGATE (single swallow point at the publisher) — a Wave-2 realization of §28.
- **Architecture-doc note candidate (low)** — §10 SNS gateway now real.
- **Future TODO — operational** — the `app.env` pointer-field wiring (infra) + the `sns:Publish` IRSA grant verification.

## How to invoke
1. Read this brief end-to-end (esp. Step-2.5 Q1/Q2 — library + bean selection; Q4 is an infra seam, not code).
2. `/tdd wave2-real-sns-gateway` in the implementer session.
3. Step 0 (Restate) → confirm against the Feature line.
4. Step 1 (Identify files) → confirm against Files expected to touch.
5. Step 2.5 → ping back with the 4 answers (or defaults).
6. Step 9 → categorized summary + draft commit message; I run the ad-hoc security review at the Step-7→8 boundary.
