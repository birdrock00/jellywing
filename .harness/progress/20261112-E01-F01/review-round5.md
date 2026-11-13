# Review — E01-F01 round 5 (authored artifacts only)

Date: 2026-11-13 (UTC). Scope: 4 authored-only commits `a173dfe8..ab180f64`.
Baseline from the Orchestrator, not re-derived: unit 150/0/0/1; androidTest compile green; `init.sh` green.

**C-B remains open and unexecutable in this round.** No device, credentials, P5
restore, or B6 scan exist here; no C-B row is ticked by anything below.
## 1. Vacuity, method by method
- **e2e2 (second opinion): NOT vacuous.** Positive half enforced before any
  mutation (`holders.isEmpty() ⇒ fail` pre-delete); the post-delete poll re-reads
  the *same* holder ids with a nano-time cache-buster, and `playlistMembership`
  fails loudly on non-200 rather than reading absence (`:887-928`;
  `/Playlists/{id}/Items?UserId=` gotcha honored). Weakest point: membership is
  `body.contains(itemId)` substring, not parsed `Id` — over-match can only
  inflate holders → false *red*, never false green.
- **e2e6 (second opinion): NOT vacuous.** Row-AND-bytes is positive-first on
  both halves: stale fixture force-reclaimed (`fail` on reclaim failure);
  `isCached` must start false (`:694-697`); a real row must appear ≤60 s; ≥1 of
  the 3 byte paths must exist ≤180 s; the purge loop runs while
  `isCached || anyExists`, fails at 20 s, then re-asserts each `assertFalse`.
  The 3 paths mirror `LibraryDeleteUtil.purgeCacheEntry` (`:117-132`) exactly,
  and `DownloadService` deletes `download/<id>` *before* `insertCache`, so when
  the row exists the bytes already exist — budgets generous, not fragile.
  Residual (low): a library resync could clear row/bytes independently of
  `purgeCacheEntry`; only mutation testing closes that, bound out this round.
- **e2e1/e2e3**: accepted per Orchestrator baseline (toast ordering verified);
  `requireArg` loud; no assumeTrue.
- **e2e5**: the guard is armed *by the test and proven armed before the delete*
  — positive control exists; `!sawProgressTraffic ⇒ fail` closes the
  empty-capture vacuity; without `qa_reqlog` the leg prints SKIPPED honestly.
## 2. Precondition false-red risk for the one destructive cycle
1. **BLOCKER (driver↔test ordering): e2e2's delegated scan cannot arrive while
   the test polls.** `run_instrument` is synchronous (`am instrument -w`, `:332`);
   `p3b_scan` runs only *after* it returns (`:624`). In delegated mode e2e2 POSTs
   nothing and polls holders 180 s with no scan issued in that window. If listings
   stay stale until a scan (the spec's own L4 premise), e2e2 **fails red against a
   working product**, P2 dies, a delete+restore is burned; if the server
   auto-clears playlists on DELETE, the leg goes green with no scan in its window.
   Either way the test comment "p3b_scan owns the POST" over-claims (3c). Fix:
   trigger the scan concurrently with `run_instrument` for e2e2 legs, or let
   p3b_scan own the whole post-scan assertion.
2. **e2e4 under default `--expects delete`** (medium): nothing refuses
   `run e2e4_cancelNoop` without `--expects no-delete`; P3 then expects 404 on
   an item cancel correctly left in place → false red + aborted cycle. The e2e2
   refuse-guard (`:605-614`) has no e2e4 mirror; add `e2e4*`+delete ⇒ exit 2.
3. **P0 aborts the cycle if ANY playlist has zero items** (`:257-259`). Safe
   direction, but a stray empty playlist costs the cycle. Pre-flight: no empty
   playlists; fixture inside a QA playlist; ≥2 synced local tracks (every leg
   needs `findOtherSong`); app signed-in and synced (driver never checks).
4. **e2e5 leg-1 zero-traffic rule**: with `qa_reqlog` set, no
   `Sessions/Playing` traffic in 30 s ⇒ fail; depends on the fork reporting
   progress during steady playback. Watch once on device; omitting the arg is
   the honest-skip route.
## 3. Driver ↔ test contract
`run_instrument` passes `qa_server/qa_token/qa_user/qa_item` unconditionally;
every method `requireArg`s exactly those four. `qa_scan` only with
`--scan-delegated` (driver refuses e2e2 without it); `qa_reqlog`/`qa_leg` pass
through when set. Cannot-both-skip-scan: under the driver the test never
POSTs and `p3b_scan` dies on non-2xx POST or non-completion within 900 s — no
green without a real scan **for non-empty H**. Gap: **p3b_scan with
`HOLDING_PIDS` empty passes vacuously** ("re-asserted for all 0 holder(s)").
Unreachable as a reported green on an e2e2 run (the test's holders guard fails
first), but decoration on any other `--scan-delegated` leg; p3b_scan should
require `HOLDING_PIDS ≥ 1` for destructive legs.
## 4. The `--expects` default
Correct. Default `delete` applies **only** when the flag is absent (`:122`);
explicit empty/unknown ⇒ exit 2; a trailing bare `--expects` resolves to empty
+ GIVEN=1 ⇒ exit 2 (loop shape verified; no infinite loop). The dangerous
direction (destructive leg silently losing the 404) cannot occur: the default
*is* the destructive assertion, and e2e2 refuses non-delete. Residual sharp
edge is §2.2 (e2e4 defaulting to delete) — false red, not false green.
## 5. Findings split
**Authoring defects to fix before execution:** F1 e2e2 scan-ordering blocker
(§2.1); F2 e2e4 refuse-guard mirror (§2.2); F3 p3b_scan empty-H vacuity (§3);
F4 e2e2 comment over-claiming p3b ownership (fix with F1).
**Execution-time watch (not defects):** substring-membership direction (§1);
e2e6 resync-vs-purge residual (§1); empty-playlist P0 abort, ≥2 tracks, app-sync
pre-flight (§2.3); e2e5 steady-playback traffic (§2.4); driver never executed.
**Spec mirror (Architect's fix, not the Builder's):** `.tests.md` still marks
the five E2E rows and `tools/qa-delete-cycle.sh` rows NOT YET WRITTEN — false;
correct to *authored, never executed*, never implying evidence.
Checks run here: `change-size.sh` tier **ok** (899 test lines / 6 files; no
split decision needed); `test-drift-check.sh` 0 findings / 0 waived (base
`a6568bf8~2`, scope `origin/master`).
## 6. Verdict
**VERDICT: APPROVE WITH CONDITIONS** — the round-5 artifacts are sound enough to
be worth executing once F1 and F2 are fixed (both small, driver-side; F3/F4 ride
along). C-B is certified only by a real P0→P8 run with per-test
`started:`/`finished:` evidence, which this round could not and did not attempt;
no unconditional APPROVE is issued or implied.
