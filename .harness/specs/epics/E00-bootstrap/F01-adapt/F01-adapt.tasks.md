# Bootstrap: adapt the harness to this project — Tasks

> Per-requirement red/green pairs (ADR-0008). For each R-id in the `.spec.md`: `T<n>a` writes
> the failing case named in `.tests.md` and observes RED; `T<n>b` writes the minimal change
> and observes GREEN; the Builder commits after each GREEN. R1 is the walking skeleton: the
> thinnest end-to-end observable behavior of the bootstrap is a configured verification
> command that a clean-context Reviewer can run and get exit 0 from.
> `python3 "$HARNESS_DIR/tools/tasks-pair-check.py" F01-adapt.tasks.md --spec F01-adapt.spec.md`
> must exit 0.

- [x] **T1a** (R1) — create `tools/test_bootstrap.sh` (`#!/bin/sh`, one function per R-id, a
      dispatcher that runs one named case or all of them, one output line naming the R-id on
      failure); add the r1 case so it reads `verification.test_command` out of
      `.harness/harness.config.yaml` and runs that value from the repository root; run
      `sh tools/test_bootstrap.sh r1_test_command_runs_unit_tests`; observe RED (the seeded
      value is empty, so there is nothing to run)
- [x] **T1b** (R1) — set `verification.test_command: ./gradlew testDebugUnitTest` in
      `.harness/harness.config.yaml`, leaving every neighbouring comment untouched; re-run the
      r1 case; observe GREEN; commit
- [x] **T2a** (R2) — add the r2 case to `tools/test_bootstrap.sh`: read
      `verification.typecheck_command` and run its value; observe RED with
      `sh tools/test_bootstrap.sh r2_typecheck_command_compiles_sources`
- [x] **T2b** (R2) — set `verification.typecheck_command: ./gradlew compileDebugKotlin
      compileDebugJavaWithJavac`; re-run; observe GREEN; commit
- [x] **T3a** (R3) — add the r3 case: read `verification.lint_command` and run its value;
      observe RED with `sh tools/test_bootstrap.sh r3_lint_command_completes`
- [x] **T3b** (R3) — set `verification.lint_command: ./gradlew lintDebug`; re-run; observe
      GREEN (this run takes ~34 s warm); commit
- [x] **T4a** (R4) — add the r4 case: the `verification.init_script` value names a file that
      exists relative to the repository root, is executable, and exits 0 when run from there;
      observe RED against the seeded `./init.sh`
- [x] **T4b** (R4) — set `verification.init_script: ./.harness/init.sh`; re-run; observe GREEN
      (the gate itself exits 0 today, with two warn-only notices); commit
- [x] **T5a** (R5) — add the r5 case: `.harness/state/tasks.json` parses, its `project` field
      reads exactly `jellywing`, and `python3 .harness/tools/validate-board.py` exits 0;
      observe RED against `TODO-rename-me`
- [x] **T5b** (R5) — change the single `project` value in `.harness/state/tasks.json` to
      `jellywing`; touch no other key; re-run; observe GREEN; commit
- [x] **T6a** (R6) — add the r6 case: `.harness/specs/product.md` contains no `TODO`, its
      three sections (`## What this product is`, `## Who it is for`,
      `## Principles & hard constraints`) each hold non-empty real prose, frontmatter `status`
      reads `ready` and frontmatter `stage` still reads `production`; observe RED against the
      seeded template
- [x] **T6b** (R6) — write the three sections from the plan's *Product constitution content*
      and set `status: ready` in the frontmatter block that starts on line 1; re-run; observe
      GREEN; commit
- [x] **T7a** (R7) — add the r7 case: under `## Principles & hard constraints` there is a
      bullet that states the Jellyfin server library is read-only and that feature-visible
      mutations land in the client's own local store; observe RED
- [x] **T7b** (R7) — add principle P1 (the plan's wording) to that section, keeping reading
      server data and reporting playback status/usage explicitly allowed; re-run; observe
      GREEN; commit
- [x] **T8a** (R8) — add the r8 case: every term listed in the plan's *Glossary terms* table
      is defined in `.harness/specs/glossary.md`, and all six entries the file already carries
      are still present; observe RED
- [x] **T8b** (R8) — append the domain terms to `.harness/specs/glossary.md` (append; do not
      rewrite the existing entries); re-run; observe GREEN; commit
- [x] **T9a** (R9) — add the r9 case: running `./.harness/init.sh` exits 0 and its output
      reports both project prerequisites (Gradle wrapper present and executable, JDK 17 on
      `PATH`) by name; observe RED against the comment-only `.harness/init.project.sh`
- [x] **T9b** (R9) — add the two fast presence checks to `.harness/init.project.sh` using the
      inherited `ok`/`fail` helpers — no Gradle, no network, no file writes (this file is
      sourced before every orchestrator step); re-run; observe GREEN; commit
- [x] **T10a** (R10) — add the r10 case: `sh .harness/tools/change-size.sh --print-test-re
      --repo .` prints a pattern that matches
      `app/src/androidTest/java/club/thatpetbff/gramophone/ui/AppSessionGuard.java`; observe
      RED against the empty `change_size.test_paths` list
- [x] **T10b** (R10) — replace `test_paths: []` under `change_size:` in
      `.harness/harness.config.yaml` with the single entry `(^|/)src/androidTest/`; re-run;
      observe GREEN; commit
- [x] **T11a** (R11) — add the r11 case: `git check-ignore -q jellywing-delete.md` exits 0,
      `git status --porcelain` does not list that path, and the file still exists unchanged;
      observe RED (it is untracked-but-not-ignored today)
- [x] **T11b** (R11) — add `jellywing-delete.md` to `.gitignore` under a comment naming the
      reason (local scratch holding device/host identifiers). Do not delete, move, edit or
      stage the file; re-run; observe GREEN; commit
- [x] **T12** (R1, R2, R3) — refactor `tools/test_bootstrap.sh` so the three gradle-backed
      cases read their command from `.harness/harness.config.yaml` through one shared reader
      instead of duplicating the parse; the r10 case is deliberately excluded — its subject is
      the *effective* pattern `change-size.sh` prints, not a YAML value; R1/R2/R3 cases stay
      GREEN; commit
- [x] **T13** — hand-off sweep (no R-id of its own; it re-runs what T1–T11 made green): run
      `sh tools/test_bootstrap.sh` (all eleven cases), `./.harness/init.sh`, and the three
      configured `verification.*` commands from the repository root; every one exits 0
