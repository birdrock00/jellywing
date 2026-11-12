---
id: E00-F01
title: "Bootstrap: adapt the harness to this project"
epic: E00-bootstrap
status: in-review      # pending → spec-ready → in-progress → in-review → done
sdd: true
autonomous: false        # the human spec gate applies
complexity: standard     # basis stated in Context (E17-F03)
depends_on: []
owner: birdrock00
---

# Bootstrap: adapt the harness to this project — Functional Spec

## Context

The harness is installed at `.harness/` but has never been pointed at *this* project. It
still carries the values it shipped with: `state/tasks.json` names the board
`TODO-rename-me`; `verification.test_command`, `lint_command` and `typecheck_command` are
empty strings ("set during bootstrap"), so the Reviewer on every downstream feature has no
command to run and no way to fail a build; `.harness/specs/product.md` is the untouched
seeded template with three literal `TODO` sections; `.harness/init.project.sh` holds only
comments; `change_size.test_paths` is empty, so the change-size classifier reads this repo's
Android instrumentation tree (`app/src/androidTest/`) as production code; and an untracked
scratch note at the repository root (`jellywing-delete.md`) holds device identifiers,
hostnames and infrastructure names that the committed `AGENTS.md` rule forbids putting on
GitHub. This feature turns those seeded placeholders into this project's real settings. It
changes no app behavior — the "user" of every requirement here is a downstream harness role
(Reviewer, Builder, Orchestrator) or the repository itself.

**Intent source.** No inbox brief exists for this feature (`.harness/progress/inbox/` holds
only `E01-F01.md`, a different epic, which was not used). Per `agents/architect.md`, the
one-line TaskStore intent is the legitimate fallback; the factual context (repository state,
the operator's restated constraints, the boundaries) came from the Orchestrator's task file
`progress/20261112-E00-F01/architect-task.md`. There was no Inception Q&A to record.

**Complexity basis (E17-F03).** `standard`. Every requirement here is a value written into
five project-owned harness files, into `.gitignore` at the repository root, and into one new
verification script; none of the four stated
signals applies — the decomposition landed at 11 of the 12 allowed `R-id`s without splitting,
there is no ADR whose constraints reach across files (see *Architecture alignment*), the plan
names no known defect class, and speccing it required no reconciliation between two existing
components (the one stale value, `verification.init_script`, is corrected in place). If the
split seam named under *Out of scope* is ever pulled forward into this feature, re-read this
line.

## Business rules

- The harness body (`.harness/init.sh`, `.harness/tools/`, `.harness/agents/`,
  `.harness/docs/`, `.harness/specs/_templates/`) is installer-owned and is overwritten on
  upgrade. Project truth lives only in the project-owned set that
  `.harness/tools/harness-owned-paths.sh` excludes: `harness.config.yaml`,
  `init.project.sh`, `specs/product.md`, `specs/glossary.md`, `specs/epics/`, `state/`,
  `progress/`. This feature writes nothing else inside `.harness/`.
- `harness.config.yaml` is safe to hand-edit: the installer rewrites single scalars only, so
  these settings survive an upgrade.
- The heavyweight build belongs to `verification.test_command` (run by the Reviewer once per
  role phase). `.harness/init.sh` runs before *every* orchestrator step, so anything added to
  the project gate must be a fast presence check, never a Gradle invocation.
- The verification commands are the ones this repository already runs in CI
  (`.github/workflows/main.yml` runs `./gradlew clean assembleDebug testDebugUnitTest`), so
  the gate and CI agree rather than inventing a third build shape.
- No file under `app/`, no Gradle build file, and no CI workflow is touched: this feature
  adapts the harness, not the product.
- The untracked scratch note is *ignored*, never deleted or edited — it is the operator's
  working note, and its contents are not this feature's to destroy or to commit.

## Architecture alignment

> **Not required on this spec** — graceful degradation (`agents/architect.md`), recorded so
> the omission is deliberate rather than accidental.
>
> Verified absent on 2026-11-12: `.harness/specs/architecture.md` does not exist, and no
> `adr/` directory exists anywhere under `.harness/specs/` (`specs/epics/` holds only
> `E01-library-management/epic.md`). This repository has never run `/sdd-plan`, so there is
> no recorded decision to honor and **no citation is fabricated**.
>
> Two artifacts this bootstrap *would* have consumed if they existed: the ADR set (nothing to
> align to) and the inbox brief's touched-ADR seed (no brief — see Context). If `/sdd-plan`
> runs later, specs written after it carry the section; **this spec is not retro-fitted**.

## Acceptance criteria (EARS)

> Each is one testable behavior with a stable id. Every check below is asserted by
> `tools/test_bootstrap.sh` (see `.tests.md` for the exact case name).

- **R1** — When `verification.test_command` is read from `.harness/harness.config.yaml` and
  run at the repository root, the system shall run the app's JVM unit tests as
  `./gradlew testDebugUnitTest` and exit 0.
- **R2** — When `verification.typecheck_command` is read from `.harness/harness.config.yaml`
  and run at the repository root, the system shall compile the app's Kotlin and Java sources
  as `./gradlew compileDebugKotlin compileDebugJavaWithJavac` and exit 0.
- **R3** — When `verification.lint_command` is read from `.harness/harness.config.yaml` and
  run at the repository root, the system shall run the Android lint analysis for the debug
  variant as `./gradlew lintDebug` and exit 0.
- **R4** — The `verification.init_script` value shall name a script that exists relative to
  the repository root, is executable, and exits 0 when run from that root (the seeded
  `./init.sh` names a file that does not exist there; the installed gate is
  `./.harness/init.sh`).
- **R5** — When any role reads the TaskStore at `.harness/state/tasks.json`, the system shall
  report the project name `jellywing` in place of the seeded `TODO-rename-me`, and that file
  shall still validate against `store/tasks.schema.json`.
- **R6** — The product constitution `.harness/specs/product.md` shall describe this product
  in its three sections — what it is, who it is for, its principles and hard constraints —
  with no occurrence of the word `TODO` anywhere in the file (case-insensitive, whole word, so
  a rewording to `Todo` cannot slip past), with frontmatter `status: ready`, and with
  frontmatter `stage: production` preserved unchanged.
- **R7** — The `## Principles & hard constraints` section of
  `.harness/specs/product.md` shall state the server read-only constraint in terms a
  downstream feature can honor: jellywing treats the Jellyfin server's library as read-only
  and applies every feature-visible mutation in the client's own local store.
- **R8** — `.harness/specs/glossary.md` shall define the domain terms this product's specs
  use (see the plan's *Glossary terms* list) while keeping every entry the file already
  contains.
- **R9** — While `.harness/init.sh` sources the project gate `.harness/init.project.sh`, the
  gate shall report the two prerequisites every agent depends on — the Gradle wrapper is
  present and executable, and a JDK 17 is on `PATH` — and `./.harness/init.sh` shall still
  exit 0 on this repository.
- **R10** — When `sh .harness/tools/change-size.sh --print-test-re --repo .` reports the
  effective test-path pattern, that pattern shall match
  `app/src/androidTest/java/club/thatpetbff/gramophone/ui/AppSessionGuard.java`, so
  instrumentation code never books against the production line budget.
- **R11** — If an untracked scratch file at the repository root holds local device, host or
  infrastructure identifiers (today: `jellywing-delete.md`), then the system shall ignore it
  through `.gitignore` — `git check-ignore -q jellywing-delete.md` exits 0 and
  `git status --porcelain` does not list it — and shall neither delete the file nor change its
  contents.

## Out of scope

- **Any change to the product**: `app/**`, `build.gradle`, `app/build.gradle`,
  `gradle/**`, `.github/workflows/**`. The gate uses the Gradle tasks that already exist.
- **Arming Builder escalation.** `init.sh` warns that `builder-heavy` is configured but
  DISARMED. Arming it needs a real heavier model pinned in `models:` plus a re-run of the
  installer, which is not present in this installed target, and the model choice is the
  operator's. Left as the warn-only notice it is today. (Open question below.)
- **Creating `specs/epics/E00-bootstrap/epic.md`.** The epic brief and feature index are the
  Driller's/Orchestrator's artifact; the Architect writes feature-scoped files only. `E00`
  has no `epic.md` today and this feature does not change that.
- **Committing `.harness/` to git.** Today the whole harness body is untracked, which is why
  the `init.sh` drift guard is inert. Whether the specs/board are versioned is a repository
  policy call for the human, not a bootstrap setting. (Open question below.)
- **Running instrumented (Espresso) tests.** `app/src/androidTest/**` needs a device or
  emulator; this feature only teaches the classifier that those paths are tests.
- **Wiring `tools/test_bootstrap.sh` into CI or into `init.sh`.** It is the bootstrap's own
  verification contract, run by the Builder and the Reviewer for this feature; making it a
  permanent repository gate is a separate decision.
- **Any work on `E01-F01`** or its inbox brief.

## Open questions

- Should `app/build.gradle`'s `lint { abortOnError false }` be changed (or given a text
  report) so a lint finding fails the gate or is readable from stdout? As shipped,
  `./gradlew lintDebug` exits 0 whatever it finds, so R3 proves the command *runs*; the
  Reviewer must read `app/build/reports/lint-results-debug.html` for findings. That edit is
  product build config, so it is not specified here. The report files `lintDebug` writes are
  `app/build/reports/lint-results-debug.txt` (plus `.html`, `.xml` and `.sarif`), all four
  verified present on 2026-11-12 after a run; this feature's contract reads the `.txt`.
- Should `.harness/` (specs, board, progress) be committed? Untracked specs are durable docs
  with no history and no review.
- Should `models.builder-heavy` be pinned and the installer re-run to arm escalation, or stay
  deliberately disarmed?
- Should `tools/test_bootstrap.sh` run in CI, so a later upgrade that clobbers a bootstrap
  value is caught? Left out of scope above.
