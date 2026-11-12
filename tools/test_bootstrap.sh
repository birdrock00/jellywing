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
CASES="r1_test_command_runs_unit_tests r2_typecheck_command_compiles_sources"

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
