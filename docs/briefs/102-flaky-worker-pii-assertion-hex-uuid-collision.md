# /tdd brief — harden the flaky `:worker` PII-sanitization assertions (random-UUID vs hex-substring collision) — deploy-support, NON-BLOCKING

## Feature
Make the two `:worker` rule-#7 PII-sanitization assertions **deterministic** without weakening their teeth. Deploy #10's `gates` job failed intermittently on `GraphCalendarAdapterTest.graphError_isSanitizedToCleanNonPiiException()` line 130 (`.hasMessageNotContaining("403")`). It is a **flaky TEST**, not a regression and not caused by #9 — the worker code is byte-identical across runs #8/#9/#10 and passed twice.

## Use case + traceability
- **Task ID:** deploy-fix-flaky-worker-test (live-deploy CI hardening; lead-routed). **NON-BLOCKING** (the lead re-triggered deploy #11 on the same `fac73c8`). **Rule-#7-adjacent** — these assertions guard the §44/§10 PII-sanitization contract; the fix must STRENGTHEN, never weaken, them.
- **LESSONS:** §44 (the cause-less, ids-only sanitized exception + "pin with teeth"), §48 (audit the whole class in one slice — there are TWO sites, not one).

## Root cause (orch-verified against the code — NOT parallelism / mock-leak / ordering)
The sanitized exception message is **ids-only and embeds the record's UUID**:
- `GraphCalendarException(UUID)` → `super("Graph calendar create failed for syncRecord=" + syncRecordId)`.
- `SyncProcessingException(UUID)` → `super("Sync processing failed for syncRecord=" + syncRecordId)`.

Both tests seed the record id as a **`UUID.randomUUID()`** (`GraphCalendarAdapterTest:47`, `SyncMessageListenerTest:44`) and then assert `.hasMessageNotContaining("403")`. **`"403"` is pure hex** (`4`,`0`,`3` ∈ `[0-9a-f]`) → it can appear by chance as a substring of a random 32-hex-digit UUID (~0.5% per run; ~22 candidate start positions × `(1/16)^3`). The OTHER two asserted tokens — `john.doe@acme.com` and `Q3 OKRs` — contain non-hex characters (`@ . j o h n`, `Q`, space, capitals) and can **never** appear in a UUID, so they never flake. That's exactly why ONLY the `"403"` line fails, and only intermittently. The mock setup, the adapter sanitization, and the production message are 100% deterministic and **correct** — the production code stays untouched.

**Two sites (audit-the-whole-class, §48):**
1. `worker/src/test/java/com/st6/wc/worker/sync/GraphCalendarAdapterTest.java:130` — failed in #10.
2. `worker/src/test/java/com/st6/wc/worker/sync/SyncMessageListenerTest.java:104` — **latent**, identical pattern (same ~0.5% chance; could bite a future run, possibly the OTHER one in #11).

## Acceptance criteria
- [ ] Both `graphError_isSanitizedToCleanNonPiiException` (GraphCalendarAdapterTest) and `graphFailure_recordsFailedAndThrows` (SyncMessageListenerTest) are **deterministic** — they pass for ANY record id, including a worst-case id that contains `"403"`.
- [ ] The rule-#7/§44 teeth are **preserved or strengthened** — the test still proves the sanitized message carries NONE of the raw Graph error (no attendee email, no OKR/commitment text, no status code) and the exception is still `hasNoCause()` + the correct sanitized type.
- [ ] **No production change** (`GraphCalendarAdapter`/`GraphCalendarException`/`SyncMessageListener`/`SyncProcessingException` untouched) — this is a test-only hardening.
- [ ] **No test-infra change** — do NOT touch Gradle fork config / mock isolation / test ordering (the lead's initial hypothesis; the real cause is the random UUID, so none of that is involved).
- [ ] `./gradlew check` green all 3 modules; re-run `:worker:test` several times (or with a forced worst-case id) to confirm determinism.

## Recommended fix (Step-2.5 — pick + justify)
**Primary (strongest + simplest): replace the three `.hasMessageNotContaining(...)` with one exact-message assertion** — e.g. `.hasMessage("Graph calendar create failed for syncRecord=" + r.getId())` (and the SyncProcessingException analogue). An exact match is **strictly stronger** than three substring-absence checks (it proves the message is EXACTLY the ids-only string → contains nothing from the raw error, "403" included) AND is fully deterministic regardless of the UUID. Appropriately brittle: any future change to the ids-only message wording SHOULD break a rule-#7 guard and get re-reviewed.

**Alternative (if you prefer to keep the explicit "these PII tokens are absent" intent):** pin the record id to a fixed constant UUID with no `"403"` substring (e.g. `UUID.fromString("11111111-1111-4111-8111-111111111111")`) and keep the `notContaining` lines. Weaker than exact-match (still only asserts absence of the 3 chosen tokens) but documents intent. If you keep `notContaining`, the pinned id is REQUIRED (else still flaky).

Either way: apply to BOTH sites.

## RED → GREEN (prove the diagnosis, then fix)
1. **RED (deterministic repro):** temporarily seed the record id with a UUID that CONTAINS `"403"` (e.g. `UUID.fromString("00000403-0000-4000-8000-000000000000")`) in `graphError_isSanitizedToCleanNonPiiException` → the current `.hasMessageNotContaining("403")` **fails deterministically** (proves the random-UUID-collision diagnosis, not parallelism). Do the same for `SyncMessageListenerTest:104`.
2. **GREEN:** apply the chosen fix (exact-message, or pinned non-colliding id) → both pass even with the worst-case `"403"`-containing id.
3. **Verify:** `./gradlew check` green; re-run `:worker:test` a handful of times.

## Cross-doc invariant impact
- **Model changes:** none. **Orchestrator doc routing (Step 9):** optionally a one-line LESSONS note / **§44-addendum** — *a PII-sanitization assertion that does `notContaining(<token>)` against an ids-only message embedding a random UUID flakes when the token is pure hex (e.g. a status code "403"); assert the EXACT ids-only message instead (stronger + deterministic), or pin the id.* I'll route it at Step 9 if you surface it.

## Dependencies + sequencing
- **Depends on:** nothing. **Blocks:** nothing (NON-BLOCKING — #11 is running on the verified `fac73c8`). Becomes mildly urgent ONLY if #11 also flakes on a `"403"` assertion and the lead is waiting.

## Estimated commit count
**1.** A test-only hardening at two sites. **No security-reviewer needed** (the change STRENGTHENS the rule-#7 assertion; no production/safety surface touched). Conventional commit (e.g. `test(wc-api): deterministic worker PII-sanitization assertions (flaky #403/UUID collision)`); **do NOT push** — the lead pushes. Ping the orch the hash at done-with-slice.

## How to invoke
1. **Read this brief end-to-end.**
2. Pre-flight: confirm the two message constructions (`GraphCalendarException` / `SyncProcessingException`) + the two `randomUUID()` seeds (test lines noted above).
3. **Run `/tdd flaky-worker-pii-assertion`** (or treat as a focused bug-hunt: deterministic RED repro → fix → verify).
4. Step 0/1 → **Step 2.5** (pick exact-message vs pinned-id + confirm the teeth are preserved; wait for my header).
5. GREEN → Step 9 commit-message-first → report the hash. Flag the optional §44-addendum.
