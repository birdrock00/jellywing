# Delete the playing song from the Jellyfin library — Test Contract

> The traceability matrix: every `R-id` maps to a concrete, executable test. The Reviewer
> fails the feature if any `R-id` lacks a passing test. E2E rows run under the QA protocol
> below — destructive by design, restored every cycle.
>
> **Coverage as it actually stands (third pass, 2026-11-12; reconciled with the nine round-2
> commits after Reviewer F8).**
> An earlier revision of this matrix named five E2E methods — `e2e1_happyPathCycle`,
> `e2e2_playlistCleanAfterScan`, `e2e3_alreadyGoneSecondDelete`, `e2e5_noSessionReportsForDeadId`,
> `e2e6_cachedSongPurged` — and treated `tools/qa-delete-cycle.sh` as an existing driver.
> **None of them has ever been written.** `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`
> contains exactly one test, `e2e4_cancelNoop`, and the driver file does not exist. Naming a
> method is a coverage claim, not a deferral, so the matrix now carries an **Exists?** column
> and every unwritten case says so in plain words. The "six E2E cases" section at the bottom of
> this file is the QA round's **work order**, not a description of code.
>
> Say it plainly rather than leaving it to inference:
>
> - **R3, R4, R9 and R10 have no executed proof of the behavior their `R-id` specifies.**
>   Nothing on any device or against the server exercises the delete pipeline end to end: the
>   one-DELETE-on-the-wire claim (R3), the queue drop + advance and the lone-entry clean stop
>   (R4), the unprompted Favorites refresh (R9) and the "no session report names the dead id"
>   claim (R10) are unverified. What runs today for those requirements is JVM *source
>   contracts* only — text assertions about `LibraryDeleteUtil.java` — listed row-by-row below
>   with exactly what each does and does not pin.
> - Three safety/content properties that survived mutation in round 1 (**R2** "no request before
>   the positive button", **R2** destructive copy content, **R9** SUCCESS-branch Favorites refresh)
>   **have executed JVM coverage today** — `DeleteFromLibraryDialogSourceTest` (4 tests) and the
>   SUCCESS-block-scoped reading of `testSuccessOutcomeRunsQueueDropAndFavoritesRefresh`. The
>   Reviewer re-ran the round-1 mutants against them and each is now RED. Their round-1 `Exists? NO`
>   rows are retired below; the history stays in *History* at the end of "What actually executes".
>
> - **A green instrumented run is not evidence.** Instrumented and E2E legs `assumeTrue`-skip when
>   there is no live app session or no driver arguments, and both runners report that as success:
>   `am instrument` printed `OK (2 tests)` and logcat printed `run finished: 2 tests, 0 failed,
>   0 ignored` whether or not a leg executed. **Three** of the six device legs in this matrix skipped
>   on the 2026-11-12 run — `DeleteMenuItemVisibilityTest::deleteItemShownWhenServerBackedSongIsCurrent`
>   and `DeleteConfirmDialogTest` (both `assumption failed: Need an existing real Jellyfin session`) and
>   `e2e4_cancelNoop` (needs driver args, and has **never executed** in the feature's history). An
>   earlier revision of this header said **four**, which contradicted the G4 ledger below ("three
>   executed and three did not"); the Reviewer's round-2 logcat and the ledger both say three, so the
>   header now agrees with them (round-3 §3 row 9).
>   See **G4** and the ledger in that section. **An ignored test is a missing test, and this
>   harness's runners do not distinguish the two.**

| R-id | Behavior | Test (file::name) | Type | Exists? | Status |
|---|---|---|---|---|---|
| R1 | Overflow hides **Delete from library** when no song is current | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java::deleteItemHiddenWhenNoSongIsCurrent` — the only device leg of R1 that executed on 2026-11-12 (per-test `started:`/`finished:` in logcat) | instrumented (**not** in the unit gate — G1) | yes (leg executed) | ⬜ |
| R1 | Overflow **shows** the item when a server-backed song is current | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java::deleteItemShownWhenServerBackedSongIsCurrent` — **exists, self-skips without a live Jellyfin session (`assumption failed: Need an existing real Jellyfin session`) — NOT RUN**; the runner still printed `OK (2 tests)` (Reviewer F6, G4) | instrumented (**not** in the unit gate — G1) | yes, **self-skips — NOT RUN** | ⬜ |
| R1 | Menu entry statics: id present, `showAsAction="never"`, title from a string resource | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testMenuItemPresent` | unit (source contract) | yes | ⬜ |
| R2 | Confirm dialog precedes any network; Cancel is a no-op (device) | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteConfirmDialogTest.java::selectDeleteOpensDestructiveConfirmDialogAndCancelIsNoop` — **exists, self-skips without a live Jellyfin session (`assumption failed: Need an existing real Jellyfin session`) — NOT RUN** on 2026-11-12, while `am instrument` printed OK (Reviewer F6, G4) | instrumented (non-destructive; **not** in the unit gate — G1) | yes, **self-skips — NOT RUN** | ⬜ |
| R2 | Cancel leaves the item on the server and issues no DELETE (E2E) | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java::e2e4_cancelNoop` — **exists but has NEVER executed**: it `assumeTrue`s on driver args (`E2E-4 needs driver args (qa_server/qa_token/qa_user/qa_item)`) supplied by `tools/qa-delete-cycle.sh`, which does not exist (G2). Non-execution reads as `0 ignored` (G4) | e2e (non-destructive; **not** in the unit gate — G1) | yes, **self-skips — NOT RUN** | ⬜ |
| R1 | Gate reads the current song and requires a **non-blank server id** (not merely a non-null song), and drives `setVisible` | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testVisibilityGateRequiresAServerBackedSongId` — executed JVM source contract on the helper body; kills all three round-1-surviving shapes (always-show, non-null-only, blank-id accepted). **R2-B1 CLOSED (round 3, Reviewer-ran):** `deleteItem.setVisible(serverBacked \|\| true)` is RED at `:166` (the new exact-expression assertion), and so are the local-variable shape (`boolean alwaysShow = serverBacked \|\| true; setVisible(alwaysShow);`) and `serverBacked = song != null \|\| true` (kills `song.id != null` at `:156`). **Remaining bound, narrower than round 2's:** `boolean serverBacked = song != null \|\| (song != null && song.id != null && !song.id.trim().isEmpty());` keeps every guarded token and stays **GREEN** (`m_r2b1d`) — the disjunct folded into the initializer, so the flag is always true whenever a song exists while `setVisible(serverBacked)` is untouched. That is the intrinsic limit of a textual/source-contract gate, not a fixable oversight here; **the real proof of R1's shown-when-server-backed property is the device leg (G4, C-B B2), which still self-skips without a live session** | unit (source contract) | yes | ⬜ |
| R1 | Both player themes apply the gate on menu inflate **and** on current-song change | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testBothPlayerThemesApplyTheGateOnInflateAndSongChange` — executed; dropping the call from either `CardPlayerFragment` or `FlatPlayerFragment` goes RED | unit (source contract) | yes | ⬜ |
| R2 | Handler opens the dialog and never calls the API directly | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testHandlerOpensDialogNotApiDirectly` — reads only `app/src/main/java/club/thatpetbff/gramophone/fragments/player/AbsPlayerFragment.java` | unit (source contract) | yes | ⬜ |
| R2/R3 | Opening the dialog never fires the delete — the request sits **only** inside the positive-button callback | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryDialogSourceTest.java::testConstructingTheDialogIssuesNoRequest` + `#testDeleteCallSitsInsideOnPositive` — executed source contract on `app/src/main/java/club/thatpetbff/gramophone/dialogs/DeleteFromLibraryDialog.java`, split into construction vs. `onPositive` vs. everything-outside regions. Round-1 B2 is RED today in **both** shapes: moving the call into `onCreateDialog`, and leaving it in `onPositive` *and* adding it to `onCreateDialog` | unit (source contract) | yes | ⬜ |
| R2 | Destructive copy warns in plain words (leaves the server, permanent, every playlist holding it, cannot be undone) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryDialogSourceTest.java::testConfirmCopyCarriesTheThreeWarnings` — executed **content** assertion over `delete_from_library_body`; Reviewer B3's mutant (`delete_from_library_body` → "Remove this song?") is RED here, not merely on key existence | unit (source contract) | yes | ⬜ |
| R2 | Confirm and Cancel are distinct explicit verbs (delete / cancel), not a generic OK | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryDialogSourceTest.java::testConfirmAndCancelButtonsAreDistinctVerbs` — executed | unit (source contract) | yes | ⬜ |
| R3 | Exactly one `DeleteItem` call site, through the existing apiclient, no hand-rolled HTTP | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testUtilIssuesSingleStockDeleteCall` | unit (source contract) | yes | ⬜ |
| R3 | One `DELETE /Items/{id}` actually reaches the server per confirm, and the item 404s afterwards | **NOT YET WRITTEN — the QA round must author `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`**, wrapped by the QA driver (also NOT YET WRITTEN; see G2). R3 has no device or server-side test today | e2e | **NO** | ⬜ |
| R4 | Success toast; queue drops the deleted entry and advances to the next track | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` — **SUCCESS-block-scoped since `9fb1f19b`**: it reads the `case SUCCESS:` … `break;` region and asserts the success toast, `removeFromQueueAndAdvance(` and `FavoritesStore.notifyChanged()` inside it. Reviewer B4 is RED, and the kill is independent of R6: removing `notifyChanged()` from `ALREADY_GONE` only reddens `testAlreadyGoneRunsCleanupWithoutSuccessClaim` | unit (source contract) | yes | ⬜ |
| R4 | Nothing success-shaped happens **before** the request is issued (no early toast, no early queue edit, no early purge, no early Favorites refresh) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testInFlightGuardWrapsRequestAndReleasesOnOutcome` pins the region before `DeleteItem(` against `handleOutcome(` and `DeleteOutcome.SUCCESS` — which is what kills the optimistic-success mutant (the Reviewer re-ran it with only this test in the run: RED on its own). **CLEARED (round 3, Reviewer-ran):** the region widened at `17d91a00` now forbids all five success-side effects (`:376-388`), and the Reviewer inserted each one immediately before `App.getApiClient().DeleteItem(` — `toast(delete_from_library_success)`, `removeFromQueueAndAdvance(itemId)`, `purgeCacheEntry(itemId)`, `FavoritesStore.notifyChanged()`, `clearSuppressionIfOurs(itemId)` — and all five are **RED** at `testInFlightGuardWrapsRequestAndReleasesOnOutcome:382`, each run scoped to that class. **Bound (Minor, reasoned, not demonstrated):** the region is **lexical** — the code-only text between `deleteFromLibrary` and `DeleteItem(` — so a premature success effect that lives inside a *helper the pre-request path calls* sits outside the region and survives. Same class of limit as R3-B1; every shape round 2 actually named is dead | unit (source contract) | yes | ⬜ |
| R4 | The cleanup helper the SUCCESS branch calls: drop **every** entry naming the deleted item, know whether the removed one was current, start the track now at that index, and stop cleanly when the queue empties | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries` — executed source contract on the `removeFromQueueAndAdvance` body; RED when only the first entry is removed and RED when the lone-entry `clearQueue()` branch is removed. **Its round-2 form did not pin the removal ORDER (Reviewer F1)** — see the next row | unit (source contract) | yes | ⬜ |
| R4 | A song queued twice yields **every** one of its queue entries; a single/absent entry is unchanged | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteDuplicateQueueTest.java::testEveryEntryOfATwiceQueuedSongIsFound` + `#testSingleAndAbsentEntriesAreUnchanged` — executed JVM **behavior** tests on `LibraryDeleteUtil.positionsOfItem` (the lookup). **The shipped code is `found.add(i)` at `app/src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java:241`** — an ascending scan that appends. `found.add(0, i)` is a **mutant**, not the shipped form, and the Reviewer's round-3 run confirmed it RED on **both** duplicate legs. An earlier revision of this row cited `found.add(0, i)` as though it were shipped code; citing the mutant as the implementation is the exact defect class (a matrix line that reads as coverage for something the code does not do) this row exists to prevent, so it is corrected here (round-3 §6 C-C2, B1 class). What the row pins is *completeness of the lookup* (every entry naming the item is found), not order — order is the next row | unit | yes | ⬜ |
| R4 | Those entries are removed **highest-index-first**, which is the only order that keeps the later indices valid | Round 2 proved nothing here: mutating the loop to ascending left the gate GREEN (`MusicPlayerRemote.removeFromQueue`'s `0 <= p < size()` guard silently refuses the out-of-range index and a dead entry survives — Reviewer F1), because the round-2 RED was the *lookup*, not the order. **C-A.1 landed at `e88b33fa`** — `LibraryDeleteUtil.removalOrder(...)` extracted plus `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteDuplicateQueueTest.java::testRemovalOrderIsHighestIndexFirst` (`[1,3] → [3,1]`, `[0] → [0]`, `[] → []`). **CLEARED (round 3, Reviewer-ran):** the inline ascending loop over `positions` is RED at `DeleteFromLibraryWiringSourceTest::testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries:291` (the `assertFalse(body.contains("removeFromQueue(positions.get("))` clause), and an ascending helper is RED at `LibraryDeleteDuplicateQueueTest::testRemovalOrderIsHighestIndexFirst:56` (`expected:<[3, 1]> but was:<[1, 3]>`). **Bound R3-B1 (open, Minor, test-side):** the helper's *order* is executed-pinned, but the **call site's consumption** of `removalOrder()` is only textual-pinned — a call site that still calls `removalOrder(positions)` and then binds `List<Integer> order = new ArrayList<>(positions)`, or one that walks `order` backwards (`for (int i = order.size() - 1; i >= 0; i--)`), keeps every guarded token, reproduces the ascending-removal defect, and stays **GREEN** (round-3 §1, mutants `m_f1d`/`m_f1c`). A behavioural pin needs the device — C-B B5's lone-entry leg. HEAD is correct; no shipped code is wrong | unit | yes | ⬜ |
| R4 | Lone-entry queue: after delete, playback stops cleanly and no surface presents the dead item | **NOT YET WRITTEN — the QA round must author the lone-entry leg of `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (Reviewer: the code reads correct; no test of any kind covers it) | e2e | **NO** | ⬜ |
| R4 | After a delete and a library scan, every playlist that held the item no longer lists it | **NOT YET WRITTEN — the QA round must author `e2e2_playlistCleanAfterScan` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the stale-`.m3u8` leg of limitation L4) | e2e | **NO** | ⬜ |
| R5 | 401 → permission-refusal toast naming the fix; nothing claimed or changed | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testStatus401MapsToPermissionDenied` — **forced status; see limitation L1** | unit | yes | ⬜ |
| R6 | 404 → distinct already-gone toast + client cleanup, no deletion claim | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testStatus404MapsToAlreadyGone` + `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testAlreadyGoneRunsCleanupWithoutSuccessClaim` (branch-scoped) | unit | yes | ⬜ |
| R6 | A second delete of the same id on device really toasts *already gone* and keeps the item out of the queue | **NOT YET WRITTEN — the QA round must author `e2e3_alreadyGoneSecondDelete` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the E2E half of the round-1 T6a task was never written; only its unit leg ran) | e2e | **NO** | ⬜ |
| R7 | Transport / other-status failure → named failure, nothing claimed | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testTransportErrorMapsToNetworkFailure`, `#testOtherStatusMapsToServerFailure`, `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testFailureOutcomesChangeNothingButToast` | unit | yes | ⬜ |
| R8 | In-flight delete absorbs repeat activations; one request only; no outcome dispatched before the request | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testSecondRequestIgnoredWhileInFlight` + `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testInFlightGuardWrapsRequestAndReleasesOnOutcome` (latch arms before `DeleteItem(`, every terminal outcome releases it, and no outcome/`SUCCESS` may appear in the pre-request region) | unit | yes | ⬜ |
| R8 | A **synchronous** throw from the API call is contained and leaves the latch free (otherwise that song can never be deleted again for the life of the process) | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteSyncThrowLatchTest.java::testSynchronousThrowReleasesTheLatchAndNeverEscapes` + `#testADeleteAfterAContainedThrowIsNotIgnored` — executed JVM behavior tests (nothing binds an `ApiClient` on the JVM, which reproduces the shape exactly). Reviewer M-1 is RED in both discriminating shapes: drop the `try/catch`, and keep the `catch` but delete `endDelete(itemId)` from it | unit | yes | ⬜ |
| R9 | Success path refreshes Favorites without user action | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` — **SUCCESS-block-scoped since `9fb1f19b`**; "SUCCESS must refresh Favorites without user action (R9)" reads the `SUCCESS` block only, so removing `FavoritesStore.notifyChanged()` from that branch alone is RED (round-1 B4 / M12). The neighbouring `ALREADY_GONE` branch has its own separate RED in `testAlreadyGoneRunsCleanupWithoutSuccessClaim`, which is what makes the two kills independent | unit (source contract) | yes | ⬜ |
| R9 | The Favorites tab visibly refreshes on device after a real delete | **NOT YET WRITTEN — the QA round must author the Favorites-tab leg of `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** | e2e | **NO** | ⬜ |
| R10 | Success clears a suppression guard that names the deleted id, and the advance is sequenced before the clear | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessClearsStaleSuppressionGuardOnlyForTheDeletedId` (SUCCESS-block-scoped; the inverted sequencing does go RED) | unit (source contract) | yes | ⬜ |
| R10 | No later `Sessions/Playing/Progress` report names the deleted id, and the restart shows the guard pref cleared | **NOT YET WRITTEN — the QA round must author `e2e5_noSessionReportsForDeadId` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`**, which needs the QA driver (G2) for its request-log leg. R10 has no device or server-side test today | e2e | **NO** | ⬜ |
| R11 | Success purges the Room `cache` row **and the three cached file locations** (`<cacheRoot>/<id>`, `<cacheRoot>/download/<id>`, `MusicUtil.getFileUri(song)`); un-cached purge is a no-op | `app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java::purgeRemovesCacheRowAndCachedFiles` + `#purgeOfUncachedItemIsANoOp` — **executed** on the emulator 2026-11-12 via `install -r` + `am instrument` (per-test logcat lines, no assumption skip), and each of the three file locations is RED under its own device mutant. **But this class is the ONLY pin for the `File.delete()` calls: delete all three lines and `./gradlew testDebugUnitTest` stays GREEN (Reviewer F2). R11's file removal therefore contributes nothing to the gate and is a C-B must-run leg** | instrumented (**not** in the unit gate — G1) | yes (device-only pin) | ⬜ |
| R11 | The purge is keyed on the deleted id, does not scan the songs table on the UI thread, and logs a swallowed failure | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testCachePurgeIsNarrowAndLogsItsCatch` — executed source contract on the `purgeCacheEntry` body (comments stripped, so prose cannot satisfy it); RED on a `getSongs()` scan and RED on a silent `catch (Exception ignored)` | unit (source contract) | yes | ⬜ |
| R11 | A **missing `songs` row must not skip** the Room `cache` row (the delete is unconditional) | Round 2 proved nothing here: gating `cacheDao().deleteFromCache(itemId)` on the `song != null` guard was GREEN in the JVM gate **and** in the device class — no fixture inserted a cache row without a songs row (Reviewer F3). **C-A.3 landed at `7a3743c8`** as `app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java::purgeRemovesCacheRowWhenTheSongsRowIsAbsent` (insert a `cache` row, delete the matching `songs` row, purge, assert `isCached` false). **CLEARED (round 3, executed on the emulator):** the leg runs — `am instrument` `OK (3 tests)` with per-test `started:`/`finished:` logcat and **no** `assumption failed` line — and `if (song == null) { return; }` inserted before `cacheDao().deleteFromCache(itemId)` in `purgeCacheEntry` is **RED on device** (`Tests run: 3, Failures: 1` at `CachePurgeOnDeleteInstrumentedTest.java:109`, the leg's own R11 assertion). **Description corrected (Builder-disclosed deviation, which the Reviewer verified rather than accepted):** the leg **never creates** a `songs` row, so its state is "cache row present, songs row never existed" rather than "songs row deleted" — deliberately, because `SongDao.deleteSongs()` is a whole-table `DELETE FROM songs` that would wipe a development emulator's real library. For `purgeCacheEntry` the observable state is identical, the leg asserts its own precondition (`:104-105`, `assertNull(getSong(id))`) so it cannot silently degrade into the cached case, and it leaves no row behind (`finally`). It stays on C-B's must-run list for the shipping run | instrumented (**not** in the unit gate — G1) | yes (device-executed 2026-11-12) | ⬜ |
| R11 | The device class is self-restoring (a broken run must fail on an R11 assertion, not on a stale fixture) | **NOT PROVEN today**: the audio fixture path comes from `getFileUri(song)` (artist/album/disc/track/title/container, not the unique id), so it is the same path every run; a run whose purge is broken leaves the file and every later run fails at `assertTrue("fixture file must be creatable")` — the Reviewer hit exactly this. **CLEARED (round 3, executed twice on the emulator, and the mechanism was proven load-bearing in both directions):** with the audio-file purge arm deliberately removed, run 1 **and** run 2 (same build, no other change) fail on `downloaded audio file must be gone (R11)` (`:76`) — **not** on `fixture file must be creatable`. With committed code, a planted stale fixture at the non-run-unique path was reclaimed (`OK (3 tests)`, planted file gone), and deleting the two `reclaimStaleFixture(…)` call sites reproduces the round-2 failure verbatim (`fixture file must be creatable`). Precision: for the break-the-purge scenario the load-bearing half is `finally { restoreFixtureState(…) }` (deletes the audio file even when assertions fail); the reclaim is the half that survives a process dying before `finally`. **Note: "break the purge and run the class twice" was a review-time mutation probe and has been performed — it is not a shipping step (see the QA must-run set at the end of this file)** | instrumented (**not** in the unit gate — G1) | yes (device-executed 2026-11-12) | ⬜ |
| R11 | Purge runs on the success outcome only (not on 401/404/transport failure) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testCachePurgeIsSuccessOutcomeOnly` | unit (source contract) | yes | ⬜ |
| R11 | Cached state is gone in-app and on disk after a real delete cycle on the sacrificial track | **NOT YET WRITTEN — the QA round must author `e2e6_cachedSongPurged` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the E2E half of the round-1 T10a task was never written; only the instrumented leg ran) | e2e | **NO** | ⬜ |
| R12 | New strings exist in default English only; locales untouched — for **any** `delete_from_library*` key, including ones nobody remembered | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testNewStringsLiveInDefaultEnglishOnly` — key names are now **derived by pattern** (`<string name="…">` matched against `delete_from_library`) since `f68d6eea`, with a non-vacuity floor (`localeFilesChecked > 0`; 33 locale files today). Reviewer M-4's mutant (untracked `delete_from_library_undo` added to `values-de`) is RED; the `delete_playlist_x` control stays GREEN, which is correct — substring, not prefix, on purpose. **Split ruling (round 3).** R2-B1's mutant is RED (see the R1 gate row above). **F5 CLOSED at `738820b1` — Builder-run evidence; the round-4 Reviewer re-run is still owed before this row is quoted as reviewer-verified.** What `10e1e297` did close: drift by duplicating the literal verbatim (`stringKeysMatching(locale, "delete_from_library")` at the locale call site) is RED via the one-literal pin (`m_f5d`, `expected:<1> but was:<2>` at `testBothStringKeyCallSitesShareTheOneFragment:188`), and the shared constant itself going vacuous (`KEY_FRAGMENT = "delete_from_library_ZZZ"`) is RED **twice** (`m_f5b`, via `testDeleteStringKeyListIsDerivedNotEmpty:51` and the new anti-vacuity floor at `:71`). What **was** still GREEN, isolated and in the full 150-test suite: a locale scan aimed at a fragment that matches nothing — `stringKeysMatching(locale, "delete_from_library_ZZZ")`, `stringKeysMatching(locale, KEY_FRAGMENT + "_ZZZ")`, or the same defect re-wrapped across lines by concatenation — because the one-literal pin counts lines containing the quoted `"delete_from_library"`, so a transformed or different fragment is invisible to it, and the anti-vacuity floor asserts on `deleteFromLibraryKeys()` (the **default-resources** derivation), a list the locale loop does not use. **How `738820b1` closed it:** the locale loop now calls a named scan `translatedDeleteKeys(String localeXml)` that takes **no** fragment parameter (the fragment can come only from `KEY_FRAGMENT`), plus a **positive control** planting one real derived key in a synthetic locale XML and asserting that scan reports it. All four shapes are RED — the two `"…_ZZZ"` spellings and the re-spelled concatenation each at `expected:<1> but was:<0>` (the concatenation killed by the control alone), the verbatim re-wrap twice (`1 ≠ 2` **and** the control); focused class 16 / 0, full suite 150 / 0 failures / 1 skip. **Trap:** a `source.contains("stringKeysMatching(locale, KEY_FRAGMENT)")` textual pin is self-satisfying — the assertion's own literal satisfies its `contains` — and the Reviewer verified it leaves both GREEN variants alive. Do not implement C-C1 that way | unit (source contract) | yes (R2-B1 cleared; F5 CLOSED at `738820b1` — **Reviewer re-ran all four mutants in round 4: each RED (the concatenation by the positive control alone, the verbatim re-wrap twice), focused class 16/0, full suite 150/0/1, `app/` clean**) | ⬜ |
| R12 | Both `stringKeysMatching` call sites share the one `KEY_FRAGMENT` constant (a second verbatim literal may not be introduced) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testBothStringKeyCallSitesShareTheOneFragment` — **executed JVM source contract, and until now it was unmapped coverage** (0 occurrences in `.tests.md` and `.tasks.md`, the same failure shape round 2 filed as F8; round-3 §3 row 11). It counts lines containing the quoted `"delete_from_library"` literal and asserts exactly one, so re-splitting the locale call site to an identical literal is **RED** (`m_f5d`, `expected:<1> but was:<2>` at `:188`). **Bound:** it pins literal *count*, not the *value* the locale loop scans for, so it never reached the F5 hole above — C-C1 closed that hole with the named scan plus positive control, deliberately **not** with a `source.contains(...)` pin | unit (source contract) | yes | ⬜ |
| R12 | The derived key list is non-empty and holds all nine frozen keys (the pattern may not match nothing) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testDeleteStringKeyListIsDerivedNotEmpty` — executed; mutating the shared matcher so it matches nothing is RED ("an empty match makes the English-only rule vacuous") | unit (source contract) | yes | ⬜ |

## What actually executes, and where the gaps are

- **G1 — the automated gate is the JVM unit gate only.** `verification.test_command` is
  `./gradlew testDebugUnitTest`, which runs `app/src/test/` and nothing else. Every
  `instrumented` row above (`DeleteMenuItemVisibilityTest`, `DeleteConfirmDialogTest`,
  `DeleteFromLibraryE2eTest`, `CachePurgeOnDeleteInstrumentedTest`) requires an explicit
  `am instrument` / `connectedAndroidTest` run and contributes nothing to a green gate. A
  requirement whose only rows are instrumented is therefore *unpinned* in the gate. Round 1's
  instance of that (M-5: forcing R1's helper to always show the item) is now closed by the executed
  JVM contracts `testVisibilityGateRequiresAServerBackedSongId` and
  `testBothPlayerThemesApplyTheGateOnInflateAndSongChange`; what still survives is the textual
  bound on R1 is now much narrower: `serverBacked = song != null || (song != null && song.id != null
  && !song.id.trim().isEmpty())` keeps every required token and stays GREEN (round-3 `m_r2b1d`), an
  intrinsic limit of a source-contract gate whose real proof is R1's still-unrun device leg (G4). The
  always-visible `setVisible(serverBacked || true)` shape and its local-variable form are **RED** at
  `:166`. R11's file removals remain unpinned per **G5** below.
- **G2 — the QA driver does not exist.** `tools/qa-delete-cycle.sh` is declared `create:` in
  `.plan.md` and has never been written, so the P0→P8 backup/restore protocol below cannot be
  run today by anyone. It is QA-round work, and until it exists every destructive E2E case is
  unrunnable regardless of whether its Java leg gets written.
- **G3 — R3, R4, R9, R10 have no executed proof of their specified behavior.** Restated here
  because the matrix shape hides it: for each of those four, *every* row that would establish
  the end-to-end claim is `Exists? NO`, and the rows that exist are source-text contracts whose
  weakness is named in the row. Their verification path runs through `e2e1`/`e2e2`/`e2e5` plus
  the driver — real work still to be authored under P0→P8.
- **G4 — instrumented and E2E legs self-skip, and both runners call it OK (Reviewer F6). Read this
  before believing any device row above.** These legs open with `assumeTrue`/`Assume` guards (live
  app session; driver args), and a skipped leg is *silently* reported as success:
  `am instrument` printed `OK (2 tests)` and logcat printed `run finished: 2 tests, 0 failed,
  0 ignored` on 2026-11-12 whether a leg ran or bailed.

  **Rule for this feature and every later one: an ignored test is a missing test, and this
  harness's runners do not distinguish the two.** A green instrumented (or E2E, or driver) run is
  evidence only if it shows **per-test `started:`/`finished:` lines** for the leg being claimed and
  **no `assumption failed` line** anywhere in that leg's output; otherwise the leg is recorded
  `NOT RUN`, never as a pass. C-B in the round-2 review makes this an explicit evidence requirement.

  Device-ledger as actually observed 2026-11-12 (Reviewer's `install -r` + `am instrument -w`
  campaign; AGP `connectedAndroidTest` forbidden because it uninstalls the app and wipes the
  session):

  | Device leg | Executed? |
  |---|---|
  | `CachePurgeOnDeleteInstrumentedTest::purgeRemovesCacheRowAndCachedFiles` | **ran** (per-test logcat, no skip) |
  | `CachePurgeOnDeleteInstrumentedTest::purgeOfUncachedItemIsANoOp` | **ran** |
  | `DeleteMenuItemVisibilityTest::deleteItemHiddenWhenNoSongIsCurrent` | **ran** |
  | `DeleteMenuItemVisibilityTest::deleteItemShownWhenServerBackedSongIsCurrent` | **NOT RUN** — `assumption failed: Need an existing real Jellyfin session` |
  | `DeleteConfirmDialogTest::selectDeleteOpensDestructiveConfirmDialogAndCancelIsNoop` | **NOT RUN** — same assumption failure |
  | `DeleteFromLibraryE2eTest::e2e4_cancelNoop` | **NOT RUN, and has never run in the feature's history** — `E2E-4 needs driver args (qa_server/qa_token/qa_user/qa_item)`, which come from `tools/qa-delete-cycle.sh` (G2). The one E2E method that exists has zero executed runs |

  So of the six device legs this matrix names, **three executed and three did not**, and the raw
  runner output for those two classes was `OK` in both cases. (The leg added after that run,
  `purgeRemovesCacheRowWhenTheSongsRowIsAbsent`, **was executed on 2026-11-12** in the round-3
  campaign — `am instrument`, per-test `started:`/`finished:`, `run finished: 3 tests, 0 failed,
  0 ignored`, no `assumption failed` line — together with the other two `CachePurgeOnDeleteInstrumentedTest`
  legs, and the C-A.4 self-restoring behaviour was demonstrated by running that class twice against a
  deliberately broken purge. So `CachePurgeOnDeleteInstrumentedTest` now stands at **three legs
  executed, zero skipped**, while the table above keeps its 2026-11-11/12-as-observed entries for the
  other classes. All three `CachePurgeOnDeleteInstrumentedTest` legs stay on C-B's must-run list for
  the shipping run; what is dropped here is only the "never executed" claim, which is no longer true.
  The three rows above for the other two classes are unchanged — they still record the earlier
  `am instrument` run, in which one leg ran and two self-skipped.)
  R1's *shown-when-server-backed*
  property therefore still has no executed device proof; its only executed pins are the JVM source
  contracts, which carry the narrowed R2-B1 survivor (`serverBacked = song != null || (…)`).

- **G5 — some device-only pins are invisible to the gate in a second way (Reviewer F2).** Beyond
  G1's "the gate does not run instrumented classes", R11's three `File.delete()` calls can be
  deleted outright and `./gradlew testDebugUnitTest` stays GREEN: the JVM contract for the purge
  (`testCachePurgeIsNarrowAndLogsItsCatch`, `testCachePurgeIsSuccessOutcomeOnly`) pins cost,
  success-outcome scoping and logging — **not file removal**. Only `CachePurgeOnDeleteInstrumentedTest`
  pins them, on device, so that class is on C-B's must-run list and R11 cannot ship on the strength
  of the unit gate.

- **SUPERSEDED — the round-2 IN-PROGRESS block (kept for the audit trail, not as a status).
  Round 3 (`.harness/progress/20261112-E01-F01/review-round3.md`) ran the named mutants rather than
  reading the Builder's claim, and ruled: C-A.1, C-A.2, C-A.3, C-A.4 and C-A.5's R2-B1 half are
  CLEARED with the named mutant shown RED (C-A.3 and C-A.4 executed on the emulator, per-test
  `started:`/`finished:`, no `assumption failed` line); C-A.5's F5 half is NOT cleared and is carried
  forward as condition C-C1.** What each row now rests on, by mutant:
  **C-A.1** `e88b33fa` — inline ascending loop over `positions` RED at
  `testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries:291`; ascending `removalOrder` RED at
  `testRemovalOrderIsHighestIndexFirst:56`; **open bound R3-B1** (call-site consumption textual:
  `m_f1c`, `m_f1d` GREEN). **C-A.2** `17d91a00` — all five success-side effects inserted before
  `DeleteItem(` RED at `:382` (`m_f7a`-`m_f7e`); bound: the region is lexical. **C-A.3** `7a3743c8` —
  `if (song == null) return;` before `deleteFromCache(itemId)` RED **on device** at
  `CachePurgeOnDeleteInstrumentedTest.java:109`. **C-A.4** `8e1ff1ee` — broken purge fails the same R11
  assertion on run 1 and run 2; removing `reclaimStaleFixture` reproduces the old fixture trap.
  **C-A.5 (R2-B1 half)** `10e1e297` — `setVisible(serverBacked || true)` RED at `:166`, local-variable
  and `song != null || true` forms RED; surviving narrower shape `serverBacked = song != null || (…)`,
  GREEN.
  **Operative rule retained, because it is what produced this result: a condition item counts as
  coverage only when the review shows its named mutant RED** (C-A.1: the ascending removal loop;
  C-A.2: a pre-request success toast and a pre-request `removeFromQueueAndAdvance(itemId)`; C-A.3:
  `if (song == null) return;` before `deleteFromCache(itemId)`, on device; C-A.4: break the purge, run
  the class twice, the second run must fail on an R11 assertion and never on "fixture file must be
  creatable" — a **review-time mutation probe**, not a shipping step, see the QA must-run set; C-A.5:
  a locale fragment that matches nothing, and `setVisible(serverBacked || true)`). Until then its
  matrix row is `Exists? NO` in substance, whatever code exists.

- **CLOSED — C-C1, `738820b1` (status mirrored by the Orchestrator after this file was written).**
  The F5 half of C-A.5 landed as a named scan `translatedDeleteKeys(String localeXml)` that takes **no**
  fragment parameter — the fragment can come only from `KEY_FRAGMENT` — plus a **positive control** that
  plants a real derived key into a synthetic `<resources>` document and asserts that same scan reports
  it. Mutants shown RED, each inside the named scan, restored from `cp` backups:
  `"delete_from_library_ZZZ"` and `KEY_FRAGMENT + "_ZZZ"` (both `expected:<1> but was:<0>`),
  `"delete_from" + "_library" + "_ZZZ"` (killed by the control alone), and the verbatim re-wrap (two
  failures: the one-literal pin `1 ≠ 2` **and** the control). Focused class 16 / 0; full suite
  150 / 0 failures / 1 skip.
  **Standing rule, retained:** do not implement this as a `source.contains(...)` pin — the Reviewer
  verified that form is self-satisfying and leaves `m_f5a`/`m_f5a2` GREEN.
  **Reviewer-confirmed (round 4, `review-round4.md` §1): all four mutants re-run independently, same
  results; HEAD green focused and full.**

- **History (so a later reader is not misled):** through round 1 the matrix cited five
  never-authored methods as coverage, and T6a/T10a were ticked while naming two of them
  (`e2e3_alreadyGoneSecondDelete`, `e2e6_cachedSongPurged`). Both remain `Exists? NO` rows. The
  three properties the Reviewer killed by mutation in round 1 (R2 request-before-confirm, R2
  destructive copy, R9 SUCCESS-branch refresh) had no executed coverage at that point; they were
  recorded as `Exists? NO` for the round-2 lane and are **closed** — the rows now name the executed
  tests (`DeleteFromLibraryDialogSourceTest`, the SUCCESS-scoped R4/R9 assertions) and the mutants
  each kills. Round 3 then closed C-A.1-C-A.4 and R2-B1 by mutation (and C-A.3/C-A.4 by device run), so
  what remains open is: the behavioral end-to-end claims of R3/R4/R9/R10 (G3), the three NOT RUN device
  legs above (G4), R11's file removals inside the gate (F2/G5), and three stated textual bounds — R3-B1 (call-site consumption of `removalOrder()`), the R2-B1 survivor
  (`serverBacked = song != null || (…)`), and the lexical extent of C-A.2's pre-request region.

## Named limitations (stated, not implied guarantees)

- **L1 — the 401 branch is not E2E-testable on this server as configured.** The 2026-11-12
  read-only probe (`.plan.md`, *Server-side ground truth*) shows the server has exactly two
  users, **both administrators with `EnableContentDeletion: true`** — including the account the
  app signs in as. There is therefore no account on this server that can produce a 401, and
  creating a temporary non-admin user would itself be a server-side change, forbidden by the
  feature's hard constraint. Decision (Orchestrator steer, option (a)): R5 is verified by the
  unit test above, which forces the 401 status into the pure outcome mapping. **E2E on this
  server does not prove the non-admin default-deny behavior** — that default is a fork property
  evidenced by the `UserManager.cs:766` source probe in `.plan.md`, not by any test here. Re-run
  the `GET /Users` probe before each QA cycle; if a non-admin user is ever provisioned by the
  operator, L1 can be retired and a real 401 E2E added.
- **L2 — the request-log oracle is a temporary server setting.** Server request logging is
  enabled today (operator note in `progress/qa-emulator.md`, with an explicit obligation to
  revert it after this feature's QA). E2E-1/3/5 use it when available; if it has been reverted,
  E2E-5 degrades to its in-app leg (current item advanced) and the driver records the log leg
  as skipped — it must never fail silently or fabricate the assertion.
- **L3 — restore may need the operator to trigger the scan.** The QA account qualifies for
  `POST /Library/Refresh` today (admin per the probe). If that ever changes, the operator
  triggers the scan from the dashboard; the restore itself is never skipped.
- **L4 — stale `.m3u8` playlists.** Item deletion does not route through the playlist-save path
  (source evidence in `.plan.md`), so a file-backed `.m3u8` could survive with the dead entry
  and be re-imported by a scan. Today's probe shows the nominated playlist is DB-backed
  (`Container: None`), so the risk is practical-low; E2E-2 is the case that will pin it —
  **NOT YET WRITTEN** (see L5). If a re-import is ever observed, it is recorded as a residual
  server-side risk — the client is forbidden from "fixing" it (server read-only).
- **L5 — destructive E2E execution is deferred; its *authoring* is not.** Keeping the
  destructive *run* of the E2E cases out of the builder lane was a deliberate decision and
  stands. It was never a licence to cite those cases as coverage:
  `e2e1_happyPathCycle`, `e2e2_playlistCleanAfterScan`, `e2e3_alreadyGoneSecondDelete`,
  `e2e5_noSessionReportsForDeadId`, `e2e6_cachedSongPurged` and `tools/qa-delete-cycle.sh` are
  **NOT YET WRITTEN** (G2 and the `Exists? NO` rows in the matrix), so R3, R4, R9 and R10 stay
  unverified until the QA round authors and runs them — before this feature's version bump
  ships.

## QA protocol (operator-mandated — runs for every destructive E2E, every time)

Destructive tests on real library data; the protocol leaves the library exactly as found so the
cycle is repeatable per run. QA target is the **emulator** (`progress/qa-emulator.md`: AVD
`latest_api36`, headless launch, debug-suffixed package, `POST_NOTIFICATIONS` granted, battery
dialog dismissed, **no reboots**); the **server is the second oracle** — app claims are never
the only evidence. The driver is `tools/qa-delete-cycle.sh` — **NOT YET WRITTEN** (G2): the
protocol below is the specification that script must implement, and until it exists no
destructive cycle can be run. When written it must read server URL, credentials and token
**only** from the operator's runtime procedure (`progress/qa-emulator.md`) and hold none in the
repository; no hostname, user id, token, NAS path or device identifier is ever written into a
committed file or a driver log that lands in the repo.

Sacrificial fixture (operator-nominated, expendable, testing only): the track
**"<redacted fixture track>"** in the playlist **<redacted fixture playlist>**. Verified present 2026-11-12 (probe output
in `.plan.md`: 1 Audio record; playlist exists, DB-backed, 4 entries including the track).
Never delete any other item; never touch items that are operator favorites.

Each destructive run executes the full cycle (as a script, **NOT YET WRITTEN** — see G2):

- **P0 Pre-probe** — driver: `GET /Users` policy probe (see L1); find the sacrificial item id
  by search; enumerate **every** playlist on the server that currently holds it (the holder
  set H) and each holder's entry count; abort the run if the track is missing or in no
  playlist (the protocol's premise is broken — fix the fixture, do not improvise with another
  item).
- **P1 Backup** — `GET /Items/{id}/Download` (ranged GET; verified 206 + m4a bytes 2026-11-12)
  → full file saved to a host temp dir **outside the repo and outside any library share**,
  sha256 recorded; original library-folder path and H recorded at run time only.
- **P2 Act** — on the emulator: play the sacrificial track (so it is the *playing* song),
  open the player overflow, confirm via the dialog.
- **P3 Assert on the server** — `GET /Items/{deletedId}` → 404; every playlist in H no longer
  lists the id; E2E-2 additionally runs a library scan (`POST /Library/Refresh`, wait for
  completion) and re-asserts no playlist resolves the dead id (the `.m3u8` leg, L4).
- **P4 Assert in the app** — success toast shown; queue advanced to the next track (mini-player
  and full player show the new current item); Favorites tab refreshed (R9 leg); reopening each
  playlist in H shows the entry gone.
- **P5 Restore** — the backed-up file is placed back into its original library folder through
  the operator's server-admin shell procedure (procedure referenced in `progress/qa-emulator.md`;
  the mechanism itself is not part of the app and changes no server configuration), then the
  library is refreshed (L3). Poll until the track is re-indexed; record its **new** item id.
- **P6 Re-add** — for every playlist in H: stock add-to-playlist call with the new id; re-assert
  each holder's entry count equals the P0 value (entry order may differ — recorded, not asserted).
- **P7 As-found audit** — the track exists exactly once; every playlist in H holds it again;
  no playlist outside H changed. Expected, asserted-as-facts (never treated as failures): the
  restored item has a **new id**, and favorite flag/play history do **not** survive (UserData
  re-point evidence in `.plan.md`) — a test asserting favorites survive is wrong by design. If
  the fixture was favorited before the run (it is nominated as not-favorited; assert anyway),
  QA re-favorites it through the app so state is as found.
- **P8 Teardown** — driver log and backups stay outside the repo; emulator state resets by
  relaunch, never by rebooting a physical device.

### The six E2E cases (all on the emulator, all with the server as second oracle)

> **This is the QA round's work order, not an inventory.** Only `e2e4_cancelNoop` exists in
> `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`.
> The other five are **NOT YET WRITTEN**; each bullet below is what the QA round must author,
> and the driver `tools/qa-delete-cycle.sh` (G2) must exist before any of them can run.

- **e2e1_happyPathCycle** (R3, R4, R9) — **NOT YET WRITTEN — the QA round must author it in
  `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`.**
  The full P0→P8 cycle around one confirmed delete, including the **lone-entry leg**: play the sacrificial track by itself, delete, and assert
  playback stops cleanly with the now-playing surface not presenting the dead item (R4's
  where-clause), before running the from-a-playlist cycle.
- **e2e2_playlistCleanAfterScan** (R4, R9) — **NOT YET WRITTEN — the QA round must author it in
  `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`.**
  Delete, scan cycle, P3's playlist-cleanliness leg including the stale-`.m3u8` assertion (L4),
  then P5→P8.
- **e2e3_alreadyGoneSecondDelete** (R6) — **NOT YET WRITTEN — the QA round must author it in
  `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`.**
  Within one cycle: delete succeeds, the app is pointed at the same dead id again (re-queued restored-queue trick or second activation after re-open),
  the second attempt toasts *already gone*, distinct from success, item stays out of the queue;
  then P5→P8.
- **e2e4_cancelNoop** (R2) — **WRITTEN but NEVER EXECUTED** (the only E2E method that exists, and
  it has zero executed runs — see G4). Open the
  dialog, Cancel; server shows the item still present and no DELETE in the request log (L2 when
  available); queue untouched. Non-destructive, but runs P0/P7 as guard rails. It `assumeTrue`s on
  `qa_server/qa_token/qa_user/qa_item` instrument args supplied by the driver (G2), so it cannot
  execute until that driver exists — and its skip prints as `0 ignored`, which is why "the E2E suite
  is green" must never be quoted as evidence for R2.
- **e2e5_noSessionReportsForDeadId** (R10) — **NOT YET WRITTEN — the QA round must author it in
  `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`.**
  After delete-and-advance, no `Sessions/Playing/Progress` request line names the deleted id
  for 30 s (request-log leg, L2);
  app restart shows the `favorites_guard` suppression pref no longer naming the deleted id;
  embedded in a full cycle.
- **e2e6_cachedSongPurged** (R11) — **NOT YET WRITTEN — the QA round must author it in
  `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`.**
  Cache the sacrificial track via the app's existing cache action, verify cached state; run the
  delete cycle; assert the cached state is gone in-app and the on-disk cached file under the
  app's cache location is removed; then P5→P8.

## Non-functional checks

- Lint: `./gradlew lintDebug` clean (per `harness.config.yaml`).
- Types/compile: `./gradlew compileDebugKotlin compileDebugJavaWithJavac` clean.
- Unit gate: `./gradlew testDebugUnitTest` green (this is `verification.test_command` as
  configured; E00-F01 formalizes the same scalars and is still `pending` — see plan note 10).
- GPLv3 / F-Droid: no new dependencies (see plan *Stack*), so the license posture is unchanged.
- PII screen for the whole feature: no hostname, user id/name, token, password, device serial,
  NAS path, cluster/context name or vault key name in any committed file, test resource, driver
  log inside the repo, commit message, or QA report. The Reviewer greps the diff for the
  server host and the token header value before approval.

## Behavioral summary for the Reviewer

Play a song → 3-dot menu → **Delete from library** → confirm → success toast, the song is gone
from the server (`GET` 404), from every playlist that held it, and from Favorites' stale view;
playback moved to the next track with no dead mini-player. Cancel instead changes nothing. A
refusal (401) and an already-gone item (404) each say which — and after every destructive test
the protocol puts the file back, re-adds it to every playlist, and the library is as found.

**Which parts of that sentence are demonstrated today** (executed, with the caveat in each row):
the four outcome mappings, the in-flight latch including the synchronous-throw path, the
menu-item statics, the English-only string rule, the destructive-copy content and the
dialog-issues-no-request rule **as JVM source contracts**, and — on device, per-test verified — the
hidden-when-empty-queue leg, the Room cache-row purge, the three cached-file removals, the absent-`songs`-
row purge leg, and the R11 class's self-restoring behaviour (round 3, `OK (3 tests)` with per-test logcat
and no assumption skip, plus two device mutants RED). Also executed-pinned by mutation since round 3:
the removal **order** inside `removalOrder` and the five success-side effects in the pre-request region.

**Not demonstrated:** that a confirmed delete reaches the server once and 404s afterwards (R3),
that playback advances or stops cleanly (R4), that Favorites refreshes (R9), and that no session
report names the dead id (R10) — those four need the unwritten E2E cases and the unwritten driver
(G2, G3, L5). Also not demonstrated, and easy to miss: **R1's shown-when-server-backed leg**, the
**confirm-dialog-on-device** behavior and the **cancel-is-a-noop path** (all three legs self-skipped;
`e2e4_cancelNoop` has never run — G4), **R11's file removal inside the gate** (F2, G5), the **call site's
consumption** of `removalOrder()` (R3-B1: the order is executed-pinned, the consumption only textual, and
the behavioral pin is C-B B5's lone-entry leg), the `serverBacked = song != null || (…)` survivor, and the
**locale scan's own target** (F5 / C-C1). What round 2 listed as undemonstrated and round 3 has since
demonstrated — R4's removal ORDER, R11 with a missing `songs` row, and the always-visible
`setVisible(serverBacked || true)` shape — is now RED-mutated coverage, with the bounds above. R6 and R11 each keep one unwritten on-device E2E leg too (the
second-delete toast and the end-to-end cached-state purge), but both have an executed contract for
the behavior itself; R3, R4, R9 and R10 do not.

**QA round's must-run device set** (C-B, before the version bump ships, with per-test
`started:`/`finished:` evidence and no `assumption failed` line): `CachePurgeOnDeleteInstrumentedTest`
— the only pin for R11's file removals (G5), **including all three of its legs as shipping legs, on a
committed, unsabotaged tree: `purgeRemovesCacheRowAndCachedFiles`, `purgeOfUncachedItemIsANoOp`, and
the C-A.3 absent-`songs`-row leg `purgeRemovesCacheRowWhenTheSongsRowIsAbsent`** — plus
`DeleteMenuItemVisibilityTest`, `DeleteConfirmDialogTest` and `e2e4_cancelNoop`, all of which need a
live app session first, or they silently do nothing and still report OK.

**Explicitly NOT a C-B step (do not do this to a shipping tree): "break the purge and run the class
twice".** That was the C-A.4 **review-time mutation probe** — production code deliberately sabotaged to
check that the fixture is self-restoring — and it **has been performed** (round 3, run 1 and run 2 both
failing on `downloaded audio file must be gone (R11)` at `:76`, plus the `reclaimStaleFixture`-removed
reproduction). QA runs the class as committed; a passing run is the expectation, and any run that
requires editing `app/**` first is a mutation campaign, not a shipping check. C-A.4's self-restoring
property is therefore already evidenced and needs no shipping-run counterpart beyond "all three legs
execute and pass".
