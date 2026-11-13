#!/usr/bin/env bash
# verify-instrumented.sh — device tier of the deterministic gate, for instrumented tests that need
# NO server session. Complements tools/verify-feature.sh (JVM + static tier).
#
# Why per-method instead of per-class, measured rather than assumed on 2026-11-13:
#   - a class-level run of an assume-skipped test still reports "OK (2 tests)" — skips are COUNTED
#     as passes in the class summary, which is precisely the false green rule R-NONASTR exists to
#     forbid;
#   - an assume-skipped method run BY NAME reports "OK (0 tests)", and a real one reports
#     "OK (1 test)" with Time > 0.
# So each method is invoked individually and only "^OK (1 test)$" counts as executed. Anything
# else is a finding, never a pass.
#
# Deliberate constraints, all learned the hard way in this lane (see qa-emulator.md, lessons.md):
#   - NEVER gradle connectedDebugAndroidTest: it uninstalls the app+test APKs afterwards and wipes
#     login state, which destroys the very session the operator-gated C-B legs need.
#   - NEVER uninstall or reboot; installs are `adb install -r -d` so state survives.
#   - Anything needing operator credentials is reported SKIPPED with a reason, never quietly.
#
# Exit: 0 all executed tests passed | 2 a test failed or did not execute | 3 preflight failed
# Usage: sh tools/verify-instrumented.sh [class ...]     default: every non-credential class

set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)" || exit 3

SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
ADB=$SDK/platform-tools/adb
AVD=${QA_AVD:-latest_api36}
APP_PKG=club.thatpetbff.gramophone.debug
TEST_PKG=$APP_PKG.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
SRC=app/src/androidTest/java
SKIP_LIST=.harness/state/device-skip-allowlist
OUT_DIR=${QA_OUT:-/tmp/verify-instrumented}
DEVICE=${QA_DEVICE:-}

mkdir -p "$OUT_DIR"

die() { echo "PREFLIGHT FAIL: $*" >&2; exit 3; }
[[ -x $ADB ]] || die "adb not found at $ADB (set ANDROID_HOME)"

# --- pick device ----------------------------------------------------------------------------------
if [[ -z $DEVICE ]]; then
  DEVICE=$($ADB devices | awk 'NR>1 && $2=="device" {print $1; exit}')
fi
if [[ -z $DEVICE ]]; then
  echo "no device online; booting $AVD headless (snapshot restore, no wipe)"
  [[ -x $SDK/emulator/emulator ]] || die "emulator not found; cannot boot a device"
  nohup "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect > "$OUT_DIR/avd-boot.log" 2>&1 &
  for _ in $(seq 1 60); do
    [[ $($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]] && break
    sleep 5
  done
  DEVICE=$($ADB devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  [[ -n $DEVICE ]] || die "device did not come online; see $OUT_DIR/avd-boot.log"
fi
echo "device: $DEVICE"

# --- build and install WITHOUT gradle's connected task --------------------------------------------
timeout 1500 ./gradlew assembleDebug assembleDebugAndroidTest -q > "$OUT_DIR/build.log" 2>&1 \
  || { tail -30 "$OUT_DIR/build.log"; die "assembleDebug/assembleDebugAndroidTest failed"; }
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for a in "$APP_APK" "$TEST_APK"; do [[ -f $a ]] || die "missing $a"; done
# -r keeps data (login state), -d allows version downgrade; -g grants permissions so a UI test does
# not stall on a runtime-permission dialog that has nothing to do with the assertion.
$ADB -s "$DEVICE" install -r -d -g "$APP_APK"  > "$OUT_DIR/install-app.log"  2>&1 || die "install app failed"
$ADB -s "$DEVICE" install -r -d -g "$TEST_APK" > "$OUT_DIR/install-test.log" 2>&1 || die "install test apk failed"

# --- enumerate methods from source (never from the APK: source is the contract) -------------------
declare -a FQNS=()
if [[ $# -gt 0 ]]; then
  FQNS=("$@")
else
  for f in $(find "$SRC" -name '*Test.java' | sort); do
    pkg=$(grep -m1 '^package' "$f" | sed 's/package[[:space:]]*//;s/;.*//')
    FQNS+=("$pkg.$(basename "$f" .java)")
  done
fi

# -x would require the WHOLE line to equal the class name, but allowlist lines carry "<class>  # reason",
# so -x silently matched nothing and the credential-gated classes ran (and correctly failed).
# Exact field compare, no regex escaping. Two earlier attempts here were both wrong: `-x` needed the
# whole line to equal the class name (but lines carry "<class>  # reason"), and a sed-escaped regex
# turned every dot into "&" because \& is a literal ampersand on the sed RHS rather than backslash +
# match. Comparing awk's first whitespace-delimited field sidesteps the entire class of bug.
is_skipped() { [[ -f $SKIP_LIST ]] && awk -v c="$1" '$1==c{f=1} END{exit !f}' "$SKIP_LIST"; }
skip_reason() { awk -v c="$1" '$1==c{ sub(/^[^#]*#[[:space:]]*/,""); print; exit }' "$SKIP_LIST" 2>/dev/null; }

pass=0; failn=0; skipn=0
FAILED=""
for cls in "${FQNS[@]}"; do
  if is_skipped "$cls"; then
    reason=$(skip_reason "$cls")
    [[ -n $reason ]] || reason="listed in $SKIP_LIST"
    echo "SKIPPED $cls — $reason"
    skipn=$((skipn+1))
    continue
  fi
  path=${cls//.//}
  srcf=$SRC/$path.java
  [[ -f $srcf ]] || { echo "FAIL $cls — no source file at $srcf"; FAILED="$FAILED$cls(missing source)\n"; failn=$((failn+1)); continue; }
  methods=$(awk '/^[[:space:]]*@Test/{t=1;next} t && /public void [A-Za-z0-9_]+/{ sub(/.*public void /,""); sub(/ *\(.*$/,""); print; t=0 }' "$srcf")
  [[ -n $methods ]] || { echo "FAIL $cls — @Test declared but no method parsed"; FAILED="$FAILED$cls(parse)\n"; failn=$((failn+1)); continue; }
  for m in $methods; do
    log="$OUT_DIR/$cls#$m.log"
    timeout 420 $ADB -s "$DEVICE" shell am instrument -w \
      -e class "$cls#$m" "$TEST_PKG/$RUNNER" > "$log" 2>&1
    rc=$?
    if grep -qE '^OK \(1 test\)$' "$log"; then
      pass=$((pass+1))
      printf '  PASS %s#%s\n' "${cls##*.}" "$m"
    elif grep -qE '^OK \(0 tests\)$' "$log"; then
      # R-NONASTR: an assumption failure is NOT a pass. It is reported as a finding so it cannot
      # quietly become a green tier.
      failn=$((failn+1))
      FAILED="${FAILED}  NOT RUN ${cls}#${m} — assumption failed, so nothing executed (R-NONASTR): $log\n"
      printf '  NOT RUN %s#%s (assumption failed — not a pass)\n' "${cls##*.}" "$m"
    else
      failn=$((failn+1))
      FAILED="${FAILED}  FAIL ${cls}#${m} (rc=$rc): $log\n"
      printf '  FAIL %s#%s (rc=%s)\n' "${cls##*.}" "$m" "$rc"
      sed -n '1,25p' "$log" | sed 's/^/      /'
    fi
  done
done

echo "-----------------------------------------------"
echo "instrumented: executed-pass=$pass not-run-or-fail=$failn classes-skipped=$skipn"
if [[ $failn -gt 0 ]]; then
  printf '%b' "$FAILED"
  echo "logs: $OUT_DIR"
  exit 2
fi
[[ $pass -gt 0 ]] || { echo "no test executed at all — that is not a pass"; exit 2; }
echo "verify-instrumented: PASS"
exit 0
