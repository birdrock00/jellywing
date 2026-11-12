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
>   0 ignored` whether or not a leg executed. Four of the six device legs in this matrix skipped on
>   the 2026-11-12 run, including the only E2E method that exists, which has **never executed**.
>   See **G4** and the ledger in that section. **An ignored test is a missing test, and this
>   harness's runners do not distinguish the two.**

| R-id | Behavior | Test (file::name) | Type | Exists? | Status |
|---|---|---|---|---|---|
| R1 | Overflow hides **Delete from library** when no song is current | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java::deleteItemHiddenWhenNoSongIsCurrent` — the only device leg of R1 that executed on 2026-11-12 (per-test `started:`/`finished:` in logcat) | instrumented (**not** in the unit gate — G1) | yes (leg executed) | ⬜ |
| R1 | Overflow **shows** the item when a server-backed song is current | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java::deleteItemShownWhenServerBackedSongIsCurrent` — **exists, self-skips without a live Jellyfin session (`assumption failed: Need an existing real Jellyfin session`) — NOT RUN**; the runner still printed `OK (2 tests)` (Reviewer F6, G4) | instrumented (**not** in the unit gate — G1) | yes, **self-skips — NOT RUN** | ⬜ |
| R1 | Menu entry statics: id present, `showAsAction="never"`, title from a string resource | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testMenuItemPresent` | unit (source contract) | yes | ⬜ |
| R2 | Confirm dialog precedes any network; Cancel is a no-op (device) | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteConfirmDialogTest.java::selectDeleteOpensDestructiveConfirmDialogAndCancelIsNoop` — **exists, self-skips without a live Jellyfin session (`assumption failed: Need an existing real Jellyfin session`) — NOT RUN** on 2026-11-12, while `am instrument` printed OK (Reviewer F6, G4) | instrumented (non-destructive; **not** in the unit gate — G1) | yes, **self-skips — NOT RUN** | ⬜ |
| R2 | Cancel leaves the item on the server and issues no DELETE (E2E) | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java::e2e4_cancelNoop` — **exists but has NEVER executed**: it `assumeTrue`s on driver args (`E2E-4 needs driver args (qa_server/qa_token/qa_user/qa_item)`) supplied by `tools/qa-delete-cycle.sh`, which does not exist (G2). Non-execution reads as `0 ignored` (G4) | e2e (non-destructive; **not** in the unit gate — G1) | yes, **self-skips — NOT RUN** | ⬜ |
| R1 | Gate reads the current song and requires a **non-blank server id** (not merely a non-null song), and drives `setVisible` | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testVisibilityGateRequiresAServerBackedSongId` — executed JVM source contract on the helper body; kills all three round-1-surviving shapes (always-show, non-null-only, blank-id accepted). **Bound R2-B1 (open): the pin is textual, so `deleteItem.setVisible(serverBacked \|\| true)` — always visible, every token present — stayed GREEN in the round-2 campaign; C-A.5 (assert the exact expression `setVisible(serverBacked)`) had not landed as of `8e1ff1ee` and has no mutation evidence — not done** | unit (source contract) | yes | ⬜ |
| R1 | Both player themes apply the gate on menu inflate **and** on current-song change | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testBothPlayerThemesApplyTheGateOnInflateAndSongChange` — executed; dropping the call from either `CardPlayerFragment` or `FlatPlayerFragment` goes RED | unit (source contract) | yes | ⬜ |
| R2 | Handler opens the dialog and never calls the API directly | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testHandlerOpensDialogNotApiDirectly` — reads only `app/src/main/java/club/thatpetbff/gramophone/fragments/player/AbsPlayerFragment.java` | unit (source contract) | yes | ⬜ |
| R2/R3 | Opening the dialog never fires the delete — the request sits **only** inside the positive-button callback | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryDialogSourceTest.java::testConstructingTheDialogIssuesNoRequest` + `#testDeleteCallSitsInsideOnPositive` — executed source contract on `app/src/main/java/club/thatpetbff/gramophone/dialogs/DeleteFromLibraryDialog.java`, split into construction vs. `onPositive` vs. everything-outside regions. Round-1 B2 is RED today in **both** shapes: moving the call into `onCreateDialog`, and leaving it in `onPositive` *and* adding it to `onCreateDialog` | unit (source contract) | yes | ⬜ |
| R2 | Destructive copy warns in plain words (leaves the server, permanent, every playlist holding it, cannot be undone) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryDialogSourceTest.java::testConfirmCopyCarriesTheThreeWarnings` — executed **content** assertion over `delete_from_library_body`; Reviewer B3's mutant (`delete_from_library_body` → "Remove this song?") is RED here, not merely on key existence | unit (source contract) | yes | ⬜ |
| R2 | Confirm and Cancel are distinct explicit verbs (delete / cancel), not a generic OK | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryDialogSourceTest.java::testConfirmAndCancelButtonsAreDistinctVerbs` — executed | unit (source contract) | yes | ⬜ |
| R3 | Exactly one `DeleteItem` call site, through the existing apiclient, no hand-rolled HTTP | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testUtilIssuesSingleStockDeleteCall` | unit (source contract) | yes | ⬜ |
| R3 | One `DELETE /Items/{id}` actually reaches the server per confirm, and the item 404s afterwards | **NOT YET WRITTEN — the QA round must author `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`**, wrapped by the QA driver (also NOT YET WRITTEN; see G2). R3 has no device or server-side test today | e2e | **NO** | ⬜ |
| R4 | Success toast; queue drops the deleted entry and advances to the next track | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` — **SUCCESS-block-scoped since `9fb1f19b`**: it reads the `case SUCCESS:` … `break;` region and asserts the success toast, `removeFromQueueAndAdvance(` and `FavoritesStore.notifyChanged()` inside it. Reviewer B4 is RED, and the kill is independent of R6: removing `notifyChanged()` from `ALREADY_GONE` only reddens `testAlreadyGoneRunsCleanupWithoutSuccessClaim` | unit (source contract) | yes | ⬜ |
| R4 | Nothing success-shaped happens **before** the request is issued (no early toast, no early queue edit, no early purge, no early Favorites refresh) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testInFlightGuardWrapsRequestAndReleasesOnOutcome` pins the region before `DeleteItem(` against `handleOutcome(` and `DeleteOutcome.SUCCESS` — which is what kills the optimistic-success mutant (the Reviewer re-ran it with only this test in the run: RED on its own). **Round-2 bound (Reviewer F7): a pre-request `toast(delete_from_library_success)` or `removeFromQueueAndAdvance(itemId)` was GREEN, because the region named only `handleOutcome(` and `DeleteOutcome.SUCCESS`. C-A.2 widens that region to every success-side effect and landed at `17d91a00`, but no Reviewer has re-run F7's two survivors against it — treat as IN PROGRESS, not done** | unit (source contract) | yes (C-A.2 written, **unverified**) | ⬜ |
| R4 | The cleanup helper the SUCCESS branch calls: drop **every** entry naming the deleted item, know whether the removed one was current, start the track now at that index, and stop cleanly when the queue empties | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries` — executed source contract on the `removeFromQueueAndAdvance` body; RED when only the first entry is removed and RED when the lone-entry `clearQueue()` branch is removed. **Its round-2 form did not pin the removal ORDER (Reviewer F1)** — see the next row | unit (source contract) | yes | ⬜ |
| R4 | A song queued twice yields **every** one of its queue entries; a single/absent entry is unchanged | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteDuplicateQueueTest.java::testEveryEntryOfATwiceQueuedSongIsFound` + `#testSingleAndAbsentEntriesAreUnchanged` — executed JVM **behavior** tests on `LibraryDeleteUtil.positionsOfItem` (the lookup; `found.add(0, i)` ordering included) | unit | yes | ⬜ |
| R4 | Those entries are removed **highest-index-first**, which is the only order that keeps the later indices valid | Round 2 proved nothing here: mutating the loop to ascending left the gate GREEN (`MusicPlayerRemote.removeFromQueue`'s `0 <= p < size()` guard silently refuses the out-of-range index and a dead entry survives — Reviewer F1), because the round-2 RED was the *lookup*, not the order. **C-A.1 landed at `e88b33fa`** — `LibraryDeleteUtil.removalOrder(...)` extracted plus `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteDuplicateQueueTest.java::testRemovalOrderIsHighestIndexFirst` (`[1,3] → [3,1]`, `[0] → [0]`, `[] → []`). **UNVERIFIED: no Reviewer run shows the ascending mutant RED against it — treat as IN PROGRESS, not done** | unit | yes (new, **unverified**) | ⬜ |
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
| R11 | A **missing `songs` row must not skip** the Room `cache` row (the delete is unconditional) | Round 2 proved nothing here: gating `cacheDao().deleteFromCache(itemId)` on the `song != null` guard was GREEN in the JVM gate **and** in the device class — no fixture inserted a cache row without a songs row (Reviewer F3). **C-A.3 landed at `7a3743c8`** as `app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java::purgeRemovesCacheRowWhenTheSongsRowIsAbsent` (insert a `cache` row, delete the matching `songs` row, purge, assert `isCached` false). **UNVERIFIED and NOT RUN: it has never been executed on a device (it joins C-B's must-run list, G4/G5) and no Reviewer run shows the `if (song == null) return;` mutant RED — treat as IN PROGRESS, not done** | instrumented (**not** in the unit gate — G1) | yes (new, **NOT RUN**) | ⬜ |
| R11 | The device class is self-restoring (a broken run must fail on an R11 assertion, not on a stale fixture) | **NOT PROVEN today**: the audio fixture path comes from `getFileUri(song)` (artist/album/disc/track/title/container, not the unique id), so it is the same path every run; a run whose purge is broken leaves the file and every later run fails at `assertTrue("fixture file must be creatable")` — the Reviewer hit exactly this. **C-A.4 (delete any stale fixture before `createNewFile()`, clean up the three paths plus the inserted rows in `finally`) landed at `8e1ff1ee`. UNVERIFIED and NOT RUN: the Reviewer's C-A.4 acceptance (break the purge once, run the class twice, the second run must fail on an R11 assertion) has not been performed on a device — treat as IN PROGRESS, not done** | instrumented (**not** in the unit gate — G1) | yes (new, **NOT RUN**) | ⬜ |
| R11 | Purge runs on the success outcome only (not on 401/404/transport failure) | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testCachePurgeIsSuccessOutcomeOnly` | unit (source contract) | yes | ⬜ |
| R11 | Cached state is gone in-app and on disk after a real delete cycle on the sacrificial track | **NOT YET WRITTEN — the QA round must author `e2e6_cachedSongPurged` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the E2E half of the round-1 T10a task was never written; only the instrumented leg ran) | e2e | **NO** | ⬜ |
| R12 | New strings exist in default English only; locales untouched — for **any** `delete_from_library*` key, including ones nobody remembered | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testNewStringsLiveInDefaultEnglishOnly` — key names are now **derived by pattern** (`<string name="…">` matched against `delete_from_library`) since `f68d6eea`, with a non-vacuity floor (`localeFilesChecked > 0`; 33 locale files today). Reviewer M-4's mutant (untracked `delete_from_library_undo` added to `values-de`) is RED; the `delete_playlist_x` control stays GREEN, which is correct — substring, not prefix, on purpose. **Remaining hole (Reviewer F5): the anti-vacuity floor covers the helper and the default-resources call site, not the locale loop's own argument, so pointing the locale scan at a fragment that matches nothing is GREEN. **C-A.5 was still in flight (uncommitted) at `8e1ff1ee`: no shared `KEY_FRAGMENT` constant, no non-empty assert inside the locale test, and no exact-expression `setVisible(serverBacked)` assertion. Open until the round-3 review shows F5's and R2-B1's mutants RED** | unit (source contract) | yes (F5 hole open) | ⬜ |
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
  bound **R2-B1** (`deleteItem.setVisible(serverBacked || true)` keeps every required token and
  stays GREEN — C-A.5, which had not landed as of this pass), and R11's file removals per **G5** below.
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
  runner output for those two classes was `OK` in both cases. (Two legs added after that run —
  `purgeRemovesCacheRowWhenTheSongsRowIsAbsent` and the C-A.4 self-restoring fixture behaviour, both
  in `CachePurgeOnDeleteInstrumentedTest` — have **never executed** on any device.) R1's *shown-when-server-backed*
  property therefore has no executed device proof; its only executed pins are the JVM source
  contracts, which carry the R2-B1 bound.

- **G5 — some device-only pins are invisible to the gate in a second way (Reviewer F2).** Beyond
  G1's "the gate does not run instrumented classes", R11's three `File.delete()` calls can be
  deleted outright and `./gradlew testDebugUnitTest` stays GREEN: the JVM contract for the purge
  (`testCachePurgeIsNarrowAndLogsItsCatch`, `testCachePurgeIsSuccessOutcomeOnly`) pins cost,
  success-outcome scoping and logging — **not file removal**. Only `CachePurgeOnDeleteInstrumentedTest`
  pins them, on device, so that class is on C-B's must-run list and R11 cannot ship on the strength
  of the unit gate.

- **IN PROGRESS THIS ROUND — C-A.1 → C-A.5. None of them is done, because none has Reviewer mutation
  evidence.** As observed at `8e1ff1ee` (2026-11-12 ~21:26 UTC; the Builder was committing `app/**`
  while this matrix was edited, so these shas are a dated observation, not a claim about the tree a
  later reader will find): **C-A.1** removal-order pin, `e88b33fa` (F1) · **C-A.2** pre-request region
  widened to every success-side effect — `delete_from_library_success`, `removeFromQueueAndAdvance(`,
  `purgeCacheEntry(`, `FavoritesStore.notifyChanged(`, `clearSuppressionIfOurs(` — `17d91a00` (F7) ·
  **C-A.3** instrumented absent-`songs`-row purge leg, `7a3743c8` (F3) · **C-A.4** self-restoring R11
  fixture, `8e1ff1ee` (F4) · **C-A.5** shared `KEY_FRAGMENT` + non-empty assert inside the locale test
  + exact-expression `setVisible(serverBacked)` (F5, R2-B1) — **uncommitted at that commit**.
  **Operative rule for the QA round and the Reviewer: a C-A item counts as coverage only when the
  review shows its named mutant RED (C-A.1: the ascending removal loop; C-A.2: a pre-request success
  toast and a pre-request `removeFromQueueAndAdvance(itemId)`; C-A.3: `if (song == null) return;`
  before `deleteFromCache(itemId)`, on device; C-A.4: break the purge, run the class twice, the second
  run must fail on an R11 assertion and never on "fixture file must be creatable"; C-A.5: a locale
  fragment that matches nothing, and `setVisible(serverBacked || true)`). Until then its matrix row is
  `Exists? NO` in substance, whatever code exists. C-A.3 and C-A.4 are instrumented legs, so they also
  need a real device run with per-test evidence (G4) before they mean anything.**

- **History (so a later reader is not misled):** through round 1 the matrix cited five
  never-authored methods as coverage, and T6a/T10a were ticked while naming two of them
  (`e2e3_alreadyGoneSecondDelete`, `e2e6_cachedSongPurged`). Both remain `Exists? NO` rows. The
  three properties the Reviewer killed by mutation in round 1 (R2 request-before-confirm, R2
  destructive copy, R9 SUCCESS-branch refresh) had no executed coverage at that point; they were
  recorded as `Exists? NO` for the round-2 lane and are **closed** — the rows now name the executed
  tests (`DeleteFromLibraryDialogSourceTest`, the SUCCESS-scoped R4/R9 assertions) and the mutants
  each kills. What remains open is behavioral, not textual: R3/R4/R9/R10 end to end (G3), the three
  NOT RUN device legs above (G4), and the four unpinned properties C-A.1–C-A.5 / F1/F3/F5/R2-B1.

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
**"Jai Hind"** in the playlist **aurral-udta-teer**. Verified present 2026-11-12 (probe output
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
hidden-when-empty-queue leg, the Room cache-row purge and the three cached-file removals.

**Not demonstrated:** that a confirmed delete reaches the server once and 404s afterwards (R3),
that playback advances or stops cleanly (R4), that Favorites refreshes (R9), and that no session
report names the dead id (R10) — those four need the unwritten E2E cases and the unwritten driver
(G2, G3, L5). Also not demonstrated, and easy to miss: **R1's shown-when-server-backed leg**, the
**confirm-dialog-on-device** behavior and the **cancel-is-a-noop path** (all three legs self-skipped;
`e2e4_cancelNoop` has never run — G4), **R4's removal ORDER** (F1), **R11's file removal inside the
gate** (F2, G5), **R11 with a missing `songs` row** (F3), and **R1's gate against a textually-complete
always-visible expression** (R2-B1). R6 and R11 each keep one unwritten on-device E2E leg too (the
second-delete toast and the end-to-end cached-state purge), but both have an executed contract for
the behavior itself; R3, R4, R9 and R10 do not.

**QA round's must-run device set** (C-B, before the version bump ships, with per-test
`started:`/`finished:` evidence and no `assumption failed` line): `CachePurgeOnDeleteInstrumentedTest`
— the only pin for R11's file removals (G5), **including its two new never-executed legs: the C-A.3
absent-`songs`-row leg and the C-A.4 self-restoring fixture behaviour (break the purge, run twice,
the second run must fail on an R11 assertion)** — plus `DeleteMenuItemVisibilityTest`,
`DeleteConfirmDialogTest` and `e2e4_cancelNoop`, all of which need a live app session first, or they
silently do nothing and still report OK.
