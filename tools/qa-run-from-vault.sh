#!/usr/bin/env bash
# qa-run-from-vault.sh — run the QA cycle with credentials pulled from Vaultwarden, never through
# this shell's visible output.
#
# Why: the driver needs QA_SERVER / QA_TOKEN / QA_USER. The vault holds only a test ACCOUNT
# (username + password), so this script mints the token at run time and exports the three values
# into the driver's environment. No secret is ever printed, logged, or written into the repo.
#
# It also refuses, by construction, to start a destructive run when no automated restore exists —
# because P5 is a server-admin file restore and a delete with no restore path is not a test, it is
# data loss.
#
# Usage:
#   sh tools/qa-run-from-vault.sh --server https://<jellyfin-host> -- probe
#   sh tools/qa-run-from-vault.sh --server https://<jellyfin-host> \
#       --restore-cmd 'cp "$BACKUP_FILE" "$ORIGINAL_PATH"' -- run e2e1_happyPathCycle
#   sh tools/qa-run-from-vault.sh --server https://<host> --dry-run -- probe
#
# Vault item: jellyfin-test  (injects JELLYFIN_TEST_USERNAME / JELLYFIN_TEST_PASSWORD)

set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)" || exit 3

SERVER=${QA_SERVER:-}
RESTORE_CMD=${QA_RESTORE_CMD:-}
DRY=0
ITEM=jellyfin-test
VVC=${VVC_BIN:-vaultwarden-cli}
PASS_VVC_FLAGS=(--allow-insecure-mac)   # this vault's stored MACs do not verify; see history.md
ARGS=()

while [[ $# -gt 0 ]]; do
  case $1 in
    --server)       SERVER=$2; shift 2 ;;
    --restore-cmd)  RESTORE_CMD=$2; shift 2 ;;
    --item)         ITEM=$2; shift 2 ;;
    --dry-run)      DRY=1; shift ;;
    --)             shift; while [[ $# -gt 0 ]]; do ARGS+=("$1"); shift; done ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

fail() { echo "REFUSING: $*" >&2; exit 3; }

[[ -n $SERVER ]] || fail "QA_SERVER (or --server) is required. It is NOT in the vault: the jellyfin-test item carries a username and password only, with no URI field. The only other source on this host is the app's stored session, which this lane's rules forbid reading."
command -v "$VVC" >/dev/null || fail "$VVC not on PATH"

# Destructive legs need an automated restore, or they are not safe to start unattended.
sub=${ARGS[0]:-}
if [[ $sub == run || $sub == restore ]]; then
  [[ -n $RESTORE_CMD ]] || fail "'${sub}' deletes a real file from the library. P5 restores it by writing the backup back to ORIGINAL_PATH on the media store, which this host cannot reach (no NAS mount; NFS closed). Pass --restore-cmd '…' that does that write, and this refusal goes away."
fi

if [[ $DRY == 1 ]]; then
  echo "would: inject $ITEM from vaultwarden -> JELLYFIN_TEST_USERNAME/JELLYFIN_TEST_PASSWORD"
  echo "would: POST \$QA_SERVER/Users/AuthenticateByName to mint QA_TOKEN and QA_USER"
  echo "would: exec tools/qa-delete-cycle.sh ${ARGS[*]:-}"
  [[ -n $RESTORE_CMD ]] && echo "would: export QA_RESTORE_CMD (value not shown)" || echo "note:  QA_RESTORE_CMD unset — destructive legs would still refuse"
  exit 0
fi

# The real work happens inside the injected environment, so the password never transits a variable
# this script prints.
VAULT_INJECT="$ITEM" QA_SERVER="$SERVER" QA_RESTORE_CMD="$RESTORE_CMD" \
  "$VVC" "${PASS_VVC_FLAGS[@]}" run "$ITEM" -- bash -c '
    set -uo pipefail
    : "${JELLYFIN_TEST_USERNAME:?}" ; : "${JELLYFIN_TEST_PASSWORD:?}"
    body=$(printf "{\"Username\":\"%s\",\"Pw\":\"%s\"}" "$JELLYFIN_TEST_USERNAME" "$JELLYFIN_TEST_PASSWORD")
    resp=$(curl -sS -m 20 \
      -H "Content-Type: application/json" \
      -H "X-Emby-Authorization: MediaBrowser Client=\"gramophone-qa\", Device=\"qa-runner\", DeviceId=\"qa-runner-1\", Version=\"1\"" \
      -X POST "$QA_SERVER/Users/AuthenticateByName" -d "$body") || { echo "REFUSING: could not reach $QA_SERVER" >&2; exit 3; }
    QA_TOKEN=$(printf %s "$resp" | python3 -c "import json,sys;print(json.load(sys.stdin).get(\"AccessToken\",\"\"))" 2>/dev/null)
    QA_USER=$(printf %s "$resp"  | python3 -c "import json,sys;print(json.load(sys.stdin).get(\"User\",{}).get(\"Id\",\"\"))" 2>/dev/null)
    [[ -n $QA_TOKEN && -n $QA_USER ]] || { echo "REFUSING: authentication did not return a token/user id for item vault item jellyfin-test against this host" >&2; exit 3; }
    export QA_SERVER QA_TOKEN QA_USER
    echo "authenticated; QA_TOKEN and QA_USER minted (values never shown)"
    exec sh tools/qa-delete-cycle.sh "$@"
  ' _ "${ARGS[@]+"${ARGS[@]}"}"
