#!/usr/bin/env bash
# verify-feature.sh — the deterministic gate that stands in for a Reviewer round.
#
# Rationale (measured; see better-agentic-system.md): an independent Reviewer round cost ~1 h of
# model generation on this host, and both of the round's genuine defects — the scan-ordering false
# red and the truncated-membership false green — were statically decidable. This exists so that
# class of finding is caught in seconds by a machine rather than hourly by an agent.
#
# Output contract, borrowed from .harness/tools/run-tests.sh deliberately: a green run says ONE
# thing, so it prints ONE line. A red run prints the failing check's output IN FULL, because the
# diagnostic is the point and truncating it to save tokens would be a false economy.
#
# Exit: 0 passed | 2 a check failed | 3 the gate itself could not run
# Usage: sh tools/verify-feature.sh [--fast]     --fast skips the gradle tier (seconds)
#        STRICT=1 sh tools/verify-feature.sh     also block on commit-author PII (use before push)
#        DEVICE_TIER=1 sh tools/verify-feature.sh  also run the instrumented device tier (~2.5 min,
#                                                  boots the QA AVD; see tools/verify-instrumented.sh)

set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)" || exit 3

FAST=0
[[ ${1:-} == "--fast" ]] && FAST=1
DRIVER=tools/qa-delete-cycle.sh
ANDROID_TEST_DIR=app/src/androidTest/java
FAILURES=""
NOTES=""

fail() { FAILURES="${FAILURES}$1
"; }
note() { NOTES="${NOTES}$1
"; }

# --- 1. every shell tool parses ------------------------------------------------------------------
bad=""
for f in tools/*.sh .harness/tools/*.sh; do
  [[ -f $f ]] || continue
  bash -n "$f" 2>/tmp/vf-bash-err || bad="$bad$f: $(tr '\n' ' ' < /tmp/vf-bash-err)
"
done
[[ -z $bad ]] || { printf '%s' "$bad"; fail "shell syntax: a .sh file does not parse"; }

# --- 2. playlist membership reads are hardened (C-1 class) ---------------------------------------
# A read without &Limit= can come back page-truncated, after which "the id is not in this response"
# silently means "the playlist is clean" — a forgeable green. So no read may bypass the hardened
# helper. Comment lines are excluded: a doc comment describing the endpoint is not a read.
bare=$(grep -rn 'Playlists/[^"]*Items?UserId=' --include='*.sh' --include='*.java' . 2>/dev/null \
      | grep -v 'Limit=' \
      | grep -v 'verify-feature.sh' \
      | awk -F: '{ payload=$0; sub(/^[^:]*:[0-9]*:/, "", payload); gsub(/^[[:space:]]+/, "", payload);
                  if (payload !~ /^#/ && payload !~ /^\*/ && payload !~ /^\/\//) print $0 }' || true)
if [[ -n $bare ]]; then
  printf '%s\n' "$bare"
  fail "membership reads without &Limit= (a truncated page can forge a clean verdict, C-1)"
fi

# The helper must exist AND actually compare page size against TotalRecordCount. A helper that
# exists but never checks the total is decoration.
if [[ -f $DRIVER ]]; then
  if ! grep -q 'playlist_items_checked' "$DRIVER"; then
    fail "no playlist_items_checked helper in $DRIVER — membership reads are per-site again"
  elif ! grep -A60 'playlist_items_checked()' "$DRIVER" | grep -q 'TotalRecordCount'; then
    fail "playlist_items_checked never compares against TotalRecordCount (truncation still forgeable)"
  fi
fi

# --- 3. no newly-added assumeTrue in instrumented tests (R-EV) ------------------------------------
# An assumption failure prints as SUCCESS, so a skipped leg is indistinguishable from a pass.
# Scoped to files this feature touched: a repo-wide ban would have been red on day one against
# pre-existing live tests, and a permanently-red gate teaches people to ignore it.
allow=.harness/state/assume-allowlist
out=""
while IFS= read -r f; do
  [[ -f $f ]] || continue
  while IFS= read -r ln; do
    enc=$(awk -v L="$ln" 'NR<=L && /public void [A-Za-z0-9_]+\(/ { m=$0 }
          END { if (match(m, /public void [A-Za-z0-9_]+/)) print substr(m, RSTART+12, RLENGTH-12) }' "$f")
    grep -qxF "${enc:-}" "$allow" 2>/dev/null \
      || out="$out$f:$ln sits in method '${enc:-<unknown>} which is not listed in $allow
"
  done < <(grep -n 'assumeTrue(' "$f" 2>/dev/null | cut -d: -f1)
done < <({ git diff --name-only origin/master..HEAD -- "$ANDROID_TEST_DIR" 2>/dev/null
           git diff --name-only HEAD -- "$ANDROID_TEST_DIR" 2>/dev/null
           # Without an origin ref the diff-based scope is EMPTY, which would make this whole check
           # pass vacuously. Fall back to every tracked file in the tree so a missing remote narrows
           # nothing.
           if ! git rev-parse --verify --quiet origin/master >/dev/null; then git ls-files -- "$ANDROID_TEST_DIR"; fi
         } | sort -u)
if [[ -n $out ]]; then printf '%s' "$out"; fail "assumeTrue added outside an allowlisted method (skips report as green, R-EV)"; fi

# --- 4. library-scan ordering pin (F1 class) ------------------------------------------------------
# Narrow regression pin, not a general proof: the scan is POSTed from exactly one place — the phase
# that runs AFTER the blocking `am instrument -w` — and the delegated test path declares instead of
# polling. Only real invocations are counted, so prose about the endpoint cannot trip this. A
# backgrounded racing scanner would satisfy a naive version of this check and is exactly what turns
# a false red into a false green, hence pinning the count rather than the shape.
if [[ -f $DRIVER ]]; then
  n=$(grep -cE 'api[[:space:]]+POST[[:space:]]+"[^"]*Library/Refresh' "$DRIVER" || true)
  [[ ${n:-0} -le 1 ]] || { echo "$DRIVER POSTs /Library/Refresh $n times; exactly one (post-instrument) allowed"; fail "library-scan ordering pin (F1 class)"; }
  grep -rq 'PARTIAL (post-scan cleanliness delegated to driver' "$ANDROID_TEST_DIR" 2>/dev/null \
    || fail "delegated e2e path has no PARTIAL declaration, so the driver/test split is undocumented"
fi

# --- 5. secrets and local paths (blocking) --------------------------------------------------------
# Offending values are MASKED: a gate that prints the value it is objecting to creates the leak it
# exists to prevent.
hits=$(git grep -nIE '(api[_-]?key|passwd|password|secret|token)[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9+/_-]{20,}' -- ':!*.md' 2>/dev/null \
      | grep -viE 'bindToService|serviceToken|getToken|setToken|tokenRef|QA_TOKEN|X-Emby|MediaBrowser|Authorization' || true)
if [[ -n $hits ]]; then printf '%s\n' "$hits" | sed -E 's/[A-Za-z0-9+\/_-]{20,}/<REDACTED>/g'; fail "possible secret in tracked content"; fi

# The gate contains this path string in its own pattern, so it must not flag itself.
hits=$(git grep -ln '/home/ec2-user' -- ':!*.md' ':!*verify-feature.sh' 2>/dev/null || true)
if [[ -n $hits ]]; then printf '%s\n' "$hits"; fail "local filesystem path in tracked non-doc content"; fi

# --- 6. commit-author identity ---------------------------------------------------------------
# ADVISORY by default. This is a known standing debt with a tracked rewrite that only the operator
# can authorise, and it blocks a PUSH rather than development. Making it red on every settle would
# block on a decision that is not the agent's to make, which is how gates get ignored. STRICT=1
# promotes it to a failure — that is what to run immediately before pushing.
# Range base: with no origin/master, `origin/master..HEAD` is EMPTY and this check would pass
# vacuously — which is the precise failure mode this gate exists to catch elsewhere. Fall back to
# scanning all reachable history when the remote ref is absent.
if git rev-parse --verify --quiet origin/master >/dev/null; then
  pii_range=(origin/master..HEAD)
else
  echo "note: no origin/master ref; scanning ALL reachable history for identities" >&2
  pii_range=(--all)
fi
pii=$(git log "${pii_range[@]}" --format='%ae%n%ce' 2>/dev/null | grep -v 'noreply' | grep -v '^$' | sort -u || true)
if [[ -n $pii ]]; then
  masked=""
  while IFS= read -r e; do
    masked="$masked  ${e%%@*}@$(printf '%s' "${e#*@}" | sed -E 's/^(..).*/\1***/')"
  done <<< "$pii"
  if [[ ${STRICT:-0} == 1 ]]; then
    printf '%s\n' "$masked"; fail "non-noreply commit identity in the unpushed range"
  else
    note "ADVISORY: $(printf '%s\n' "$pii" | wc -l | tr -d ' ') non-noreply author identity/combo in the unpushed range — DO NOT PUSH until the tracked rewrite lands:"$masked
  fi
fi

# --- 7. harness preflight + task/spec pairing -----------------------------------------------------
# init.sh already runs validate-board and the readiness sweep, so those are not re-implemented here.
if [[ -f .harness/init.sh ]]; then
  if ! sh .harness/init.sh >/tmp/vf-init.log 2>&1; then
    echo "--- .harness/init.sh (tail) ---"; tail -25 /tmp/vf-init.log
    fail "harness init.sh exited non-zero"
  fi
fi
for spec in $(git diff --name-only origin/master..HEAD -- '.harness/specs' 2>/dev/null | grep '\.spec\.md$'); do
  tasks="${spec%.spec.md}.tasks.md"
  [[ -f $tasks ]] || continue
  if ! python3 .harness/tools/tasks-pair-check.py "$tasks" --spec "$spec" >/tmp/vf-pair.log 2>&1; then
    echo "--- tasks-pair-check $tasks (tail) ---"; tail -20 /tmp/vf-pair.log
    fail "tasks/spec pairing broken for $tasks"
  fi
done

# --- gradle tier ---------------------------------------------------------------------------------
if [[ $FAST == 0 ]]; then
  # Freshness is part of the result. Gradle reports "up-to-date" against results from a previous
  # tree, and a count taken from stale XML has been mistaken for a passing gate in this lane before.
  rm -rf app/build/test-results/testDebugUnitTest
  if ! timeout 1800 ./gradlew testDebugUnitTest >/tmp/vf-unit.log 2>&1; then
    echo "--- gradle testDebugUnitTest (tail) ---"; tail -40 /tmp/vf-unit.log
    fail "unit tests failed (full log: /tmp/vf-unit.log)"
  else
    python3 - <<'PY' || fail "unit test totals wrong or absent"
import glob, sys, xml.etree.ElementTree as ET
t = f = e = s = 0
files = glob.glob('app/build/test-results/testDebugUnitTest/*.xml')
if not files:
    print("no result XML written - the suite did not actually run"); sys.exit(1)
for p in files:
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0))
    e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print("unit: tests=%d failures=%d errors=%d skipped=%d" % (t, f, e, s))
sys.exit(0 if (t and not f and not e) else 1)
PY
  fi
  if ! timeout 900 ./gradlew compileDebugAndroidTestSources >/tmp/vf-at.log 2>&1; then
    echo "--- gradle compileDebugAndroidTestSources (tail) ---"; tail -30 /tmp/vf-at.log
    fail "androidTest sources do not compile (full log: /tmp/vf-at.log)"
  fi
fi

# --- optional device tier -------------------------------------------------------------------------
# Off by default: it boots an emulator and takes ~2.5 min, and a gate that is slow gets skipped.
# Ask for it with --device once the JVM tier is green.
if [[ ${DEVICE_TIER:-0} == 1 ]]; then
  if ! sh tools/verify-instrumented.sh > /tmp/vf-device.log 2>&1; then
    echo "--- verify-instrumented (tail) ---"; tail -40 /tmp/vf-device.log
    fail "instrumented device tier failed (full logs: /tmp/verify-instrumented/)"
  fi
fi

# --- verdict -------------------------------------------------------------------------------------
if [[ -n $FAILURES ]]; then
  echo "VERIFY GATE: FAIL"
  printf '%s' "$FAILURES" | sed '/^$/d' | sed 's/^/  - /'
  if [[ -n $NOTES ]]; then echo "also:"; printf '%s' "$NOTES" | sed '/^$/d' | sed 's/^/  - /'; fi
  exit 2
fi
[[ -n $NOTES ]] && printf '%s' "$NOTES"
if [[ $FAST == 1 ]]; then echo "verify-feature: PASS (static tier only; gradle skipped by --fast)"
else echo "verify-feature: PASS"; fi
exit 0
