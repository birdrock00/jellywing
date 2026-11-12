# Bootstrap: adapt the harness to this project — Technical Plan

> Translates the `.spec.md` intent into design. Every table row cites the `R-id`(s) it serves
> or the `ADR-NNNN` that defers it (ADR-0009). This feature writes **configuration and docs**,
> not product code; the only executable it creates is the bootstrap's own verification script.

## Stack & dependencies

- Language/framework: the existing Android Gradle build — `./gradlew` (Gradle 9.5.1,
  Android Gradle Plugin 9.3.0-alpha10, JDK 17, `app` module, Java 17 source/target, three
  `.kt` sources compiled by AGP's built-in Kotlin support). Plus POSIX `sh`, `git`, and
  `python3` (already a hard harness prerequisite for the local TaskStore).
- Configuration surface: `harness.config.yaml` under `.harness/` (project-owned, hand-edit
  safe — the installer rewrites single scalars only).
- New dependencies: **none**. No Gradle plugin, no CI change, no new tool on PATH.

## Data model  (serves: R1, R2, R3, R4, R5, R10, R11)

No runtime data model — this feature changes no product state. The state it changes is the
harness configuration and the board file, i.e. the fields below:

| Entity | Field | Type | Notes | R-id |
|---|---|---|---|---|
| `harness.config.yaml` | `verification.test_command` | string | was `""` → `./gradlew testDebugUnitTest` | R1 |
| `harness.config.yaml` | `verification.typecheck_command` | string | was `""` → `./gradlew compileDebugKotlin compileDebugJavaWithJavac` | R2 |
| `harness.config.yaml` | `verification.lint_command` | string | was `""` → `./gradlew lintDebug` | R3 |
| `harness.config.yaml` | `verification.init_script` | string | was `./init.sh` (no such file at the repo root) → `./.harness/init.sh` | R4 |
| `harness.config.yaml` | `change_size.test_paths` | list of ERE | was `[]` → one entry: `(^|/)src/androidTest/` (appended to the built-in `TEST_RE`, never replacing it) | R10 |
| `state/tasks.json` | `project` | string | was `"TODO-rename-me"` → `"jellywing"`; no other key in this file is touched (status/spec_path are the Orchestrator's) | R5 |
| `specs/product.md` | body sections + frontmatter `status` | markdown | three `TODO` sections replaced; `status: draft` → `ready`; `stage: production` unchanged | R6, R7 |
| `specs/product.md` | frontmatter `stage` | enum | **unchanged** at `production` — the stage line the stage-aware NFR rule reads | R6 |
| `.gitignore` | one ignore pattern | pattern | `jellywing-delete.md`, added under a comment naming why (local device/host identifiers) | R11 |

`verification.integration_command` is deliberately **not** set: it stays `""` because this is a
single-repo project with no umbrella manifest, so there is no cross-repo integration gate to
run. No `R-id` asks for it.

## API / interface  (serves: R1, R2, R3, R4)

No HTTP API. The interface this feature defines is the one the Reviewer consumes: the three
`verification.*` commands plus the init gate, each run from the repository root.

| Command | Config key | What it proves | Measured on 2026-11-12 | R-id |
|---|---|---|---|---|
| `./gradlew testDebugUnitTest` | `verification.test_command` | the app's JVM unit tests (`app/src/test/**`, plain JUnit 4) compile and pass | exit 0, ~1.5 s warm; minutes cold | R1 |
| `./gradlew compileDebugKotlin compileDebugJavaWithJavac` | `verification.typecheck_command` | every Kotlin and Java source in `app/src/main` compiles | exit 0, ~9 s warm | R2 |
| `./gradlew lintDebug` | `verification.lint_command` | Android lint analysis for the debug variant completes | exit 0, ~34 s warm | R3 |
| `./.harness/init.sh` | `verification.init_script` | the harness structure, TaskStore schema, plan-citation and readiness sweeps pass | exit 0, ~1 s, two warn-only notices | R4 |

`./gradlew lintDebug` exits 0 whatever it finds — `app/build.gradle` sets
`lint { abortOnError false }`. It writes four reports into `app/build/reports/` —
`lint-results-debug.txt`, `.html`, `.xml`, `.sarif` (all four verified present on 2026-11-12).
R3 therefore certifies the command is wired and completes; findings are read from
`app/build/reports/lint-results-debug.txt`, the same file the `.tests.md` r3 case keys its
freshness assertion to. See the `.spec.md` Open questions.

## Files to change  (serves: R1 … R11)

| File | Change | R-id |
|---|---|---|
| `.harness/harness.config.yaml` | modify — set `verification.test_command` to `./gradlew testDebugUnitTest`; keep every neighbouring comment intact | R1 |
| `.harness/harness.config.yaml` | modify — set `verification.typecheck_command` to `./gradlew compileDebugKotlin compileDebugJavaWithJavac` | R2 |
| `.harness/harness.config.yaml` | modify — set `verification.lint_command` to `./gradlew lintDebug` | R3 |
| `.harness/harness.config.yaml` | modify — set `verification.init_script` to `./.harness/init.sh` | R4 |
| `.harness/harness.config.yaml` | modify — under `change_size:`, replace the empty `test_paths:` list with the single entry `(^|/)src/androidTest/` | R10 |
| `.harness/state/tasks.json` | modify — `project` becomes `jellywing`; rewrite through `python3 .harness/tools/tasks-lock.py` if a write path is needed, otherwise a single-token edit; nothing else in the file changes | R5 |
| `.harness/specs/product.md` | modify — replace the three `TODO` sections with the content drafted under *Product constitution content*, and set frontmatter `status: ready` (keep `stage: production`) | R6 |
| `.harness/specs/product.md` | modify — the principles section must contain the server read-only constraint sentence drafted under *Product constitution content* (item P1) | R7 |
| `.harness/specs/glossary.md` | modify — append the domain terms drafted under *Glossary terms*; keep every existing entry verbatim | R8 |
| `.harness/init.project.sh` | modify — after the seeded comments, add the fast presence checks: the Gradle wrapper file exists and is executable, and `java -version` reports major 17; each on an `ok` line, each on a `fail` line when absent | R9 |
| `.gitignore` | modify — add `jellywing-delete.md` under a comment saying it is local scratch holding device/host identifiers | R11 |
| `tools/test_bootstrap.sh` | create — the verification suite: one `sh` function per `R-id`, named exactly as the `.tests.md` table, exiting non-zero on the first failed assertion set | R1 … R11 |

## Deferred  (ADR-0009 — bolt-ons the stage excludes)

Deferred: none — stage is production (read from `.harness/specs/product.md` frontmatter:
`stage: production`; the rewrite keeps that line). No bolt-on NFR is being left out on stage
grounds, and no plan row above adds a bolt-on without an `R-id`.

## DO NOT TOUCH

- `app/**` — the product. The bootstrap adapts the harness; an app change here would put
  unreviewed behavior in a feature whose acceptance criteria cannot test it.
- `build.gradle`, `app/build.gradle`, `gradle/**`, `gradle.properties`, `gradlew` — the gate
  consumes the build as it exists; changing it is a product decision (see Open questions).
- `.github/workflows/**` — CI already runs `./gradlew clean assembleDebug testDebugUnitTest`.
- `.harness/init.sh`, `.harness/tools/**`, `.harness/agents/**`, `.harness/docs/**`,
  `.harness/specs/_templates/**` — installer-owned harness body; edits are silently
  overwritten on the next upgrade. This is why the verification script is created under
  `tools/` at the repository root rather than under `.harness/`: a new file inside `.harness/`
  is *not* in the excluded project-owned set that `.harness/tools/harness-owned-paths.sh`
  emits, so once `.harness/` is committed it would read as un-landed harness drift and fail the
  `init.sh` gate forever.
- `.harness/state/tasks.json` beyond the `project` scalar — status transitions belong to the
  Orchestrator (`set-status`), and `spec_path` values are the board's routing state.
- `.harness/specs/epics/E01-library-management/**` and
  `.harness/progress/inbox/E01-F01.md` — another feature's artifacts.
- `jellywing-delete.md` — ignored, never deleted, moved, edited, staged or committed; it
  holds device identifiers, hostnames, a username and infrastructure names, and the committed
  `AGENTS.md` rule forbids putting PII or credentials on GitHub.
- `.harness/store/tasks.schema.json` — the schema the board is validated against.

## Approach notes

1. **Order: gate first, docs second, hygiene last.** R1–R4 unblock every downstream Reviewer
   and are the reason this feature exists; R5–R9 make the harness speak the project's
   language; R10–R11 are correctness/hygiene on top. R1 is the walking skeleton: it is the
   thinnest end-to-end observable behavior of the bootstrap (a configured command string that
   a clean-context Reviewer can actually run and get exit 0 from).
2. **RED is real for R1–R4.** With `test_command: ""`, the R1 assertion in
   `tools/test_bootstrap.sh` has nothing to run and fails; the config edit turns it GREEN. Do
   not "help" the RED by stubbing a command into the test — the test reads the config value,
   exactly as the Reviewer will.
3. **Run the gradle commands without `--offline`.** Verified on 2026-11-12: `./gradlew
   testDebugUnitTest` resolves from the cache and exits 0 with and without `--offline`, so the
   plain form (matching CI) is specified; pinning `--offline` would break a fresh clone.
4. **Keep `init.project.sh` fast and side-effect free.** It is sourced by `init.sh` before
   *every* orchestrator step. Presence and `java -version` only — no Gradle, no network, no
   build directory reads. `init.sh` runs from the project root, so reference `./gradlew` and
   use the inherited `ok`/`fail` helpers; write no files.
5. **`java -version` parsing:** it prints to stderr; take the quoted version token and accept
   either `"17` or `17.` (JDK 17 prints `"17.0.x"`). Fail the gate with a message naming the
   expected major version, not a bare exit.
6. **`change_size.test_paths` is additive by design.** `change-size.sh` appends each
   configured entry to its built-in `TEST_RE`, which on 2026-11-12 reads
   `(^|/)tests?/|(^|/)__tests__/|[._-](test|spec)\.[a-z]+$|_test\.(go|py|rb)$|^test_[^/]*\.(py|sh)$|/test_[^/]*\.(py|sh)$|Test[s]?\.(java|kt|cs)$`
   — read it back with `sh .harness/tools/change-size.sh --print-test-re --repo .`; that query
   mode, not eyeballing the YAML, is how R10 is verified. The built-ins already cover
   `app/src/test/**` and any `*Test.java`; what they miss is `app/src/androidTest/**` whose
   filename does not end in `Test.java`. Verified on 2026-11-12 against the effective pattern:
   `app/src/androidTest/java/club/thatpetbff/gramophone/ui/AppSessionGuard.java` classifies as
   **production** today (R10's premise), while `tools/test_bootstrap.sh` already classifies as
   **test** through the built-in `/test_[^/]*\.(py|sh)$` alternative — which is why the new
   script needs no `test_paths` entry of its own.
7. **`validate-board.py` after the board edit.** `.harness/init.sh` validates
   `state/tasks.json` against `store/tasks.schema.json` on every run, so R5's GREEN check is
   simply "init.sh still exits 0"; run `python3 .harness/tools/validate-board.py` directly for
   the sharper error message if it does not.
8. **The verification script is the only executable created.** Keep it POSIX `sh`
   (`#!/bin/sh`, `set -eu` is fine, no bashisms), one function per `R-id`, and print one line
   per case so a failure names the `R-id`. Make the three gradle-backed cases (R1, R2, R3)
   run the *configured* command, not a hard-coded copy, so a later edit to the config that
   breaks the gate is caught by the same script. Keep R11 read-only: assert
   `git check-ignore -q` and the absence of the path from `git status --porcelain`, and assert
   the file still exists — never delete, `chmod` or rewrite it.
9. **Nothing here is wired into `init.sh` or CI** (see Out of scope): `tools/test_bootstrap.sh`
   is run by the Builder (per `.tasks.md`) and by the Reviewer as this feature's contract.

## Product constitution content  (serves: R6, R7)

Facts taken from `README.md`, `app/build.gradle` and the repository (the Builder writes this
prose; keep it high-level — the constitution is Layer 0 and detail cascades errors):

- **What this product is** — *Jellywing* is a native Android music player for Jellyfin media
  servers (`applicationId club.thatpetbff.gramophone`, GPLv3), based on Gelli, itself based on
  Phonograph. Library navigation, artists/albums/songs/genres, search, favorites and
  playlists, playback history reporting, gapless playback through Media3 ExoPlayer, per-song
  and batch caching, filtering by library and audio language, and a media-session notification.
  Version 1.3.13 (`versionCode 26`), `minSdk 23`, `targetSdk 36`, distributed via F-Droid and
  GitHub Actions APK builds.
- **Who it is for** — a person who self-hosts (or is invited to) a Jellyfin server carrying
  their own music and wants a proper native Android player for it on a phone or tablet, without
  a subscription, without Google services, and with the freedom to read and change the source.
  Today that is largely the author; contributions are welcomed on issues first.
- **Principles & hard constraints** — write these as bullets. The set:
  - **P1 (R7) — the Jellyfin server's library is read-only.** jellywing never creates,
    updates, or deletes library content, images, or server-side playlist entities on someone
    else's server; every feature-visible mutation (favorites, playlists, queue, up-next,
    sorting, cache) lands in the app's own local store. Reading server data and reporting
    playback status/usage are not library mutations and remain allowed.
  - **GPLv3 stays.** The license stays GPLv3; no dependency or code import that is
    incompatible with it.
  - **Self-hosted and credential-local.** Server URLs, users and access tokens stay on the
    device; no telemetry, no accounts, no third-party server, no secrets in the repository.
  - **No PII or credentials in the repository** (the committed `AGENTS.md` rule): device
    identifiers, hostnames, usernames, cluster/context names and vault key names stay out of
    tracked files and out of commits.
  - **Don't break the fork's upgrade path.** This tree carries a renamed fork of Gelli;
    mass-reformatting or wholesale rewrites of inherited files raise the cost of taking
    upstream fixes.
  - **Known open defects, stated not fixed** (copied from `README.md` as of 2026-11-12 — this
    bullet is a snapshot, so date-stamp any edit to it): artist sorting is not available
    through the API, and playlists/favorites do not refresh automatically when changed elsewhere.

## Glossary terms  (serves: R8)

Append these to `.harness/specs/glossary.md` (definitions are the Builder's wording, the
terms and their boundaries are not negotiable — they are the vocabulary downstream specs use).
Keep the six existing entries verbatim; they are harness-level conversation terms and removing
them would break references in harness docs.

| Term | Boundary to convey |
|---|---|
| Jellyfin server | The remote media server jellywing connects to. Its library is read-only from the app's point of view (principle P1). |
| Server connection | The saved triple of base URL, server user and access token that identifies jellywing to one Jellyfin server. |
| Library | The media the app shows for the current server connection and the selected library/category filter. |
| Category | A server-side library filter choice (`model/Category.java`); "filter content by library" in the README. |
| Language | The audio-language filter (`model/Language.java`, surfaced as the `Language: ` genre prefix) used to scope albums/songs and queues. |
| Playlist | A client-local ordered list of songs held in the app's own database, not a server entity. |
| Favorite | A client-local star on a song, held in the app's own database; not written back to the server. |
| Cache | A local copy of a song the app downloaded for offline play (per-song or batch); not a server-side download job. |
| Up next | The app's own upcoming-playback queue, separate from the server's playback state. |
