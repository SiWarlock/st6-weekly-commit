# Runbook — Local JDK 21 toolchain setup (backend build prerequisite)

> **Purpose.** The `apps/wc-api` Gradle/Java backend requires **JDK 21** (locked decision: Spring Boot 3.3 / `JavaLanguageVersion.of(21)`). This runbook records how the toolchain was provisioned on the build machine and the one non-obvious caveat that affects Gradle toolchain resolution. Origin: task 0.2 pre-flight blocker (2026-06-02) — the machine had **no JVM at all**.

## Status

- **Resolved:** 2026-06-02 (user-directed environment fix).
- **Mechanism chosen:** Homebrew `openjdk@21` (escalation option 2). _(Options 1/3 — portable Temurin tarball / sdkman — were the alternatives; not used.)_

## What was installed

- **OpenJDK 21.0.11** via Homebrew at `/opt/homebrew/opt/openjdk@21` (Apple Silicon / arm64).
- `JAVA_HOME` + PATH exported in **`~/.zshenv`**:
  ```sh
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

## Verification (all pass as of 2026-06-02)

```sh
java -version     # → openjdk version "21.0.11"
javac -version    # → javac 21.0.11
echo "$JAVA_HOME"  # → /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Confirmed resolvable in the default Bash-tool shell (which is **zsh**, so it sources `~/.zshenv`), and via `zsh -c` / `zsh -ic`. Each Bash invocation sources the profile fresh — no per-command `JAVA_HOME` export needed.

## ⚠ Caveat — Homebrew openjdk@21 is keg-only

The Homebrew JDK is **keg-only**: it is **not** symlinked into `/Library/Java/JavaVirtualMachines/`, so the macOS canonical locator does NOT see it:

```sh
/usr/libexec/java_home -V   # → "Unable to locate a Java Runtime"  (EXPECTED — not a problem)
```

Gradle still works because it auto-detects the **running JVM + `JAVA_HOME`** (both JDK 21 here).

**Toolchain resolution decision (task 0.2, supersedes an earlier draft).** Do **NOT** commit `org.gradle.java.installations.paths=/opt/homebrew/...` into the tracked `gradle.properties` — that path is machine-specific and would break CI and other developers. Instead, for portability:

- The build applies the **`org.gradle.toolchains.foojay-resolver-convention`** settings plugin, which auto-provisions a JDK 21 on any machine when one isn't already discoverable. Combined with `JAVA_HOME` pointing at JDK 21 (as here), `JavaLanguageVersion.of(21)` resolves cleanly with no committed pin.
- If a developer's `JAVA_HOME` is not 21 and they want an explicit local pin, it belongs in their **uncommitted user** `~/.gradle/gradle.properties` (`org.gradle.java.installations.paths=...`), never the repo file.

If `brew` ever upgrades the minor, `/opt/homebrew/opt/openjdk@21` (the versioned symlink) stays valid; only the resolved Cellar path under it changes.

## CI implication (task 0.8)

CI must provision JDK 21 explicitly — do **not** rely on this local install. Use `actions/setup-java` with `distribution: temurin`, `java-version: 21` (which also sets `JAVA_HOME` for the runner). Tracked as an origin note on task 0.8 in `MVP_TASKS.md`.

## To reverse / re-provision

- Reverse: `brew uninstall openjdk@21` and remove the two `~/.zshenv` exports.
- Alternative if a no-system-mutation setup is ever needed (e.g. a locked-down CI runner): a portable Temurin 21 aarch64 tarball extracted to `~/.local/jdks/temurin-21` + `JAVA_HOME` pointed there (escalation option 1).
