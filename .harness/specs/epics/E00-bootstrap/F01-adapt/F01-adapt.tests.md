# Bootstrap: adapt the harness to this project — Test Contract

> The traceability matrix: every R-id in the `.spec.md` maps to a concrete, executable case.
> All eleven cases live in one new file, `tools/test_bootstrap.sh` — POSIX `sh`, one function
> per R-id, named exactly as the table's second column. Run one case with
> `sh tools/test_bootstrap.sh <case-name>`, all of them with `sh tools/test_bootstrap.sh`.
> The Reviewer fails the feature if any R-id lacks a passing case.
>
> Why the script lives at `tools/` and not under `.harness/`: only the paths that
> `.harness/tools/harness-owned-paths.sh` excludes are project-owned, so a new file inside
> `.harness/` would read as un-landed harness drift to the `init.sh` guard once the harness
> body is committed.
>
> It counts as test code, not production: the built-in classifier pattern reported by
> `sh .harness/tools/change-size.sh --print-test-re --repo .` includes the alternative
> `/test_[^/]*\.(py|sh)$`, which matches the path `tools/test_bootstrap.sh` (verified against
> the effective pattern on 2026-11-12, as recorded in the plan's approach note 6). It needs no
> `change_size.test_paths` entry of its own, and it is not part of the app build.

| R-id | Behavior | Test (file::name) | Type | Status |
|---|---|---|---|---|
| R1 | When `verification.test_command` is read and run at the root, it runs the JVM unit tests and exits 0 | `tools/test_bootstrap.sh::r1_test_command_runs_unit_tests` | unit (config + build) | ⬜ |
| R2 | When `verification.typecheck_command` is read and run, it compiles all Kotlin/Java sources and exits 0 | `tools/test_bootstrap.sh::r2_typecheck_command_compiles_sources` | unit (config + build) | ⬜ |
| R3 | When `verification.lint_command` is read and run, it completes the debug lint analysis and exits 0 | `tools/test_bootstrap.sh::r3_lint_command_completes` | unit (config + build) | ⬜ |
| R4 | `verification.init_script` names a script that exists at the root, is executable, and exits 0 | `tools/test_bootstrap.sh::r4_init_script_resolves_and_passes` | unit (config) | ⬜ |
| R5 | The TaskStore reports `project: jellywing` and still validates against the schema | `tools/test_bootstrap.sh::r5_board_project_name_is_jellywing` | unit (data) | ⬜ |
| R6 | `product.md` describes the product in all three sections, carries no `TODO`, `status: ready`, `stage: production` | `tools/test_bootstrap.sh::r6_product_constitution_has_no_todo` | unit (doc) | ⬜ |
| R7 | The principles section states the Jellyfin server library is read-only, mutations are client-local | `tools/test_bootstrap.sh::r7_server_read_only_principle_stated` | unit (doc) | ⬜ |
| R8 | The glossary defines every listed domain term and keeps every pre-existing entry | `tools/test_bootstrap.sh::r8_glossary_defines_domain_terms` | unit (doc) | ⬜ |
| R9 | The project gate reports both prerequisites and `./.harness/init.sh` still exits 0 | `tools/test_bootstrap.sh::r9_project_gate_reports_prerequisites` | unit (gate) | ⬜ |
| R10 | The effective test-path pattern matches a representative `app/src/androidTest/` source file | `tools/test_bootstrap.sh::r10_android_test_paths_classified_as_test` | unit (classifier) | ⬜ |
| R11 | The PII scratch path is git-ignored, absent from `git status --porcelain`, and still present on disk | `tools/test_bootstrap.sh::r11_pii_scratch_is_ignored` | unit (repo hygiene) | ⬜ |

## Case-by-case contract

Each case asserts exactly what its R-id claims, read from the repository as it stands — no
fixture, no network, no writes outside the Gradle build directory.

- `r1` / `r2` / `r3` — read the value of `verification.test_command`,
  `verification.typecheck_command`, `verification.lint_command` from
  `.harness/harness.config.yaml`; fail if empty; run the value with `sh -c` from the repository
  root; fail on non-zero exit. R3 additionally checks that `app/build/reports/lint-results-debug.txt`
  — the text report the plan's API section records `lintDebug` as writing (alongside `.html`,
  `.xml` and `.sarif`) — was refreshed by this run, i.e. its mtime is not older than the case
  start, so a command that silently no-ops cannot pass.
- `r4` — the `verification.init_script` value resolves to an existing executable file relative
  to the repository root, and running it from that root exits 0.
- `r5` — `.harness/state/tasks.json` parses as JSON; `project` equals `jellywing`; the file is
  still one that `python3 .harness/tools/validate-board.py` accepts (exit 0).
- `r6` — `.harness/specs/product.md` contains no whole-word `TODO` on any line, matched
  case-insensitively (`grep -Eiq '(^|[^[:alnum:]_])TODO([^[:alnum:]_]|$)'`), so a reworded
  `Todo` cannot pass; each of its three `##` sections
  has at least one non-empty, non-comment prose line after its heading; the frontmatter block
  that starts on line 1 carries `status: ready` and `stage: production`.
- `r7` — under `## Principles & hard constraints` there is a line matching both a server
  read-only phrase (`read-only`, case-insensitive) and `Jellyfin`, and that line or its
  neighbour states the client-local store; a bullet that only says "be careful with the server"
  does not pass.
- `r8` — the case embeds both lists literally, so it fails loudly rather than vacuously if the
  plan is ever reworded. All nine new terms must appear in `.harness/specs/glossary.md` as a
  bolded list entry: **Jellyfin server**, **Server connection**, **Library**, **Category**,
  **Language**, **Playlist**, **Favorite**, **Cache**, **Up next**. All six pre-existing entries
  must still be present: **Handoff**, **Take over**, **Bot-handled**, **Human-handled**,
  **Operator inbox**, **Run**.
- `r9` — `./.harness/init.sh` exits 0 and its stdout names both prerequisites it now checks
  (the Gradle wrapper and JDK 17), so a silently missing check cannot pass; the two warn-only
  notices (un-versioned harness body, escalation disarmed) are expected and not failures.
- `r10` — capture `sh .harness/tools/change-size.sh --print-test-re --repo .` and match the
  path `app/src/androidTest/java/club/thatpetbff/gramophone/ui/AppSessionGuard.java` against
  the printed pattern with `grep -Eq`; also assert the pattern still matches
  `app/src/test/java/club/thatpetbff/gramophone/util/ServerUrlUtilTest.java` (no built-in
  regression).
- `r11` — `git check-ignore -q jellywing-delete.md` exits 0; `git status --porcelain` has no
  line for that path; `jellywing-delete.md` still exists. The case never writes, deletes, or
  `chmod`s the file.

## Behavioral / end-to-end checks

There is no UI behavior in this feature; the "end-to-end" path is a downstream role running the
gate. The Reviewer performs it by hand, in a clean shell at the repository root:

1. `./.harness/init.sh` → exit 0, structure + TaskStore schema + sweeps green, two warn-only
   notices.
2. `sh "$(config: verification.test_command)"` — i.e. run what the config now names, exactly as
   `agents/reviewer.md` check 1 does, and confirm exit 0. This is the downstream Reviewer's
   experience: before this feature the field was empty and check 1 could not run at all.
3. Run the other two configured commands the same way; both exit 0.
4. `git status --porcelain` → `jellywing-delete.md` is not listed; the file is still on disk;
   nothing under `app/` is modified by this feature's commits.
5. `git log -p` on this feature's commits → no device identifier, hostname, username, cluster
   or vault name appears in any tracked file.

## Non-functional checks

- Lint: `./gradlew lintDebug` clean-exits; findings (not exit code) are read from
  `app/build/reports/lint-results-debug.txt` — the build sets `lint { abortOnError false }`, so
  a finding here is reported to the human, not auto-rejected (see `.spec.md` Open questions).
- Types: `./gradlew compileDebugKotlin compileDebugJavaWithJavac` clean.
- Speed: `./.harness/init.sh` stays under a few seconds — the R9 additions are presence checks
  only; a Gradle call in the project gate is a defect (it runs before every orchestrator step).
- Budget: production lines stay far under `change_size.advise_lines` (1500). The only
  non-document file this feature creates is `tools/test_bootstrap.sh`, which the built-in
  classifier reads as a test path (see the header note); the config, board and doc edits are
  tens of lines.
