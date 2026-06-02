---
description: Run tests by class. cwd-aware. Usage: /run-tests [unit|integration|e2e|validate|plan|all]
allowed-tools: Bash
argument-hint: "[unit|integration|e2e|validate|plan|all]"
---

Run tests by class. **cwd-aware** — runs the right test runner for whichever code area you're in.

Argument: `$ARGUMENTS` — see the mapping table(s) below. Default: `unit`.

## Step 0 — Detect mode

```bash
case "$(pwd)" in
  */wc-web|*/wc-web/*) MODE=frontend ;;
  */infra|*/infra/*)   MODE=infrastructure ;;
  *)                   MODE=backend ;;
esac
```

Announce the detected mode before running.

---

## backend mode mapping

| Argument | Command |
|---|---|
| (empty / `unit`) | `./gradlew test` |
| `integration` | `./gradlew integrationTest` |
| `all` | `./gradlew check` |

## frontend mode mapping

| Argument | Command |
|---|---|
| (empty / `unit`) | `yarn nx test wc-web` |
| `e2e` | `yarn nx e2e wc-e2e` |
| `all` | `yarn nx run-many -t test` |

## infrastructure mode mapping

| Argument | Command |
|---|---|
| (empty / `validate`) | `terraform -chdir=infra/terraform validate` |
| `plan` | `terraform -chdir=infra/terraform plan` |
| `all` | `terraform -chdir=infra/terraform validate && terraform -chdir=infra/terraform plan` |

If an argument names a class that belongs to a *different* mode, **ERROR** with a clear message naming the expected cwd.

---

<!-- ▼ EXAMPLE BLOCK [id=test-class-discipline-notes]: test-class discipline notes — OPTIONAL. Some test classes
     need preconditions (a live external dependency, an env var, a slow browser).
     The source project documented things like: "the live-attack class needs a
     reachable target + a bearer env var, else it skips with a clear message;"
     "the visual-smoke class is slow — run per-PR, not per-commit." Add the
     project's own per-class discipline notes here, or delete this block. ▼ -->
- **backend `integration`** needs Docker (Testcontainers PostgreSQL) — fails clearly if Docker is unavailable; no H2 fallback.
- **frontend `e2e`** (Cypress/Cucumber) needs the local full stack via `docker-compose` (the in-process async profile stands in for SNS/SQS) — slower; run per-PR, not per-commit.
- **infra `plan`** needs AWS credentials + initialized Terraform; in CI without creds it is skipped with a clear message (`validate` still runs).
<!-- ▲ END EXAMPLE BLOCK [id=test-class-discipline-notes] ▲ -->

## Output

Report:
- Mode (which code area)
- Test count + class
- Pass / fail counts
- First ~20 lines of any failure
- Total duration
