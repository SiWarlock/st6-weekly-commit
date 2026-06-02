---
description: Full preflight gate — sync deps, lint, format-check, type-check, test.
allowed-tools: Bash, Read
argument-hint: ""
---

Run the full quality gate for the current code area. **cwd-aware** — runs the right toolchain for whichever code area you're in.

Stops on first failure. Reports per-step pass/fail with the first ~20 lines of error output. Does NOT auto-fix on failure.

## Step 0 — Detect mode

```bash
case "$(pwd)" in
  */wc-web|*/wc-web/*) MODE=frontend ;;
  */infra|*/infra/*)   MODE=infrastructure ;;
  *)                   MODE=backend ;;
esac
```

Announce the detected mode to the user before running steps. If the mode looks wrong for the user's intent, surface the cwd and ask before proceeding.

---

## backend mode (cwd is `apps/wc-api/` or repo root)

### Step 1 — Sync dependencies
```bash
./gradlew build -x test
```

### Step 2 — Lint
```bash
./gradlew spotbugsMain
```

### Step 3 — Format check
```bash
./gradlew spotlessCheck
```

### Step 4 — Type check
```bash
./gradlew compileJava
```

### Step 5 — Test
```bash
./gradlew test
```

---

## frontend mode (cwd is `apps/wc-web/` or below)

### Step 1 — Sync dependencies
```bash
yarn install
```

### Step 2 — Lint
```bash
yarn nx lint wc-web
```

### Step 3 — Format check
```bash
yarn prettier --check .
```

### Step 4 — Type check
```bash
yarn nx typecheck wc-web
```

### Step 5 — Test
```bash
yarn nx test wc-web
```

### Step 6 — Build
```bash
yarn nx build wc-web
```

<!-- Keep a build step only if the area's build catches a class of errors the
     type-checker alone doesn't (e.g. a frontend production build). -->

---

## infrastructure mode (cwd is `infra/` or below)

> **No red-green test step for IaC.** Infrastructure (Terraform/k8s) is not red-green TDD — `terraform validate` + `terraform plan` + policy/lint and the deployed smoke suite stand in for the test step.

### Step 1 — Sync dependencies
```bash
terraform -chdir=infra/terraform init
```

### Step 2 — Lint
```bash
terraform fmt -check -recursive && tflint
```

### Step 3 — Format check
```bash
terraform fmt -check -recursive
```

### Step 4 — Type check
```bash
terraform validate
```

### Step 5 — Test
```bash
terraform -chdir=infra/terraform validate
```

---

## Output

**Success:**
> "Preflight clean (<mode>): lint ✓ + format ✓ + types ✓ + N tests pass"

**Failure (either mode):**
> "Preflight failed at Step N: <step name>"
> <first ~20 lines of error output>

## Forbidden in this command

- **Auto-fixing on failure.** The gate exists to catch problems; fixing them silently defeats the purpose.
- **Modifying baseline / ignore files to suppress failures.** Fix the underlying error.
- **Skipping steps.** Run in order; stop on first failure.
- **Cross-mode contamination.** Don't run one area's toolchain from another area's cwd. If cwd is wrong, fail loud with a clear message.
