#!/bin/sh
# tools/test_bootstrap.sh — E00-F01 bootstrap verification contract.
#
# Test code, not production: the effective change-size classifier pattern
# (`sh .harness/tools/change-size.sh --print-test-re --repo .`) already matches
# this path through its built-in `/test_[^/]*\.(py|sh)$` alternative.
#
# One function per R-id, named exactly as the table in
# .harness/specs/epics/E00-bootstrap/F01-adapt/F01-adapt.tests.md.
#   sh tools/test_bootstrap.sh <case-name>   run one case
#   sh tools/test_bootstrap.sh               run all cases (T13 sweep)
# A failure prints one line naming the R-id and exits non-zero.
# Lives at tools/ (not .harness/): only harness-owned-paths.sh exclusions are
# project-owned; a new file inside .harness/ would read as un-landed drift.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
CONFIG=".harness/harness.config.yaml"

# Case list, appended as pairs land; consumed by the all-cases sweep.
CASES="r1_test_command_runs_unit_tests r2_typecheck_command_compiles_sources r3_lint_command_completes r4_init_script_resolves_and_passes r5_board_project_name_is_jellywing r6_product_constitution_has_no_todo r7_server_read_only_principle_stated r8_glossary_defines_domain_terms r9_project_gate_reports_prerequisites r10_android_test_paths_classified_as_test"

fail() {
    printf 'FAIL %s\n' "$1" >&2
    exit 1
}

r1_test_command_runs_unit_tests() {
    # R1 — verification.test_command, read from the config, must be non-empty,
    # run from the repository root, and exit 0.
    # The parse is deliberately local; T12 (R1/R2/R3) factors the three
    # gradle-backed cases onto one shared reader.
    cmd=$(sed -n 's/^[[:space:]]*test_command:[[:space:]]*//p' "$CONFIG" | head -n 1)
    cmd=${cmd%%#*}                                   # trailing YAML comment
    cmd=$(printf '%s' "$cmd" | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/')
    [ -n "$cmd" ] || fail "R1 r1_test_command_runs_unit_tests: verification.test_command is empty in $CONFIG — nothing to run"
    sh -c "$cmd" || fail "R1 r1_test_command_runs_unit_tests: configured command '$cmd' exited non-zero"
    echo "PASS R1 r1_test_command_runs_unit_tests"
}

r2_typecheck_command_compiles_sources() {
    # R2 — verification.typecheck_command, read from the config, must be
    # non-empty, run from the repository root, and exit 0.
    # Parse duplicated from r1 by design; T12 (R1/R2/R3) unifies it.
    cmd=$(sed -n 's/^[[:space:]]*typecheck_command:[[:space:]]*//p' "$CONFIG" | head -n 1)
    cmd=${cmd%%#*}                                   # trailing YAML comment
    cmd=$(printf '%s' "$cmd" | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/')
    [ -n "$cmd" ] || fail "R2 r2_typecheck_command_compiles_sources: verification.typecheck_command is empty in $CONFIG — nothing to run"
    sh -c "$cmd" || fail "R2 r2_typecheck_command_compiles_sources: configured command '$cmd' exited non-zero"
    echo "PASS R2 r2_typecheck_command_compiles_sources"
}

r3_lint_command_completes() {
    # R3 — verification.lint_command, read from the config, must be non-empty,
    # run from the repository root, and exit 0; the text lint report must be
    # refreshed by THIS run (.tests.md freshness clause).
    #
    # Sharp edge (doc-critic re-audit): with unchanged inputs Gradle reports
    # lintDebug UP-TO-DATE and does NOT rewrite the report, so a bare mtime
    # comparison would go RED on a legitimate re-run. Resolution chosen here:
    # remove the report (a Gradle output, inside gitignored app/build/) right
    # before invoking — deleting an output forces the task to re-execute —
    # then assert existence + mtime >= case start. The freshness assertion is
    # kept verbatim, not loosened to existence-only. Parse duplicated from r1
    # by design; T12 (R1/R2/R3) unifies it.
    report="app/build/reports/lint-results-debug.txt"
    cmd=$(sed -n 's/^[[:space:]]*lint_command:[[:space:]]*//p' "$CONFIG" | head -n 1)
    cmd=${cmd%%#*}                                   # trailing YAML comment
    cmd=$(printf '%s' "$cmd" | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/')
    [ -n "$cmd" ] || fail "R3 r3_lint_command_completes: verification.lint_command is empty in $CONFIG — nothing to run"
    start=$(date +%s)
    rm -f "$report"
    sh -c "$cmd" || fail "R3 r3_lint_command_completes: configured command '$cmd' exited non-zero"
    [ -f "$report" ] || fail "R3 r3_lint_command_completes: '$cmd' exited 0 but produced no $report — the lint report was not written"
    mt=$(stat -c %Y "$report")
    [ "$mt" -ge "$start" ] || fail "R3 r3_lint_command_completes: $report mtime ($mt) predates case start ($start) — lint did not actually re-run"
    echo "PASS R3 r3_lint_command_completes"
}

r4_init_script_resolves_and_passes() {
    # R4 — verification.init_script names a script that exists relative to the
    # repository root, is executable, and exits 0 when run from that root.
    script=$(sed -n 's/^[[:space:]]*init_script:[[:space:]]*//p' "$CONFIG" | head -n 1)
    script=${script%%#*}                                 # trailing YAML comment
    script=$(printf '%s' "$script" | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/')
    [ -n "$script" ] || fail "R4 r4_init_script_resolves_and_passes: verification.init_script is empty in $CONFIG"
    [ -f "$script" ] || fail "R4 r4_init_script_resolves_and_passes: init_script '$script' does not exist relative to repository root $ROOT"
    [ -x "$script" ] || fail "R4 r4_init_script_resolves_and_passes: init_script '$script' exists but is not executable"
    "$script" || fail "R4 r4_init_script_resolves_and_passes: init_script '$script' exited non-zero from the repository root"
    echo "PASS R4 r4_init_script_resolves_and_passes"
}

r5_board_project_name_is_jellywing() {
    # R5 — the TaskStore parses, its project field reads exactly 'jellywing',
    # and validate-board.py still accepts it (same invocation init.sh uses).
    board="state/tasks.json"
    proj=$(cd .harness && python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["project"])' "$board" 2>/dev/null) \
        || fail "R5 r5_board_project_name_is_jellywing: .harness/$board does not parse as JSON"
    [ "$proj" = "jellywing" ] || fail "R5 r5_board_project_name_is_jellywing: project reads '$proj', expected 'jellywing'"
    ( cd .harness && python3 tools/validate-board.py state/tasks.json store/tasks.schema.json --spec-root . ) >/dev/null \
        || fail "R5 r5_board_project_name_is_jellywing: validate-board.py rejected .harness/$board"
    echo "PASS R5 r5_board_project_name_is_jellywing"
}

r6_product_constitution_has_no_todo() {
    # R6 — no whole-word TODO (case-insensitive) anywhere; each of the three
    # sections holds at least one real prose line; the frontmatter block that
    # starts on line 1 carries status: ready and stage: production.
    f=".harness/specs/product.md"
    if grep -Eiq '(^|[^[:alnum:]_])TODO([^[:alnum:]_]|$)' "$f"; then
        fail "R6 r6_product_constitution_has_no_todo: $f still contains a whole-word TODO occurrence"
    fi
    for h in "What this product is" "Who it is for" "Principles & hard constraints"; do
        # Non-empty, non-comment (no blank / no '>' blockquote / no '#') prose
        # line inside the extracted section — not a whole-file grep, so a
        # section body cannot be satisfied by text living elsewhere.
        if ! awk -v hdr="## $h" 'index($0, hdr) == 1 {k = 1; next} k && /^#/ {k = 0} k' "$f" \
            | grep -Ev '^[[:space:]]*$|^[[:space:]]*>|^[[:space:]]*#' \
            | grep -q '[[:alnum:]]'; then
            fail "R6 r6_product_constitution_has_no_todo: section '## $h' has no non-empty non-comment prose line"
        fi
    done
    head -n 1 "$f" | grep -qx -- '---' \
        || fail "R6 r6_product_constitution_has_no_todo: frontmatter block does not start on line 1"
    fm=$(awk 'NR == 1 && $0 == "---" {inb = 1; next} inb && $0 == "---" {exit} inb' "$f")
    printf '%s\n' "$fm" | grep -qx 'status: ready' \
        || fail "R6 r6_product_constitution_has_no_todo: frontmatter does not carry status: ready"
    printf '%s\n' "$fm" | grep -qx 'stage: production' \
        || fail "R6 r6_product_constitution_has_no_todo: frontmatter no longer carries stage: production"
    echo "PASS R6 r6_product_constitution_has_no_todo"
}

r7_server_read_only_principle_stated() {
    # R7 — inside the extracted '## Principles & hard constraints' section
    # (not a whole-file grep), some line states both Jellyfin and the server
    # library being read-only, and that line or its immediate neighbour
    # states the client-local store. A bullet that only says "be careful
    # with the server" does not pass.
    f=".harness/specs/product.md"
    awk '
        index($0, "## Principles & hard constraints") == 1 { k = 1; next }
        k && /^#/ { k = 0 }
        k { n = NR; lines[n] = $0 }
        k {
            low = tolower($0)
            if (low ~ /jellyfin/ && low ~ /read-only/) { hit = NR }
        }
        END {
            if (!hit) exit 1
            ctx = tolower(lines[hit] " " (hit < n ? lines[hit + 1] : ""))
            if (ctx !~ /local store/) exit 1
        }
    ' "$f" \
        || fail "R7 r7_server_read_only_principle_stated: '## Principles & hard constraints' has no Jellyfin read-only line whose line or neighbour states the client-local store"
    echo "PASS R7 r7_server_read_only_principle_stated"
}

r8_glossary_defines_domain_terms() {
    # R8 — the glossary defines the nine domain terms this product's specs
    # use, and keeps every entry the seeded file already carried. Both lists
    # are embedded literally here (not read from the plan) so the case fails
    # loudly, not vacuously, if the plan is ever reworded. Each term must
    # appear as a bolded list entry: `- **Term**`.
    f=".harness/specs/glossary.md"
    # Heredoc, one term per line: a pipe would run the loop body in a
    # subshell, where fail()'s exit would not end the run.
    while IFS= read -r t; do
        if ! grep -qF -- "- **$t**" "$f"; then
            fail "R8 r8_glossary_defines_domain_terms: glossary has no bolded list entry for '$t'"
        fi
    done <<'TERMS'
Jellyfin server
Server connection
Library
Category
Language
Playlist
Favorite
Cache
Up next
Handoff
Take over
Bot-handled
Human-handled
Operator inbox
Run
TERMS
    echo "PASS R8 r8_glossary_defines_domain_terms"
}

r9_project_gate_reports_prerequisites() {
    # R9 — ./.harness/init.sh exits 0 AND its stdout names both project
    # prerequisites the gate now checks, so a silently missing check cannot
    # pass. The two warn-only notices (un-versioned harness body, escalation
    # disarmed) are expected and not failures.
    out=$(./.harness/init.sh 2>&1) \
        || fail "R9 r9_project_gate_reports_prerequisites: ./.harness/init.sh exited non-zero"
    printf '%s\n' "$out" | grep -qi 'gradle wrapper' \
        || fail "R9 r9_project_gate_reports_prerequisites: gate output does not name the Gradle wrapper check"
    printf '%s\n' "$out" | grep -qi 'jdk 17' \
        || fail "R9 r9_project_gate_reports_prerequisites: gate output does not name the JDK 17 check"
    echo "PASS R9 r9_project_gate_reports_prerequisites"
}

r10_android_test_paths_classified_as_test() {
    # R10 — the EFFECTIVE pattern change-size.sh prints (built-ins plus the
    # configured test_paths; queried, not eyeballed from the YAML) must
    # classify a representative app/src/androidTest source as test, and must
    # still classify an app/src/test *Test.java as test (no built-in
    # regression).
    re=$(sh .harness/tools/change-size.sh --print-test-re --repo .) \
        || fail "R10 r10_android_test_paths_classified_as_test: change-size.sh --print-test-re exited non-zero"
    [ -n "$re" ] || fail "R10 r10_android_test_paths_classified_as_test: --print-test-re printed nothing"
    if ! printf '%s\n' app/src/androidTest/java/club/thatpetbff/gramophone/ui/AppSessionGuard.java | grep -Eq "$re"; then
        fail "R10 r10_android_test_paths_classified_as_test: effective pattern does not match app/src/androidTest/.../AppSessionGuard.java — instrumentation books as production"
    fi
    if ! printf '%s\n' app/src/test/java/club/thatpetbff/gramophone/util/ServerUrlUtilTest.java | grep -Eq "$re"; then
        fail "R10 r10_android_test_paths_classified_as_test: effective pattern no longer matches app/src/test/.../ServerUrlUtilTest.java — built-in regression"
    fi
    echo "PASS R10 r10_android_test_paths_classified_as_test"
}

main() {
    name="${1:-all}"
    if [ "$name" = "all" ]; then
        for c in $CASES; do
            "$c"
        done
        return 0
    fi
    case " $CASES " in
        *" $name "*) "$name" ;;
        *) printf 'unknown case: %s (known: %s)\n' "$name" "$CASES" >&2; exit 2 ;;
    esac
}

main "$@"
