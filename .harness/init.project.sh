# init.project.sh — project-specific gate checks.
# Sourced by init.sh from the PROJECT ROOT (not .harness/), so paths and commands
# like `npm test` / `pytest` resolve against the repo. Seeded once; NEVER clobbered
# on upgrade — put your real checks here instead of editing init.sh. Inherits the
# `fail "msg"` (abort the gate) and `ok "msg"` helpers from init.sh.
#
# Examples:
#   command -v node >/dev/null 2>&1 || fail "node not installed"
#   npm test --silent             || fail "tests are failing — do not start work"

# --- Project prerequisites (E00-F01 R9) --------------------------------------
# Fast presence checks ONLY: init.sh sources this file before every
# orchestrator step — no Gradle, no network, no file writes (plan note 4).
#
# JDK reading (deliberate, per .harness/progress/2026-11-12-E00-F01 rulings):
# the repo pins Java 17 source/target (app/build.gradle) and AGP requires
# 17+, so the checked dependency is "major >= 17"; the host JDK may be
# newer, and the messages state what is actually found — never a claim
# that JDK 17 itself is installed.

if [ -f ./gradlew ] && [ -x ./gradlew ]; then
  ok "Gradle wrapper present and executable (./gradlew)"
else
  fail "Gradle wrapper prerequisite missing: ./gradlew absent or not executable"
fi

# java -version prints to stderr; take the quoted version token.
if v=$(java -version 2>&1 | sed -n 's/[^"]*"\([^"]*\)".*/\1/p' | head -n 1) && [ -n "$v" ]; then
  # Legacy two-part versions print "1.8.0" — there the major is the SECOND
  # dotted token, so JDK 8 cannot parse as major 1 and false-pass.
  major=$(printf '%s\n' "$v" | awk -F. '{ if ($1 == "1" && NF > 1) print $2; else print $1 }')
  case "$major" in
    ''|*[!0-9]*) fail "JDK 17 or newer required on PATH — cannot read a numeric major from java version '$v'" ;;
  esac
  if [ "$major" -ge 17 ]; then
    ok "JDK 17+ on PATH satisfied (found java $v)"
  else
    fail "JDK 17 or newer required on PATH — found java $v (expected major >= 17)"
  fi
else
  fail "JDK 17 or newer required on PATH — 'java -version' produced no version token (is a JDK installed?)"
fi
