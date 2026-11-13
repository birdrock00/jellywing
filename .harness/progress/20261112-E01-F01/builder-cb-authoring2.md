# Builder notes — E01-F01 C-B/B1 authoring, round 5 (builder-cb-authoring2)

Date: 2026-11-13 (UTC). Backend `in-session`. Role `builder`.
**Nothing in this run executes a destructive cycle, an instrumented test, or an emulator
action. Authoring only.** No app session and no credentials exist in this environment.

## Acceptance list (from the brief)

- [ ] D1: five E2E methods authored in `DeleteFromLibraryE2eTest.java`
      (`e2e1_happyPathCycle`, `e2e2_playlistCleanAfterScan`, `e2e3_alreadyGoneSecondDelete`,
      `e2e5_noSessionReportsForDeadId`, `e2e6_cachedSongPurged`) with lone-entry leg FIRST in e2e1.
- [ ] R-EV: no leg passes by skipping. Only e2e5's request-log leg (L2) may skip, printing exactly
      `SKIPPED (request-log oracle unavailable, L2)`. Every other absent precondition FAILs by name.
- [ ] D2.1: `tools/qa-delete-cycle.sh` P7 snapshot filename mismatch (`ids-before-$pid.json` vs
      `.txt`) fixed; unavailable snapshot is a loud failure, never `""`.
- [ ] D2.2: per-method P3 expectation is explicit (delete → 404; `e2e4_cancelNoop` → no-delete),
      so a cancel leg cannot fail P3 and a destructive leg cannot lose its 404 assertion.
- [ ] D2.3: `--scan-delegated` is not decoration — either implement POST /Library/Refresh +
      poll-for-completion + re-assert, or refuse to run `e2e2` naming the missing operator step.
- [ ] Gate: `./gradlew compileDebugAndroidTestSources` passes.
- [ ] Gate: `./gradlew testDebugUnitTest` stays 150 tests / 0 failures / 1 skipped.
- [ ] Commits: explicit paths, one per coherent unit; no `git add -A`, no push, no status change.
- [ ] Boundaries held: only `app/src/androidTest/**` + `tools/**`; no `app/src/main/**`, no
      `.harness/specs/**`, no task-box ticking.

## Status: IN PROGRESS

## Notes (appended as work proceeds)


## Driver fixes (tools/qa-delete-cycle.sh) — authored, still NEVER run
- Fix 1 (P7 vacuous corruption verdict): p0_probe write site (now line 263) and p7_audit read site (line 552) both use `ids-before-$pid.txt`; the read is guarded by `[[ -s $snap ]] || die P7 …` so a missing snapshot fails loudly instead of comparing against `""`. Verified by grep (two hits, identical filename string) + `bash -n`. Note for the restore path: a `restore` after a process restart now dies loudly at P7 rather than auditing against empty — intended.
- Fix 2 (P3's 404 asserted for every method): added `--expects delete|no-delete` (parse line 107, validation lines 123-128; default `delete` ONLY when the flag is absent, empty/unrecognized VALUE is exit 2). `main()` line 622 branches: `delete` → `p3_assert_server` (404, unchanged), `no-delete` → new `p3n_assert_cancel` (line 379: item 200 + every holder still at its P0 count and still listing the item) reported as its own `P3n` phase. Verified by `bash tools/qa-delete-cycle.sh run x --expects bogus` / `--expects ""` → exit 2 with the hard-error message (no network touched), and grep that `p3_assert_server` is still on the delete branch.
- Fix 3 (`--scan-delegated` was decoration): added `p3b_scan()` (line 405) — `POST /Library/Refresh` via `api`, non-2xx → `die P3b` naming the operator dashboard-scan step (never a pass); polls the B1.3 membership endpoint per holder (bounded 900s) for completion, then re-queries and re-asserts every pid in H, dying if any still resolves the dead id. Wired at line 624 between P3 and P5 restore, only when `--scan-delegated`; `e2e2*` without it now refuses at line 611 with the missing operator step named. `die()`'s `DELETED_FLAG` → `restore_sequence` contract untouched, so any new post-act abort still restores first.
- Gate: `bash -n` passes; `shellcheck` is not on PATH and was not installed. Static checks only — no driver run, no server/device contact, no gradle.

## E2E part 1 — `e2e1_happyPathCycle`, `e2e3_alreadyGoneSecondDelete` (AUTHORED, NEVER EXECUTED)
- File: `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`, commit `56df4ff4`. Nothing else touched; no push; no status change.
- Gate: `./gradlew compileDebugAndroidTestSources` BUILD SUCCESSFUL (the only proof these methods exist and typecheck). `./gradlew testDebugUnitTest` = 150 tests / 0 failures / 0 errors / 1 skipped, unchanged.
- R-EV honored: zero `assumeTrue` in either new method. `requireArg()` fails naming the missing `-e` arg; absent server item, absent local copy of the sacrificial id, or absence of a second local track each `fail()`s with the gap named. The only remaining `assumeTrue` in the class is `e2e4`'s pre-existing one (not mine to change).
- e2e1 leg order is as mandated: lone-entry FIRST — asserts deleted id out of the queue, not the current song, `!isPlaying()` (R3-B1/R4, the only executed backstop), then 404 oracle. Then from-a-playlist — `_success` toast, queue no longer contains it, `getCurrentSong().id == nextTrack.id`, 404 oracle. R9 leg observes `FavoritesStore` by registering a `Listener` (held strongly) and re-arming the flag immediately before the confirm click, then asserting delivery + `getVersion()` advance; both fail loudly, nothing dropped.
- e2e3: act 1 real delete (`_success` + 404), 5 s settle, then the SAME held `Song` object is re-queued beside a live track and deleted again. Asserts `_gone` toast shown, `_success` toast ABSENT for that act, dead id stays out of the queue and off the current-song surface, id still 404. R10/R11 deliberately not asserted (success-only by design).
- Toast distinguishability (`isToastRoot()`): matcher selects the TYPE_TOAST window; `withText()` selects one exact string. `_success` = "Removed from library", `_gone` = "This song is no longer on the server" are distinct resources/text, so a wrong toast makes the positive assertion time out RED *and* the negative assertion fail RED. It cannot pass on the other string.
- Still needed at run time / residual risk: (1) never executed — no emulator or device was started; (2) on API 30+ toast windows are owned by system_server and Espresso may see no toast root — that yields a loud RED on the positive assertion (never a false green), and the fix then is a log/state-based leg, not `assumeTrue`; (3) the now-playing check is taken at the model layer (`getCurrentSong()`), because the same title renders in both the panel and the queue list, so a `withText(title)` check would be ambiguous — the surface-level title render is therefore NOT pinned by this test; (4) both methods are destructive and must run only inside the driver's P0→P8 cycle (P5 restore is the operator's).

## E2E part 2 — e2e2 / e2e5 / e2e6 (authored 2026-11-12; NONE of these has executed)
- Gate for all three: `./gradlew compileDebugAndroidTestSources` BUILD SUCCESSFUL; `./gradlew testDebugUnitTest` = 150 / 0 failures / 0 errors / 1 skipped. Three commits (e2e2, e2e5, e2e6), explicit paths, no push. No `tools/` or part-1 method/helper was modified.
- **e2e2_playlistCleanAfterScan (R4/R9/L4)** proves: dead id out of the queue and off the current-song surface, 404 on the server, and — the B6 half — that the id WAS listed before. Positive-first: it enumerates `GET /Users/{user}/Playlists`, fails if no playlist lists the sacrificial id (that would make "no longer lists it" vacuous), and every membership read is `GET /Playlists/{id}/Items?UserId=` with a non-200 promoted to `fail()` (`/Items/{id}/Items` is never used — 404 would fake cleanliness). Scan: without `-e qa_scan delegated` it POSTs `/Library/Refresh` itself and fails naming the operator step on non-2xx; with `delegated`, the driver's p3b_scan owns POST+poll+re-assert and this method polls ≤180 s for cleanliness, failing if the dead id still resolves. Re-reads carry a cache-buster + `Cache-Control: no-cache` so a stale listing cannot pass as the fresh one (L4).
- **e2e5_noSessionReportsForDeadId (R10)** proves: the guard is ARMED by the test (`favorites_guard`/`suppress_report_item_id` = dead id, asserted TRUE first), then gone after the delete — checked both in-process (`MusicService.isServerReportSuppressed`) and on disk. Asserts the queue advanced to `nextTrack` (so a report would name the new item). Leg 1 (request log) is the feature's only sanctioned skip: no `-e qa_reqlog` ⇒ prints exactly `SKIPPED (request-log oracle unavailable, L2)`; with the arg, non-200 or a capture containing no `Sessions/Playing` traffic at all is a `fail()` (an empty capture proves nothing), and any `Sessions/Playing/Progress` line naming the dead id in the 30 s window fails. Playback is restarted on the advanced item so that positive control is reachable.
- **e2e6_cachedSongPurged (R11)** proves the device-level cycle: caches via the app's own `NavigationUtil.startDownload(activity, …)`, asserts cache row AND at least one on-disk path exist (row-without-bytes would make the purge check vacuous), deletes through the player overflow, then asserts `isCached` false and all three paths (`<root>/<id>`, `<root>/download/<id>`, `MusicUtil.getFileUri(song)`) gone. Stale fixture files are reclaimed *before* the run and the row/bytes are cleaned in `finally`; a cache action that cannot cache the item is a `fail()`, never a skip.
- Still needed at run time / residual risk: (1) **none of the three has ever run** — no emulator, no device, no server, no destructive cycle; (2) e2e2 needs a nominated track that actually lives in a server playlist, and either admin rights on the QA token or `-e qa_scan delegated`; (3) e2e2 in delegated mode does not itself prove *who* ran the scan — that half stays with the driver's p3b_scan; (4) e2e5 performs **no process restart** (a force-stop needs the driver); the on-disk pref read is the restart-equivalent, and that bound is stated in the method's javadoc and its failure message; (5) e2e5's "capture has no Sessions/Playing traffic ⇒ fail" rule can produce a false RED on a quiet capture — intentional asymmetry, per B6; (6) e2e6's download leg depends on device network/throughput (60 s row / 180 s bytes budgets) and on `getFileUri` naming, which is metadata-derived and shared across runs — the reclaim-first step is what keeps that from poisoning later runs.
