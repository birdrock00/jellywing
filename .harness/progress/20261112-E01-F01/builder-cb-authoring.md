# Builder — C-B/B1 authoring (E01-F01)

Status: IN PROGRESS — authoring only; destructive cycle NOT run (no session, needs operator).

## Plan / acceptance list
- [ ] Read review-round4.md §4 (incl. overriding rules) and F01 tests P0-P8 (:268-300)
- [ ] Study existing e2e4_cancelNoop pattern + instrument-arg plumbing
- [ ] Author tools/qa-delete-cycle.sh (P0→P8 verbatim; fail-loud; no secrets embedded)
- [ ] Author 5 E2E methods: e2e1_happyPathCycle, e2e2_playlistCleanAfterScan,
      e2e3_alreadyGoneSecondDelete, e2e5_noSessionReportsForDeadId, e2e6_cachedSongPurged
- [ ] Gate: ./gradlew compileDebugAndroidTestSources passes
- [ ] Gate: ./gradlew testDebugUnitTest = 150 / 0 failures / 1 skip
- [ ] Commit each coherent unit with explicit paths
- [ ] Notes: per-method proof + runtime needs; state plainly that NONE has executed

## Notes (append as I go)

### Research findings (pre-authoring)
- e2e4 pattern confirmed: args qa_server/qa_token/qa_user/qa_item; helpers arg(), serverItemStatus(),
  findSong(), bind(), dismissBatteryDialogIfPresent(), openPlayerOverflowAndTapDelete() in
  DeleteFromLibraryE2eTest.java (207 lines). e2e4's own assumeTrue left untouched (authored scope = 5 new).
- Strings: delete_from_library_{title,body,confirm,cancel,success,denied,gone,failed} exist (strings.xml:54-62).
  gone = "This song is no longer on the server" (distinct from success by resource id).
- R10 guard: MusicService.GUARD_PREFS="favorites_guard", GUARD_KEY="suppress_report_item_id";
  isServerReportSuppressed() lazily restores from disk (perfect for the after-restart leg); armed by
  MusicUtil.java:253. e2e5 arms the guard directly (same pref) so the clear is non-vacuous.
- Cache action (e2e6): NavigationUtil.startDownload(activity, songs) -> DownloadService ACTION_START;
  cached file path = MusicUtil.getFileUri(song); row = cacheDao().isCached(id); temp at <cacheRoot>/download/<id>.
- LibraryDeleteUtil.handleOutcome: SUCCESS and ALREADY_GONE share removeFromQueueAndAdvance; lone entry
  -> clearQueue (clean stop). SUCCESS-only: toast(success), clearSuppressionIfOurs, purgeCacheEntry.
- espresso-contrib 3.7.0 present -> ToastMatcher available for toast assertions.
- Gate plan: compileDebugAndroidTestSources + testDebugUnitTest (must stay 150/0/1).
