# Review — E01-F01 round 6 (F1–F4 closure only)

Date 2026-11-13 (UTC). Scope: commit `e94802c0` only (2 files; nothing under `app/src/main/**`).
**Verdict: APPROVE WITH CONDITIONS.** No blocker in the diff; one condition must land before the
operator spends a destructive cycle; **C-B remains open and unexecutable in this round.**

## Conditions F1–F4
- **F1 CLOSED** (with C-1). The guarantee moved; it did not leak — see Q1.
- **F2 CLOSED** — `tools/qa-delete-cycle.sh:631-638` mirrors the e2e2 guard; Builder executed it
  standalone (refused `rc=2` on `delete`, runs on `no-delete`); not re-derived here.
- **F3 CLOSED** — `:419` is `p3b_scan`'s first statement, before the POST, and `die` is terminal
  (`:66-74`: FAILED=1, report, `exit 1`), so `phase P3b PASS` is unreachable past it.
- **F4 CLOSED** — javadoc `:375-393` states the real ordering (`am instrument -w` blocks; p3b_scan
  runs after it returns) and that the declaration is not a skip. The over-claim is gone.

## Q1 — the moved assertion keeps its proposition (probed, not read)
Proposition: *every holder that listed the id at P0 stops resolving it after a completed scan.*
- Same subject: the test's `itemId` IS the driver's `-e qa_item "$ITEM_ID"` (`:330`) — the test
  deletes exactly the id the driver re-asserts; a mismatch could only false-red, never false-green.
- Same oracle `GET /Playlists/{id}/Items?UserId=`, non-200 = die (never read as "absent"); holder
  set `HOLDING_PIDS` = every playlist whose membership lists `ITEM_ID` (`:246-285`), i.e. the
  server-wide set — equal-or-wider than the in-test `holders` set the old loop polled.
- Scan mandatory and 2xx-gated, polled to completion, then re-asserted on a **fresh read per
  holder** — strictly more than the original single poll loop.
- Grip shown by stubbed execution (`/tmp/r6-E01-F01-reviewer/probe.sh`: lines 415-462 extracted,
  `api`/`sleep`/`die` stubbed): holder-still-lists ⇒ `die P3b … did not complete`; *only* the fresh
  re-assertion read dirty ⇒ `die P3b … still resolves the dead id after a completed scan`; clean ⇒
  PASS. The re-assertion pass therefore grips independently of the poll loop.
In-test half intact and positive-first: `:416-424` fails if no playlist lists the id; the 404
assertion (`:454`) and the queue/now-playing assertions precede the PARTIAL declaration.
## Q2 — PARTIAL surfacing: ruling = acceptable as log-only, NOT materially misleading

The report already carries the post-scan half in its own line (`phase P3b PASS … scan triggered …
and completed; holder cleanliness re-asserted`) and a green requires P3b, so the operator cannot
read the half-leg as a whole one without also ignoring the line naming the other half. The
declaration does reach the persisted log (`logcat -d >> "$log"`, `:340`). Optional, not required:
mirror the `:350` SKIPPED grep with `PARTIAL (post-scan cleanliness delegated` ⇒ `phase P2 PARTIAL`.

## Q3 — non-delegated path and green routes
`else` branch unchanged: POST `/Library/Refresh` (`:458-467`, non-delegated only, non-2xx = fail)
then `awaitHoldersClean` (`:488`, verbatim move). `run e2e2*` refuses unless `--expects delete`
**and** `--scan-delegated` (`:619-627`), so every delegated green passes `p3_assert_server` +
`p3b_scan`, and every non-delegated green passes an in-test scan. No route reaches green with neither.

## Reviewer checks
`./.harness/init.sh` exit 0. Test gates cited from the Builder's re-run (not re-derived).
`change-size.sh` vs origin/master: **tier ok** (1033 prod lines / 11 files; budget >1500/25).
`test-drift-check.sh origin/master`: 0 findings / 11 files; finer baseline (`e94802c0^
--feature-scope origin/master`): **3 DRIFT** in `DeleteFromLibraryE2eTest.java` — :383 and :490 are
javadoc/comment; :477 is the deleted delegated `fail("R4/L4: …")`, judged per 3b (moved, per the
Q1 probe) not from the diff. Builder should record a `waive-test-drift:` line naming it so the
waiver travels with the PR.

## Fix before the operator spends a destructive cycle
- **C-1 (execution-blocking, one line):** `p3b_scan`'s two membership reads (`:433`, `:452`) omit
  both knobs the deleted in-test read carried — `&Limit=1000` and the `X-QA-Read` cache-buster (L4).
  This is now the *only* carrier of the post-scan guarantee, so a page-truncated or cached "clean"
  read could forge `P3b PASS` after a real delete — and nothing compares the returned `Items`
  length against `TotalRecordCount`, so truncation is undetected today. Add `&Limit=1000`; the
  cache-buster is optional (the fresh second pass mitigates it).

## Watch during execution (needs a device; not reviewer claims)
- Per holder (not just the nominated one): `TotalRecordCount` vs `Items` length in `pl-<pid>.json` —
  the cheap C-1 check if the code is not touched first.
- `run restore` alone exits 0 with no P2/P3/P3b line at all: pre-existing, recovery-only, but it must
  never be read as a cycle pass. P3b's 900 s poll runs with the item already deleted; a timeout
  lands in the abort→P5 path.

## C-B
**C-B remains open and unexecutable in this round**: no device, no credentials, no operator P5
restore, no B6 scan trigger here; the destructive P0→P8 cycle with per-test `started:`/`finished:`
device evidence has never run. Ceiling for this round: APPROVE WITH CONDITIONS.

## Carried forward (Architect's lane — not fixed here)
`.tests.md` still marks the five E2E rows and the `tools/qa-delete-cycle.sh` rows NOT YET WRITTEN;
correct wording is *authored, never executed*, never implying evidence.
(progress/ is gitignored, so this report is intentionally left uncommitted.)
