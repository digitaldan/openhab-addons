# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **openHAB Add-ons** repository (~500 extensions) for the openHAB smart-home platform. It depends on [openhab-core](https://github.com/openhab/openhab-core) for base APIs. Version: 5.2.0-SNAPSHOT, Java 21, Maven build, OSGi runtime (Karaf/Equinox), EPL-2.0 license.

## Build Commands

```bash
# Build a specific binding (most common during development)
mvn clean install -pl :org.openhab.binding.<name>

# Build with integration test dependency resolution
mvn clean install -DwithResolver -pl :org.openhab.binding.<name>

# Full repository build (rarely needed, very slow)
mvn clean install

# Code formatting (run in the specific binding directory, not root)
mvn spotless:apply

# Check formatting without fixing
mvn spotless:check

# Generate i18n properties files
mvn i18n:generate-default-translations

# Create KAR file for deployment
mvn clean install karaf:kar -pl :org.openhab.binding.<name>
```

**Useful build flags:**
- `-DskipChecks` — skip static analysis (Checkstyle, SpotBugs)
- `-DskipTests` — skip test execution
- `-Dspotless.check.skip=true` — skip Spotless formatting check
- `-Dfeatures.verify.skip=true` — skip Karaf feature verification
- `-T 1C` — parallel build (1 thread per core)
- `-o` — offline mode

## Running Tests

Unit tests are in `src/test/java/` within each bundle. Integration tests are in `itests/`.

```bash
# Run tests for a specific binding
mvn clean install -pl :org.openhab.binding.<name>

# Run with resolver for integration tests
mvn clean install -DwithResolver -DskipChecks
```

Test reports after build:
- `target/code-analysis/report.html` — static analysis results (per-binding)
- `target/site/jacoco/index.html` — code coverage
- Root `target/summary_report.html` — consolidated summary of all static analysis warnings (Checkstyle, PMD, SpotBugs)

**Important**: After building a binding, always review `target/summary_report.html` to check for any code quality warnings. Fix any issues before committing. Common warnings include:
- Non-static loggers (use `private final Logger logger = ...` instead of `static`)
- Missing `@NonNullByDefault` annotations on classes
- `RelianceOnDefaultCharset` (use `StandardCharsets.UTF_8` explicitly)
- `ImplicitDefaultLocale` (use `Locale.ROOT` with `toLowerCase()`/`toUpperCase()`)
- Forbidden package usage (e.g., use `org.openhab.core.library.unit.Units` instead of `tech.units.indriya.unit.Units`)

## Running a Demo openHAB Instance

To manually test a binding in a live openHAB runtime, use the `openhab-distro` demo app. Requires Java 21 and Maven 3.8.6+.

### One-Time Setup

1. Clone the distro repo (sibling to this repo):
   ```bash
   git clone https://github.com/openhab/openhab-distro.git
   ```

2. Build your binding first so it's in your local Maven repo:
   ```bash
   mvn clean install -pl :org.openhab.binding.<name> -DskipChecks
   ```

3. In `openhab-distro/launch/app/pom.xml`, add your binding as a dependency (version must match 5.2.0-SNAPSHOT):
   ```xml
   <dependency>
     <groupId>org.openhab.addons.bundles</groupId>
     <artifactId>org.openhab.binding.<name></artifactId>
     <version>${project.version}</version>
     <scope>runtime</scope>
   </dependency>
   ```

4. In `openhab-distro/launch/app/app.bndrun`, add your binding to the `runrequires` list:
   ```
   bnd.identity;id='org.openhab.binding.<name>',\
   ```
   (append with a backslash on the preceding line)

5. Resolve dependencies from `openhab-distro/launch/app/`:
   ```bash
   mvn bnd-resolver:resolve
   ```

### Launch

From `openhab-distro/launch/app/`:
```bash
# Standard launch
mvn bnd-run:run

# Debug mode (attach debugger to port 10001)
mvn -D-runjdb=10001 package bnd-run:run
```

- **UI**: http://localhost:8080/
- **Felix Webconsole**: http://localhost:8080/system/console/ (admin:admin) — inspect bundles, services, components

### Development Cycle

After modifying binding code:
1. Rebuild: `mvn clean install -pl :org.openhab.binding.<name> -DskipChecks`
2. Restart the demo: re-run `mvn bnd-run:run` from `openhab-distro/launch/app/`

## Architecture

### Bundle Structure

Each addon in `bundles/` follows this layout:

```
org.openhab.binding.<name>/
├── pom.xml                          # Minimal, inherits from parent
├── src/main/java/.../internal/      # All implementation code (not exported via OSGi)
│   ├── handler/                     # ThingHandler implementations (core logic)
│   ├── config/                      # Configuration POJOs
│   ├── discovery/                   # Discovery services
│   └── ...
├── src/main/resources/
│   ├── OH-INF/
│   │   ├── addon/addon.xml          # Binding metadata
│   │   ├── thing/*.xml              # Thing type definitions (channels, properties)
│   │   ├── config/*.xml             # Configuration descriptions
│   │   └── update/instructions.xml  # Update instructions for thing type changes
│   └── i18n/                        # Translations (managed via Crowdin, don't edit non-English)
├── src/test/java/                   # Unit tests (JUnit 5)
└── README.md                        # User-facing binding documentation
```

### Key Concepts

- **Thing**: Represents a physical device or service. Defined in `OH-INF/thing/*.xml`. Managed by a `ThingHandler`.
- **Channel**: A specific function of a Thing (e.g., temperature reading, switch state). Linked to Items.
- **Item**: Virtual representation in openHAB that users interact with. Bindings update Items via Channels.
- **ThingHandler**: Central class that manages communication with a device. Extends `BaseThingHandler` or `BaseBridgeHandler`.
- **Discovery**: Optional service to auto-discover devices on the network.

### Addon Categories

- `org.openhab.binding.*` — Device/service integrations (~400+)
- `org.openhab.automation.*` — Scripting engines (Groovy, JS, Ruby, Python, etc.)
- `org.openhab.io.*` — Integration interfaces (HomeKit, Hue Emulation, Cloud)
- `org.openhab.persistence.*` — Data storage backends (InfluxDB, JDBC, RRD4J, etc.)
- `org.openhab.transform.*` — Data transformations (JSONPath, Regex, Map, Jinja, etc.)

## Code Style

- **Formatter**: Run `mvn spotless:apply` before committing. Uses Eclipse formatter with openHAB-specific config.
- **Imports**: Alphabetically sorted, logically grouped. Unsorted imports fail CI.
- **Static analysis**: Checkstyle, SpotBugs, PMD run during `verify` phase.
- **License header**: EPL-2.0 header required on all Java files (enforced by CI).
- **Forbidden packages**: `com.google.common`, `org.joda.time`, `org.junit.Assert` — use alternatives.
- **POM sections**: Must be sorted (enforced by Spotless).
- **Logging levels**: openHAB bindings only use `trace` and `debug`. Do not use `warn`, `error`, or `info` in binding code — these levels are reserved for core framework use. Use `debug` for diagnostic messages and `trace` for verbose/high-frequency output.
- **Passwords as String**: Storing passwords as `String` fields (not `char[]`) is standard practice in openHAB bindings. Do not flag this as a security issue in code reviews.

## Creating New Bindings

Never create binding folders manually. Use the skeleton script from the `bundles/` directory:

```bash
cd bundles
sh create_openhab_binding_skeleton.sh <BindingIdInCamelCase> "<Author Name>" <githubusername>
```

Then run `mvn spotless:apply` inside the new binding directory (not the repo root).

The script updates CODEOWNERS automatically. Binding name must be CamelCase matching `[A-Z][A-Za-z]*`.

## Git Remotes and Push Policy

This repo has multiple remotes:

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` (fetch) | `https://github.com/openhab/openhab-addons.git` | Upstream openHAB repo (read-only) |
| `dd` | `https://github.com/digitaldan/openhab-addons.git` | Personal fork (read-write) |

**CRITICAL: NEVER push to or create PRs against the `openhab` (origin) remote directly.** All commits and pushes MUST go to the `dd` (digitaldan) fork. PRs to the upstream openHAB repo are created from the fork via GitHub.

- Push feature branches: `git push dd <branch-name>`
- Push with tracking: `git push -u dd <branch-name>`
- Create PRs: `gh pr create --repo openhab/openhab-addons`
- **NEVER run**: `git push origin ...` — origin's push URL is set to `dd` as a safety net, but always use `dd` explicitly.

## Contribution Requirements

- Commits require `Signed-off-by` line (DCO): use `git commit -s`
- Commit messages: imperative, capitalized, max 50 char summary
- PRs should reference issues with `Fixes #XXX`
- CI runs smart builds — only changed addons are built on PRs
- Translations are managed via Crowdin; only edit English i18n files in this repo

## Per-Binding Agent Notes

Some bindings have their own `AGENTS.md` at `bundles/org.openhab.*/AGENTS.md` — check for these when working on a specific binding.

## Claude Code Configuration Management

This repo uses Claude Code configuration files (`CLAUDE.md`, `.claude/`) that are **not committed to upstream**. They live locally as untracked files (hidden via `.git/info/exclude`) and are committed to a dedicated `main-working` branch on the personal fork for use with claude.ai/code.

### How It Works

- **Local CLI (`claude`)**: Files exist as untracked files on `main`. `.git/info/exclude` hides them from `git status`.
- **claude.ai/code**: Sessions use the `main-working` branch on `digitaldan/openhab-addons`, which has these files committed.
- **GitHub Actions**: Two workflows keep things in sync:
  1. **Nightly upstream sync** — fetches `openhab/openhab-addons` main and fast-forwards the fork's `main` to match.
  2. **main → main-working merge** — triggered by pushes to `main`, auto-merges into `main-working` to keep it current.

### Files on the `main-working` Branch

| File | Purpose |
|------|---------|
| `CLAUDE.md` | Project instructions |
| `.claude/settings.json` | Shared permission rules |
| `.claude/agents/*.md` | Custom agent definitions |
| `.github/workflows/sync-main-working.yml` | Auto-merge main → main-working |
| `.github/workflows/sync-main-from-upstream.yml` | Nightly upstream → fork main sync |

**Not committed** (local-only): `.claude/settings.local.json`, `.claude/agent-memory/`

### Updating Claude Code Configuration

When you modify `CLAUDE.md`, `.claude/settings.json`, or `.claude/agents/*.md` locally, push the changes to the `main-working` branch so claude.ai/code sessions pick them up:

**Recommended workflow** — use a temporary worktree to avoid switching branches (which deletes untracked files):

```bash
# The simplest approach: use a temporary worktree
git worktree add /tmp/main-working main-working
cp CLAUDE.md /tmp/main-working/
cp .claude/settings.json /tmp/main-working/.claude/
cp -r .claude/agents/ /tmp/main-working/.claude/agents/
cd /tmp/main-working
git add -A && git commit -s -m "Update Claude Code configuration"
git push dd main-working
cd -
git worktree remove /tmp/main-working
```

Or let Claude Code do it — just say: **"push my claude config changes to the fork"** and it will handle the worktree workflow above.

### Manual Sync (if GitHub Action isn't running)

```bash
git fetch origin
git checkout main-working
git merge origin/main --no-edit
git push dd main-working
git checkout main
# Restore local files (branch switch deletes them):
git show dd/main-working:CLAUDE.md > CLAUDE.md
git show dd/main-working:.claude/settings.json > .claude/settings.json
# etc. for any other files
```

## Reference

- Developer docs: https://www.openhab.org/docs/developer/
- Addon development guide: https://www.openhab.org/docs/developer/addons/
- Code guidelines: https://www.openhab.org/docs/developer/guidelines.html
- Core concepts: https://www.openhab.org/docs/concepts/
