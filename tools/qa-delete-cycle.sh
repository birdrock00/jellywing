#!/usr/bin/env bash
# qa-delete-cycle.sh — host-side driver for the E01-F01 sacrificial delete-and-restore
# cycle (P0 -> P8 of F01-delete-playing-song.tests.md). Reference-only text in this file:
# NO hostname, token, user id, email, device serial or NAS path may ever appear in it,
# in its usage text, or in any log line it writes. Driver logs default OUTSIDE the repo.
#
# Secrets arrive ONLY from the operator's runtime environment:
#   QA_SERVER   base URL of the QA server                      (required)
#   QA_TOKEN    auth token for the server oracle               (required)
#   QA_USER     user id under test                             (required)
# Optional operator-runtime inputs (never committed anywhere):
#   QA_RESTORE_CMD  command implementing the P5 server-admin restore procedure. It runs
#                   with BACKUP_FILE, BACKUP_SHA256, ORIGINAL_PATH, FIXTURE_NAME exported.
#                   Absent => the driver pauses and prompts the operator to perform the
#                   restore through their own admin procedure.
#   QA_REQLOG       URL of the captured server request log (L2). Forwarded to e2e5 as
#                   -e qa_reqlog when set; the e2e5 request-log leg reports SKIPPED,
#                   with reason, when it is not — never fabricated.
#
# Usage:
#   tools/qa-delete-cycle.sh probe
#   tools/qa-delete-cycle.sh run <e2eMethodName> [--expects delete|no-delete] [--scan-delegated] [--leg VALUE]
#   tools/qa-delete-cycle.sh restore            # resume/re-run P5->P8 after an abort
# Options:
#   --fixture NAME    fixture track name (default: Jai Hind)
#   --expects MODE    what the server must show after the act: 'delete' asserts P3's
#                     GET /Items/{id} == 404 (default when the flag is ABSENT); 'no-delete'
#                     asserts P3n's cancel guarantee (item still 200, holders unchanged) for
#                     non-destructive legs such as e2e4_cancelNoop. An empty or unrecognized
#                     VALUE is a hard error (exit 2), never a silent default. e2e4_cancelNoop
#                     is refused with the default 'delete' (it would assert a 404 on an item a
#                     correct cancel leaves in place); pass --expects no-delete for it.
#   --scan-delegated  required for e2e2*: the driver owns the library scan in p3b_scan (POST
#                     /Library/Refresh + poll to completion + fresh per-holder re-assertion),
#                     which runs AFTER the blocking instrumented run returns; e2e2 then proves
#                     only its in-test half and prints a PARTIAL declaration instead of polling
#                     for a scan that cannot have started yet.
#   --playlist NAME   fixture playlist name (default: aurral-udta-teer)
#   --log-dir DIR     log/backup target; MUST be outside the repo (default: mktemp -d)
#   --device SERIAL   adb target; must be an emulator (default: the sole emulator)
#   --method-class N  instrumented class (default: club.thatpetbff.gramophone.ui.DeleteFromLibraryE2eTest)
# Exit: 0 only if every executed phase PASSed (allowed SKIPPEDs noted); non-zero on FAIL.
set -u -o pipefail

FIXTURE_NAME="Jai Hind"
FIXTURE_PLAYLIST="aurral-udta-teer"
LOG_DIR="${TMPDIR:-/tmp}/qa-delete-cycle-$(date +%Y%m%d-%H%M%S)"
DEVICE=""
METHOD_CLASS="club.thatpetbff.gramophone.ui.DeleteFromLibraryE2eTest"
SCAN_DELEGATED=0
EXPECTS=""
EXPECTS_GIVEN=0
LEG=""
SUBCMD=""
E2E_METHOD=""
STATE_TAG="default"

# ---------------------------------------------------------------- phase plumbing
PHASES=(); PHASE_STATUS=(); PHASE_NOTE=()
phase() { # phase <ID> <PASS|FAIL|SKIPPED> <note>
    local id=$1 st=$2; shift 2
    PHASES+=("$id"); PHASE_STATUS+=("$st"); PHASE_NOTE+=("$*")
    printf 'PHASE %s %s — %s\n' "$id" "$st" "$*"
}
FAILED=0
die() { # die <PHASE-ID> <note> — abort; restore immediately if the item is already deleted
    phase "${1:-cycle}" FAIL "${2:-aborted}"
    FAILED=1
    if [[ "${DELETED_FLAG:-0}" == "1" ]]; then
        echo "ABORT after delete: running P5 restore immediately (never leave the item deleted)" >&2
        restore_sequence || phase P5 FAIL "abort-path restore failed — STOP, hand operator BACKUP_FILE+sha256 from $LOG_DIR"
    fi
    report; exit 1
}
report() {
    echo "── cycle report ──────────────────────────────────────────────"
    local i st
    for i in "${!PHASES[@]}"; do
        printf '%-6s %-8s %s\n' "${PHASES[$i]}" "${PHASE_STATUS[$i]}" "${PHASE_NOTE[$i]}"
    done
    echo "logs/backups: $LOG_DIR (outside the repo; never commit them)"
}

scrub() { # scrub — redact secrets/PII from any text before it reaches stdout or a log
    local text; text=$(cat)
    text=${text//${QA_TOKEN:-«unset»}/<token>}
    text=${text//${QA_USER:-«unset»}/<user>}
    text=${text//${QA_SERVER:-«unset»}/<server>}
    printf '%s\n' "$text"
}
logify() { # logify <file> <label> — print a labelled, scrubbed copy of a response body
    echo "── $2 ──"; scrub < "$1"
}

# ---------------------------------------------------------------- argument parsing
require_env() {
    : "${QA_SERVER:?QA_SERVER must come from the operator runtime procedure (never committed)}" || return 1
    : "${QA_TOKEN:?QA_TOKEN must come from the operator runtime procedure (never committed)}" || return 1
    : "${QA_USER:?QA_USER must come from the operator runtime procedure (never committed)}" || return 1
    QA_SERVER=${QA_SERVER%/}
}
while [[ $# -gt 0 ]]; do
    case $1 in
        probe|run|restore)
            SUBCMD=$1
            if [[ $1 == run ]]; then E2E_METHOD=${2:-}; shift; fi
            ;;
        --fixture)   FIXTURE_NAME=$2; shift ;;
        --playlist)  FIXTURE_PLAYLIST=$2; shift ;;
        --log-dir)   LOG_DIR=$2; shift ;;
        --device)    DEVICE=$2; shift ;;
        --method-class) METHOD_CLASS=$2; shift ;;
        --scan-delegated) SCAN_DELEGATED=1 ;;
        --expects)   EXPECTS_GIVEN=1; EXPECTS=${2:-}; shift ;;
        --leg)       LEG=$2; shift ;;
        --tag)       STATE_TAG=$2; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done
[[ -n $SUBCMD ]] || {
    echo "usage: qa-delete-cycle.sh probe | run <e2eMethodName> [--expects delete|no-delete] [--scan-delegated] [--leg VALUE] | restore [--log-dir DIR]" >&2
    echo "       options: --fixture NAME --playlist NAME --device SERIAL --method-class CLASS --tag TAG --expects delete|no-delete" >&2
    exit 2
}

# Explicit-per-invocation server expectation (fix 2). The DEFAULT applies only when the
# flag is ABSENT; an empty or unrecognized VALUE is a hard error, so a destructive leg can
# never lose its 404 assertion through a typo or a dropped argument.
if [[ $EXPECTS_GIVEN == 0 ]]; then
    EXPECTS=delete
elif [[ $EXPECTS != delete && $EXPECTS != no-delete ]]; then
    echo "--expects must be 'delete' or 'no-delete' (got '${EXPECTS:-<empty>}'); refusing to guess — a wrong guess silently drops the P3 404 assertion" >&2
    exit 2
fi

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
mkdir -p "$LOG_DIR"
LOG_DIR=$(cd "$LOG_DIR" && pwd -P)
case $LOG_DIR in
    "$REPO_ROOT"/*|"$REPO_ROOT")
        echo "log dir must live outside the repo (R-SEC); got a path inside $REPO_ROOT" >&2
        exit 2 ;;
esac
DRIVER_LOG=$LOG_DIR/driver.log
AUTH_HEADER='Authorization: MediaBrowser Client="jellywing-qa", Device="qa-driver", DeviceId="qa-driver", Version="1.0"'
STATE_DIR=$LOG_DIR/state
ALL_SNAPSHOT=$STATE_DIR/holders-before.txt
declare -a ITEM_IDS HOLDING_PIDS HOLDING_COUNTS HOLDING_NAMES ALL_PIDS

# ---------------------------------------------------------------- HTTP helpers
# api METHOD PATH OUTFILE -> echoes HTTP status; body (if any) written to OUTFILE.
api() {
    local method=$1 path=$2 out=$3 code
    code=$(curl -sS --connect-timeout 15 --max-time 120 -o "$out" -w '%{http_code}' \
        -X "$method" -H "$AUTH_HEADER" -H 'Accept: application/json' \
        "$QA_SERVER$path" 2>"$out.err" | scrub) || {
        scrub < "$out.err" >&2; echo "000"; return 1; }
    [[ -s $out.err ]] && scrub < "$out.err" >> "$DRIVER_LOG"
    rm -f "$out.err"
    echo "$code"
}
urlencode() { python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$1"; }
# pyf — JSON parsing via python3 (no hand-rolled regex on API payloads)
pyf() { python3 - "$@"; }

check_server_reachable() {
    local code
    code=$(api GET "/System/Info/Public" "$STATE_DIR/sysinfo.json") || true
    [[ $code == 200 ]] || die preflight "server unreachable (HTTP $code on the public info probe)"
    phase preflight PASS "server reachable"
}

check_device() {
    local devs n
    devs=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    n=$(printf '%s\n' "$devs" | grep -c . || true)
    if [[ -z $DEVICE ]]; then
        [[ $n == 1 ]] || die preflight "expected exactly one device target, found $n (R-NOREBOOT: emulator only, sole target)"
        DEVICE=$devs
    fi
    case $DEVICE in
        emulator-*) : ;;
        *) die preflight "target '$DEVICE' is not an emulator serial — physical devices are refused (R-NOREBOOT)" ;;
    esac
    phase preflight PASS "sole target is an emulator"
}

# ---------------------------------------------------------------- P0 pre-probe
# Fixture ids: exact Name matches of type Audio from a search response body.
fixture_ids_from() { # fixture_ids_from BODYFILE -> ids, one per line
    pyf "$1" "$FIXTURE_NAME" <<'PY'
import json,sys
body=json.load(open(sys.argv[1]))
for it in body.get("Items",[]):
    if it.get("Type")=="Audio" and it.get("Name")==sys.argv[2]:
        print(it.get("Id",""))
PY
}

p0_total_items() { python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("TotalRecordCount",-1))' "$1"; }

# QA_PAGE_LIMIT — page size asked for on EVERY playlist membership read; the server default
# page is small enough to truncate a holder list and forge an "absent" verdict (C-1).
QA_PAGE_LIMIT=${QA_PAGE_LIMIT:-1000}

# playlist_items_checked <playlistId> <outfile> <phase-label> [context]
# The ONE sanctioned playlist membership read (C-1): every phase routes through here so the
# anti-false-green checks live in exactly one place and a future edit cannot drop one.
#   GET /Playlists/{id}/Items?UserId=…&Limit=$QA_PAGE_LIMIT&X-QA-Read=<nonce>
#   (the UserId form is mandatory — /Items/{id}/Items 404s for a playlist id, B1.3)
#   - X-QA-Read nonce: cache-buster on every read, so an absence verdict is never served
#     from a stale cache; it is a query param, so it defeats intermediary caches too.
#   - dies <phase-label> on non-200          (B1.3: never read a bad status as "absent")
#   - dies <phase-label> if TotalRecordCount is absent (-1) and Items is non-empty:
#     a server that stops reporting it must not silently downgrade the gate.
#   - dies <phase-label> if len(Items) < TotalRecordCount (page truncation) after ONE
#     retry with Limit raised to the reported total — bounded, never a silent shrink and
#     never a paging loop that can spin forever. TotalRecordCount 0 with empty Items is
#     legitimately empty and passes.
# Echoes the number of Items on stdout; the JSON body stays in <outfile>. CALLERS MUST
# invoke it bare (NOT inside $(…)): die() prints its PHASE FAIL and report to stdout, and
# command substitution would capture — i.e. swallow — exactly the failure output.
playlist_items_checked() {
    local pid=$1 out=$2 label=$3 ctx=${4:-membership read}
    local limit=$QA_PAGE_LIMIT attempt nonce code total n
    for attempt in first retry; do
        nonce=$(date +%s%N)$RANDOM
        code=$(api GET "/Playlists/$pid/Items?UserId=$QA_USER&Limit=$limit&X-QA-Read=$nonce" "$out")
        [[ $code == 200 ]] || die "$label" "playlist items query (GET /Playlists/{id}/Items) returned HTTP $code for playlist $pid ($ctx) — failing loudly instead of reading it as 'not held' (B1.3)"
        total=$(p0_total_items "$out" 2>/dev/null) || die "$label" "playlist items body for $pid ($ctx) did not parse — failing loudly (B1.3)"
        n=$(pyf "$out" <<'PY'
import json,sys
print(len(json.load(open(sys.argv[1])).get("Items",[])))
PY
) || die "$label" "playlist items body for $pid ($ctx) did not parse — failing loudly (B1.3)"
        # Truncation gate: a partial listing must never shrink into an "absent" verdict.
        if [[ $total != -1 && $n -lt $total ]]; then
            [[ $attempt == first && $total -gt $limit ]] || die "$label" "playlist $pid ($ctx) returned a TRUNCATED listing: $n of $total items (Limit=$limit) — an id missing from a partial listing proves nothing; refusing to read it as absent (C-1)"
            limit=$total
            continue
        fi
        # Missing-reporting gate: TotalRecordCount absent with non-empty Items ⇒ die.
        [[ $total != -1 || $n == 0 ]] || die "$label" "playlist $pid ($ctx) returned $n items with NO TotalRecordCount in the body — the truncation gate cannot be verified; refusing to accept this listing as evidence (C-1)"
        echo "$n"
        return 0
    done
}

playlist_ids_from() { python3 -c 'import json,sys;[print(i["Id"]) for i in json.load(open(sys.argv[1])).get("Items",[])]' "$1"; }

p0_probe() {
    mkdir -p "$STATE_DIR"
    # L1 policy probe (best effort; SKIPPED with reason when the oracle needs admin)
    local code pol del_any
    code=$(api GET "/Users/$QA_USER/Policy" "$STATE_DIR/policy.json") || true
    if [[ $code == 200 ]]; then
        del_any=$(pyf "$STATE_DIR/policy.json" <<'PY'
import json,sys
p=json.load(open(sys.argv[1]))
print("yes" if (p.get("EnableContentDeletion") or p.get("EnableContentDeletionFromMyMedia")) else "no")
PY
) || del_any=unknown
        [[ $del_any == yes ]] || die P0 "deletion policy probe says media deletion is NOT enabled for this user (L1) — fix the fixture policy, do not improvise"
        phase P0 PASS "policy probe (L1): deletion enabled"
    else
        phase P0 SKIPPED "policy probe (L1) needs an admin-capable token (HTTP $code) — not fabricated"
    fi

    # Resolve the fixture BY NAME; refuse zero or multiple Audio matches (B1.4).
    code=$(api GET "/Items?SearchTerm=$(urlencode "$FIXTURE_NAME")&IncludeItemTypes=Audio&Recursive=true&UserId=$QA_USER&Fields=Path" "$STATE_DIR/search.json")
    [[ $code == 200 ]] || die P0 "fixture search failed (HTTP $code)"
    mapfile -t ITEM_IDS < <(fixture_ids_from "$STATE_DIR/search.json" | scrub)
    if [[ ${#ITEM_IDS[@]} -eq 0 ]]; then
        die P0 "fixture '$FIXTURE_NAME' resolves to ZERO Audio records — fix the fixture, never substitute another item (B1.4)"
    elif [[ ${#ITEM_IDS[@]} -gt 1 ]]; then
        die P0 "fixture '$FIXTURE_NAME' resolves to ${#ITEM_IDS[@]} Audio records — ambiguous fixture, abort (B1.4)"
    fi
    ITEM_ID=${ITEM_IDS[0]}

    # Favorite guard (B2): an operator-favorited fixture aborts the run.
    code=$(api GET "/Items/$ITEM_ID?Fields=UserUserData&UserId=$QA_USER" "$STATE_DIR/item.json")
    [[ $code == 200 ]] || die P0 "fixture item fetch failed (HTTP $code)"
    local fav; fav=$(pyf "$STATE_DIR/item.json" <<'PY'
import json,sys
print("yes" if json.load(open(sys.argv[1])).get("UserData",{}).get("IsFavorite") else "no")
PY
)
    [[ $fav == no ]] || die P0 "fixture is flagged as a favorite (B2) — abort; do not proceed with a different item"

    # Enumerate EVERY playlist via the CORRECT endpoint (B1.3):
    #   GET /Playlists/{playlistId}/Items?UserId=…   (NOT /Items/{id}/Items, which 404s for playlists)
    code=$(api GET "/Users/$QA_USER/Playlists?UserId=$QA_USER" "$STATE_DIR/playlists.json")
    [[ $code == 200 ]] || die P0 "playlist enumeration failed (HTTP $code) — never treated as 'no playlists'"
    mapfile -t ALL_PIDS < <(playlist_ids_from "$STATE_DIR/playlists.json" | scrub)
    [[ ${#ALL_PIDS[@]} -gt 0 ]] || die P0 "server lists zero playlists — premise broken (never read as 'fixture unheld')"

    HOLDING_PIDS=(); HOLDING_COUNTS=(); HOLDING_NAMES=(); : > "$ALL_SNAPSHOT"
    local pid c ids held total pname
    for pid in "${ALL_PIDS[@]}"; do
        # Fail LOUDLY on 404/empty/truncation/stale cache (B1.3/C-1): a wrong-endpoint, cached
        # or page-truncated listing must never masquerade as "this playlist does not hold the
        # fixture" — the checks live in playlist_items_checked, not at this call site.
        playlist_items_checked "$pid" "$STATE_DIR/pl-$pid.json" P0 "P0 holder scan"
        total=$(p0_total_items "$STATE_DIR/pl-$pid.json")
        ids=$(pyf "$STATE_DIR/pl-$pid.json" <<'PY'
import json,sys
print("\n".join(i.get("Id","") for i in json.load(open(sys.argv[1])).get("Items",[])))
PY
) || die P0 "playlist items body for a scanned playlist did not parse — failing loudly (B1.3)"
        if [[ $total -le 0 || -z $ids ]]; then
            die P0 "a playlist returned an EMPTY item list via the membership endpoint — failing loudly instead of trusting emptiness (B1.3)"
        fi
        held=no; grep -qxF "$ITEM_ID" <<< "$ids" && held=yes
        printf '%s\t%s\t%s\n' "$pid" "$total" "$held" >> "$ALL_SNAPSHOT"
        # P7 before-snapshot of every playlist's id set (stored in the log dir, never the repo).
        # Filename MUST stay in sync with the read in p7_audit (both use ids-before-<pid>.txt).
        pyf "$STATE_DIR/pl-$pid.json" > "$STATE_DIR/ids-before-$pid.txt" <<'PY'
import json,sys
print("\n".join(x.get("Id","") for x in json.load(open(sys.argv[1])).get("Items",[])))
PY
        if [[ $held == yes ]]; then
            pname=$(pyf "$STATE_DIR/playlists.json" "$pid" <<'PY'
import json,sys
for i in json.load(open(sys.argv[1])).get("Items",[]):
    if i.get("Id")==sys.argv[2]: print(i.get("Name",""))
PY
)
            HOLDING_PIDS+=("$pid"); HOLDING_COUNTS+=("$total"); HOLDING_NAMES+=("${pname:-playlist-$(( ${#HOLDING_PIDS[@]} + 1 ))}")
        fi
    done

    [[ ${#HOLDING_PIDS[@]} -gt 0 ]] || die P0 "fixture is held by NO playlist (H empty) — protocol premise broken; fix the fixture (B2)"
    # The nominated playlist must be among the holders.
    local want=0 pid2 nm
    for pid2 in "${HOLDING_PIDS[@]}"; do
        nm=$(pyf "$STATE_DIR/playlists.json" "$pid2" <<'PY'
import json,sys
for i in json.load(open(sys.argv[1])).get("Items",[]):
    if i.get("Id")==sys.argv[2]: print(i.get("Name",""))
PY
)
        [[ $nm == "$FIXTURE_PLAYLIST" ]] && want=1
    done
    [[ $want == 1 ]] || die P0 "nominated playlist '$FIXTURE_PLAYLIST' does not hold the fixture (H mismatch vs snapshot) — abort (B2)"

    phase P0 PASS "fixture resolved by name (1 Audio); H = ${#HOLDING_PIDS[@]} playlist(s), counts recorded; not-favorited asserted"
}

# ---------------------------------------------------------------- P1 backup
p1_backup() {
    BACKUP_FILE=$LOG_DIR/fixture-backup
    local code rng
    rng=$(curl -sS --connect-timeout 15 --max-time 600 -o "$BACKUP_FILE" -w '%{http_code}' \
        -H "$AUTH_HEADER" -H 'Range: bytes=0-' \
        "$QA_SERVER/Items/$ITEM_ID/Download" 2>&1 | scrub) || die P1 "ranged download failed: $rng"
    code=${rng##* }
    [[ $code == 206 ]] || die P1 "ranged GET returned HTTP $code (expected 206 + media bytes) — abort before any destructive call"
    [[ -s $BACKUP_FILE ]] || die P1 "backup file is empty — abort before any destructive call"
    BACKUP_SHA256=$(sha256sum "$BACKUP_FILE" | cut -d' ' -f1)
    ORIGINAL_PATH=$(pyf "$STATE_DIR/item.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1])).get("Path",""))
PY
)
    [[ -n $ORIGINAL_PATH ]] || die P1 "server did not report the original library path — P5 would be impossible; abort (path stays in this log dir only, never in the repo)"
    printf 'ITEM_ID=%s\nBACKUP_FILE=%s\nBACKUP_SHA256=%s\nORIGINAL_PATH=%s\n' \
        "$ITEM_ID" "$BACKUP_FILE" "$BACKUP_SHA256" "$ORIGINAL_PATH" > "$STATE_DIR/run.env"
    phase P1 PASS "backup + sha256 recorded outside the repo (path/size not printed)"
}

# ---------------------------------------------------------------- P2/P4 device act (E2E inside)
run_instrument() { # run_instrument METHOD_NAME
    local method=$1 log=$LOG_DIR/instrument-$method.log
    : > "$log"
    local args=(-e class "$METHOD_CLASS#$method"
                -e qa_server "$QA_SERVER" -e qa_token "$QA_TOKEN"
                -e qa_user "$QA_USER" -e qa_item "$ITEM_ID")
    [[ $SCAN_DELEGATED == 1 ]] && args+=(-e qa_scan delegated)
    [[ -n ${QA_REQLOG:-} ]] && args+=(-e qa_reqlog "$QA_REQLOG")
    [[ -n $LEG ]] && args+=(-e qa_leg "$LEG")
    # The act may delete: from here on, ANY abort must trigger P5 restore first.
    DELETED_FLAG=1
    adb -s "$DEVICE" logcat -c >/dev/null 2>&1 || true
    echo "== am instrument -w $METHOD_CLASS#$method ==" > "$log"
    adb -s "$DEVICE" shell am instrument -w "${args[@]}" >> "$log" 2>&1
    local rc=$?
    adb -s "$DEVICE" logcat -d >> "$log" 2>&1 || true
    scrub < "$log" > "$LOG_DIR/instrument-$method.scrubbed.log"
    # R-EV: any assumption failure or self-skip means NOT RUN — that is an abort, never green.
    if grep -qiE 'assumption (failure|failed)|^OK \(0 tests\)|Tests found: 0|tests run: 0' "$log"; then
        die P2 "$method reported an assumption failure / zero-test run (R-EV: a green run where nothing ran is a FAIL)"
    fi
    if [[ $rc -ne 0 ]] || ! grep -qE '^OK \(1 test\)' "$log"; then
        die P2 "$method did not complete OK (am instrument rc=$rc) — see $LOG_DIR/instrument-$method.scrubbed.log"
    fi
    # Surface explicit SKIPPED oracles (e.g. e2e5's L2 request-log leg) into the report honestly.
    if grep -q 'SKIPPED (request-log oracle unavailable' "$log"; then
        phase P2 SKIPPED "$method executed OK; request-log oracle leg reported SKIPPED (L2) — recorded, not fabricated"
    else
        phase P2 PASS "$method executed OK with per-test evidence in the instrument stream"
    fi
}

# ---------------------------------------------------------------- P3 server assertions
p3_assert_server() {
    local code body=$STATE_DIR/after.json
    code=$(api GET "/Items/$ITEM_ID" "$body") || true
    [[ $code == 404 ]] || die P3 "GET /Items/{deletedId} returned HTTP $code, expected 404 — the delete did not reach the server"
    local i pid total ids
    for i in "${!HOLDING_PIDS[@]}"; do
        pid=${HOLDING_PIDS[$i]}
        playlist_items_checked "$pid" "$STATE_DIR/pl-after-$pid.json" P3 "post-delete read for a known holder — never faking cleanliness (B1.3/C-1)"
        total=$(p0_total_items "$STATE_DIR/pl-after-$pid.json")
        ids=$(pyf "$STATE_DIR/pl-after-$pid.json" <<'PY'
import json,sys
print("\n".join(x.get("Id","") for x in json.load(open(sys.argv[1])).get("Items",[])))
PY
) || die P3 "holder item list did not parse after delete — failing loudly (B1.3)"
        grep -qxF "$ITEM_ID" <<< "$ids" && die P3 "holder ${HOLDING_NAMES[$i]} still lists the deleted id (P3)"
        if [[ ${HOLDING_COUNTS[$i]} -gt 1 ]]; then
            [[ $total -gt 0 ]] || die P3 "holder ${HOLDING_NAMES[$i]} returned an EMPTY list post-delete where ${HOLDING_COUNTS[$i]}-1 were expected — emptiness is not evidence (B1.3)"
        fi
    done
    phase P3 PASS "GET /Items/{id} 404; no holder lists the dead id (correct membership endpoint)"
}

# P3n — cancel guarantee for a NON-destructive leg (e2e4_cancelNoop).
# p3_assert_server's 404 would fail a passing cancel test (the item is designed to stay),
# but a cancel leg still owes a positive assertion, so nothing is skipped: the item is
# still served 200 and every holder in H still lists its P0 entry count and the item.
# The phase has its own ID so the report shows it actually ran.
p3n_assert_cancel() {
    local code body=$STATE_DIR/after-cancel.json i pid total ids
    code=$(api GET "/Items/$ITEM_ID" "$body") || true
    [[ $code == 200 ]] || die P3n "GET /Items/{id} returned HTTP $code after a cancelled dialog — expected 200: cancel must leave the item on the server"
    for i in "${!HOLDING_PIDS[@]}"; do
        pid=${HOLDING_PIDS[$i]}
        playlist_items_checked "$pid" "$STATE_DIR/pl-after-$pid.json" P3n "post-cancel read for a known holder (B1.3/C-1)"
        total=$(p0_total_items "$STATE_DIR/pl-after-$pid.json")
        [[ $total == "${HOLDING_COUNTS[$i]}" ]] || die P3n "holder ${HOLDING_NAMES[$i]} lists $total entries after cancel, expected its P0 count ${HOLDING_COUNTS[$i]} — cancel must not mutate a holder"
        ids=$(pyf "$STATE_DIR/pl-after-$pid.json" <<'PY'
import json,sys
print("\n".join(x.get("Id","") for x in json.load(open(sys.argv[1])).get("Items",[])))
PY
) || die P3n "holder item list did not parse after cancel — failing loudly (B1.3)"
        grep -qxF "$ITEM_ID" <<< "$ids" || die P3n "holder ${HOLDING_NAMES[$i]} no longer lists the item after a cancelled dialog (P3n)"
    done
    phase P3n PASS "GET /Items/{id} 200 after cancel; every holder still lists its P0 entry count and the item"
}

# ---------------------------------------------------------------- P3b delegated library scan (B6)
# In delegated mode e2e2_playlistCleanAfterScan asserts only what it can observe in-test and
# prints a PARTIAL declaration; THIS function is the post-scan half of that leg — the only
# place on the delegated path where a scan exists, so it is the only place cleanliness after
# a scan can be asserted. run_instrument is synchronous (`am instrument -w`), so this runs
# strictly AFTER the instrumentation returns; the test never waits here for a scan that has
# not been issued yet. Asserting cleanliness without a completed scan is exactly the vacuity
# this feature keeps hitting (B6). Uses the stock POST /Library/Refresh endpoint, which needs
# admin rights — a non-2xx is a FAIL that names the operator step, never a pass.
p3b_scan() {
    local code trigger i pid ids waited=0 limit=900 clean
    # Every loop below iterates over HOLDING_PIDS; with an empty set they all no-op, `clean`
    # stays 1 and this function would report green having asserted nothing. Require a holder.
    [[ ${#HOLDING_PIDS[@]} -gt 0 ]] || die P3b "holder set is empty (H = 0) — post-scan cleanliness over zero holders proves nothing (B1.3); this leg may not report green"
    code=$(api POST "/Library/Refresh" "$STATE_DIR/refresh.json") || true
    trigger=$code
    case $trigger in
        2*) : ;;
        *) die P3b "POST /Library/Refresh returned HTTP $trigger — the QA token cannot trigger a library scan. OPERATOR STEP REQUIRED: run the scan from the dashboard (or supply an admin-capable token); this leg may not report green without a completed scan (B6)" ;;
    esac
    # Completion is observable only through its effect, so poll the B1.3 membership
    # endpoint until no holder resolves the dead id, bounded by $limit.
    clean=0
    while (( waited < limit )); do
        clean=1
        for i in "${!HOLDING_PIDS[@]}"; do
            pid=${HOLDING_PIDS[$i]}
            playlist_items_checked "$pid" "$STATE_DIR/pl-scan-$pid.json" P3b "poll while awaiting the delegated scan — never read as absent (B1.3/C-1)"
            ids=$(pyf "$STATE_DIR/pl-scan-$pid.json" <<'PY'
import json,sys
print("\n".join(x.get("Id","") for x in json.load(open(sys.argv[1])).get("Items",[])))
PY
) || die P3b "holder item list did not parse during the delegated scan — failing loudly (B1.3)"
            if grep -qxF "$ITEM_ID" <<< "$ids"; then
                clean=0
            fi
        done
        [[ $clean == 1 ]] && break
        sleep 15; waited=$((waited + 15))
    done
    [[ $clean == 1 ]] || die P3b "the delegated scan did not complete within ${limit}s — a holder still resolves the dead id; the operator must confirm the scan in the dashboard before this leg can report (B6: no scan, no green)"
    # Post-completion re-assertion of holder cleanliness against every pid in H, on a fresh
    # query per holder (the verdict is never carried over from the poll loop's last read).
    for i in "${!HOLDING_PIDS[@]}"; do
        pid=${HOLDING_PIDS[$i]}
        playlist_items_checked "$pid" "$STATE_DIR/pl-scan-clean-$pid.json" P3b "post-scan cleanliness re-assert for a known holder — never read as absent (B1.3/C-1)"
        ids=$(pyf "$STATE_DIR/pl-scan-clean-$pid.json" <<'PY'
import json,sys
print("\n".join(x.get("Id","") for x in json.load(open(sys.argv[1])).get("Items",[])))
PY
) || die P3b "holder item list did not parse after the scan — failing loudly (B1.3)"
        grep -qxF "$ITEM_ID" <<< "$ids" && die P3b "holder ${HOLDING_NAMES[$i]} still resolves the dead id after a completed scan (P3b/B6)"
    done
    phase P3b PASS "library scan triggered (POST /Library/Refresh HTTP $trigger) and completed; holder cleanliness re-asserted post-scan for all ${#HOLDING_PIDS[@]} holder(s) (B6)"
}

# ---------------------------------------------------------------- P5 restore (operator procedure)
poll_reindexed() {
    local waited=0 limit=900 code ids n
    while (( waited < limit )); do
        code=$(api GET "/Items?SearchTerm=$(urlencode "$FIXTURE_NAME")&IncludeItemTypes=Audio&Recursive=true&UserId=$QA_USER" "$STATE_DIR/reindex.json")
        if [[ $code == 200 ]]; then
            ids=$(fixture_ids_from "$STATE_DIR/reindex.json" | scrub) || ids=""
            n=$(grep -c . <<< "$ids" || true)
            if [[ $n == 1 ]]; then
                NEW_ID=$(head -n1 <<< "$ids")
                [[ $NEW_ID != "$ITEM_ID" ]] && { echo "re-indexed under a NEW id (asserted as a fact, P7)"; return 0; }
            fi
        fi
        sleep 15; waited=$((waited + 15))
    done
    return 1
}

restore_sequence() {
    mkdir -p "$STATE_DIR"
    if [[ -z ${BACKUP_FILE:-} || ! -f ${BACKUP_FILE:-/nonexistent} ]]; then
        echo "STOP: backup file unavailable (expected under $LOG_DIR). Hand the operator the P5 procedure reference." >&2
        return 1
    fi
    echo "P5 restore is an OPERATOR server-admin step (B8a). Backup: $BACKUP_FILE"
    echo "sha256: $BACKUP_SHA256; destination folder: (recorded in $STATE_DIR/run.env only — never in the repo)"
    if [[ -n ${QA_RESTORE_CMD:-} ]]; then
        BACKUP_FILE=$BACKUP_FILE BACKUP_SHA256=$BACKUP_SHA256 ORIGINAL_PATH=$ORIGINAL_PATH \
            FIXTURE_NAME=$FIXTURE_NAME bash -c "$QA_RESTORE_CMD" || return 1
    elif [[ -t 0 ]]; then
        read -r -p "Perform the restore through your admin procedure, then press Enter… " _
    else
        echo "No QA_RESTORE_CMD and no TTY: cannot perform P5 unattended. STOP." >&2
        return 1
    fi
    poll_reindexed || return 1
    printf 'NEW_ID=%s\n' "$NEW_ID" >> "$STATE_DIR/run.env"
    DELETED_FLAG=0
    phase P5 PASS "restored via operator procedure; re-indexed under a NEW id (fact, P7)"
    return 0
}

# ---------------------------------------------------------------- P6 re-add
p6_readd() {
    local i pid code total waited target
    for i in "${!HOLDING_PIDS[@]}"; do
        pid=${HOLDING_PIDS[$i]}; target=${HOLDING_COUNTS[$i]}
        code=$(api POST "/Playlists/$pid/AddItem?Ids=$NEW_ID&UserId=$QA_USER" "$STATE_DIR/add-$pid.json")
        [[ $code == 204 || $code == 200 ]] || code=$(api POST "/Playlists/$pid/AddItems?Ids=$NEW_ID&UserId=$QA_USER" "$STATE_DIR/add-$pid.json")
        [[ $code == 204 || $code == 200 ]] || die P6 "stock add-to-playlist failed for a holder (HTTP $code) — P6 count cannot be restored"
        waited=0
        while (( waited < 120 )); do
            playlist_items_checked "$pid" "$STATE_DIR/pl-readd-$pid.json" P6 "re-add verification — never read as absent (B1.3/C-1)"
            total=$(p0_total_items "$STATE_DIR/pl-readd-$pid.json")
            [[ $total == "$target" ]] && break
            sleep 5; waited=$((waited + 5))
        done
        [[ $total == "$target" ]] || die P6 "holder ${HOLDING_NAMES[$i]} entry count $total != P0 value $target (B8b)"
    done
    phase P6 PASS "every holder back to its P0 entry count (order recorded, not asserted)"
}

# ---------------------------------------------------------------- P7 as-found audit
p7_audit() {
    local code ids n fav pid before after
    code=$(api GET "/Items?SearchTerm=$(urlencode "$FIXTURE_NAME")&IncludeItemTypes=Audio&Recursive=true&UserId=$QA_USER" "$STATE_DIR/audit.json")
    [[ $code == 200 ]] || die P7 "audit search failed (HTTP $code)"
    ids=$(fixture_ids_from "$STATE_DIR/audit.json" | scrub); n=$(grep -c . <<< "$ids" || true)
    [[ $n == 1 ]] || die P7 "fixture exists $n times after restore (expected exactly once)"

    # Asserted AS FACTS (never as failures) per P7: new id, favorites/play-history non-survival.
    local facts="restored id is NEW ($( [[ $NEW_ID != "$ITEM_ID" ]] && echo yes || echo no ))"
    fav=$(api GET "/Items/$NEW_ID?Fields=UserUserData&UserId=$QA_USER" "$STATE_DIR/newitem.json")
    if [[ $fav == 200 ]]; then
        local ud
        ud=$(pyf "$STATE_DIR/newitem.json" <<'PY'
import json,sys
u=json.load(open(sys.argv[1])).get("UserData",{})
print("IsFavorite=%s PlayCount=%s PlaybackPositionTicks=%s" % (
    u.get("IsFavorite"), u.get("PlayCount"), u.get("PlaybackPositionTicks")))
PY
)
        facts+=", favorites/play-history on the restored item: $ud — asserted as FACTS that these do NOT survive (a test asserting survival would be wrong by design)"
    else
        facts+=", UserData query unavailable (HTTP $fav) — fact not gathered, not faked"
    fi

    local pid_line
    while IFS=$'\t' read -r pid _ held; do
        playlist_items_checked "$pid" "$STATE_DIR/pl-audit-$pid.json" P7 "as-found audit membership read (B1.3/C-1)"
        after=$(pyf "$STATE_DIR/pl-audit-$pid.json" <<'PY'
import json,sys
print("\n".join(sorted(x.get("Id","") for x in json.load(open(sys.argv[1])).get("Items",[]))))
PY
)
        # Same filename as the p0_probe write site. An absent/empty snapshot is a LOUD
        # failure: comparing against "" would flip the P7 verdict in either direction, so a
        # restore after a process restart reports a missing snapshot instead of silently
        # auditing against empty.
        local snap="$STATE_DIR/ids-before-$pid.txt"
        [[ -s $snap ]] || die P7 "no before-snapshot for a scanned playlist — the as-found audit cannot run (never compared against empty)"
        before=$(cat "$snap")
        if [[ $held == yes ]]; then
            grep -qxF "$NEW_ID" <<< "$after" || die P7 "a P0 holder no longer holds the restored item (P7)"
        else
            if [[ "$(grep -vxF "$ITEM_ID" <<< "$before" | sort)" != "$(grep -vxF "$NEW_ID" <<< "$after" | sort)" ]]; then
                die P7 "a playlist OUTSIDE H changed during the cycle (P7) — STOP, repair with the operator"
            fi
        fi
    done < "$ALL_SNAPSHOT"
    phase P7 PASS "as-found audit clean; facts: $facts"
}

p8_teardown() {
    case $LOG_DIR in
        "$REPO_ROOT"/*) die P8 "log dir inside repo detected at teardown — R-SEC" ;;
    esac
    git -C "$REPO_ROOT" status --porcelain | grep -q . && \
        echo "note: repo has pending changes (unrelated to driver logs; logs are outside the repo)"
    phase P8 PASS "logs/backups outside the repo; emulator reset by relaunch only (no reboot)"
}

load_state() {
    [[ -f $STATE_DIR/run.env ]] || { echo "no recorded cycle state in $LOG_DIR (pass --log-dir of the aborted run)" >&2; exit 2; }
    # shellcheck disable=SC1091
    source "$STATE_DIR/run.env"
    local pid
    HOLDING_PIDS=(); HOLDING_COUNTS=(); HOLDING_NAMES=()
    while IFS=$'\t' read -r pid _ held; do
        [[ $held == yes ]] && { HOLDING_PIDS+=("$pid"); }
    done < "$ALL_SNAPSHOT"
    # counts + names come from the recorded snapshot / playlists.json
    local i
    for i in "${!HOLDING_PIDS[@]}"; do
        pid=${HOLDING_PIDS[$i]}
        HOLDING_COUNTS+=("$(awk -F'\t' -v p="$pid" '$1==p{print $2}' "$ALL_SNAPSHOT")")
        HOLDING_NAMES+=("playlist-$((i + 1))")
    done
}

main() {
    require_env
    check_server_reachable
    case $SUBCMD in
        probe)
            check_device
            p0_probe
            ;;
        run)
            [[ -n $E2E_METHOD ]] || { echo "run needs an E2E method name" >&2; exit 2; }
            # B6: e2e2 asserts cleanliness AFTER a scan. Running it with no delegated scan
            # available would report green on an assertion that never had its precondition,
            # so it refuses rather than runs.
            if [[ $E2E_METHOD == e2e2* ]]; then
                [[ $EXPECTS == delete ]] || {
                    echo "refusing to run $E2E_METHOD with --expects $EXPECTS: it is a destructive leg and would lose its P3 404 assertion" >&2
                    exit 2
                }
                [[ $SCAN_DELEGATED == 1 ]] || {
                    echo "refusing to run $E2E_METHOD without --scan-delegated: the OPERATOR STEP it needs is a delegated library scan (the driver POSTs /Library/Refresh and awaits completion; needs an admin-capable token, else trigger the scan in the dashboard). Asserting post-scan cleanliness with no scan is the vacuity B6 forbids, so this leg is NOT RUN." >&2
                    exit 2
                }
            fi
            # Mirror guard for the cancel leg: --expects defaults to `delete` (see above), so
            # `run e2e4_cancelNoop` with no flag would send P3 after a 404 on an item that a
            # correct cancel leaves in place — a false red against a working product, paid for
            # after the cycle has already performed a real delete.
            if [[ $E2E_METHOD == e2e4* ]]; then
                [[ $EXPECTS != delete ]] || {
                    echo "refusing to run $E2E_METHOD with --expects delete: it is the non-destructive cancel leg, so P3 would assert a 404 on an item a correct cancel leaves in place. Pass --expects no-delete." >&2
                    exit 2
                }
            fi
            check_device
            p0_probe
            p1_backup
            run_instrument "$E2E_METHOD"
            # Server expectation is explicit per invocation (see --expects); a destructive
            # leg always runs p3_assert_server's 404, a cancel leg runs P3n instead.
            if [[ $EXPECTS == delete ]]; then
                p3_assert_server
                [[ $SCAN_DELEGATED == 1 ]] && p3b_scan
            else
                p3n_assert_cancel
            fi
            restore_sequence || die P5 "restore did not complete — STOP, do NOT retry the delete; hand the operator BACKUP_FILE + sha256 (see $STATE_DIR/run.env)"
            p6_readd
            p7_audit
            p8_teardown
            ;;
        restore)
            load_state
            restore_sequence || die P5 "restore did not complete — STOP per B8a"
            p6_readd
            p7_audit
            p8_teardown
            ;;
    esac
    report
    [[ $FAILED == 0 ]]
}

main "$@"
