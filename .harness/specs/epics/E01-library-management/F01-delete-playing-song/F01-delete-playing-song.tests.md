# Delete the playing song from the Jellyfin library — Test Contract

> The traceability matrix: every `R-id` maps to a concrete, executable test. The Reviewer
> fails the feature if any `R-id` lacks a passing test. E2E rows run under the QA protocol
> below — destructive by design, restored every cycle.
>
> **Coverage as it actually stands (corrected 2026-11-12 after Reviewer round 1, blocker B1).**
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
> - Three safety/content properties that the Reviewer found unpinned by mutation (**R2**
>   "no request before the positive button", **R2** destructive copy content, **R9**
>   SUCCESS-branch Favorites refresh) had **no executed coverage at all** as of round 1: each
>   surviving mutation left the 135-test gate green. They are recorded as `Exists? NO` rows
>   below so the history and the gap are both visible.

| R-id | Behavior | Test (file::name) | Type | Exists? | Status |
|---|---|---|---|---|---|
| R1 | Overflow shows **Delete from library** iff a server-backed song is current | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java` (2 tests: shown with a server-backed song current, hidden on an empty queue) | instrumented (**not** in the unit gate — see G1) | yes | ⬜ |
| R1 | Menu entry statics: id present, `showAsAction="never"`, title from a string resource | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testMenuItemPresent` | unit (source contract) | yes | ⬜ |
| R2 | Confirm dialog precedes any network; Cancel is a no-op | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteConfirmDialogTest.java::selectDeleteOpensDestructiveConfirmDialogAndCancelIsNoop` + `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java::e2e4_cancelNoop` | instrumented + e2e (non-destructive; **not** in the unit gate — G1) | yes | ⬜ |
| R2 | Handler opens the dialog and never calls the API directly | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testHandlerOpensDialogNotApiDirectly` — reads only `app/src/main/java/club/thatpetbff/gramophone/fragments/player/AbsPlayerFragment.java` | unit (source contract) | yes | ⬜ |
| R2 | Opening the dialog never fires the delete — the request sits inside the positive-button callback | **NOT YET WRITTEN — the Builder must author it in `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java`** as a source contract on `app/src/main/java/club/thatpetbff/gramophone/dialogs/DeleteFromLibraryDialog.java`. Reviewer B2: moving `deleteFromLibrary(...)` into `onCreateDialog` keeps the whole executed gate green, and no executed test reads that dialog file at all | unit (source contract) | **NO** | ⬜ |
| R2 | Destructive copy warns in plain words (leaves the server, every playlist holding it, cannot be undone) | **NOT YET WRITTEN — the Builder must author the content assertion in `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java`** (Reviewer B3: replacing `delete_from_library_body` with "Remove this song?" stays green today; the only existing check is that the *key* exists, which is R12's row, not this one) | unit (source contract) | **NO** | ⬜ |
| R3 | Exactly one `DeleteItem` call site, through the existing apiclient, no hand-rolled HTTP | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testUtilIssuesSingleStockDeleteCall` | unit (source contract) | yes | ⬜ |
| R3 | One `DELETE /Items/{id}` actually reaches the server per confirm, and the item 404s afterwards | **NOT YET WRITTEN — the QA round must author `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`**, wrapped by the QA driver (also NOT YET WRITTEN; see G2). R3 has no device or server-side test today | e2e | **NO** | ⬜ |
| R4 | Success toast; queue drops the deleted entry and advances to the next track | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` — **weak as written**: whole-file `contains(...)`, which the `ALREADY_GONE` branch also satisfies, so it does not pin the success path (Reviewer B4). Must be scoped to the `SUCCESS` block | unit (source contract) | yes (SUCCESS-scoping pending) | ⬜ |
| R4 | Lone-entry queue: after delete, playback stops cleanly and no surface presents the dead item | **NOT YET WRITTEN — the QA round must author the lone-entry leg of `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (Reviewer: the code reads correct; no test of any kind covers it) | e2e | **NO** | ⬜ |
| R4 | After a delete and a library scan, every playlist that held the item no longer lists it | **NOT YET WRITTEN — the QA round must author `e2e2_playlistCleanAfterScan` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the stale-`.m3u8` leg of limitation L4) | e2e | **NO** | ⬜ |
| R5 | 401 → permission-refusal toast naming the fix; nothing claimed or changed | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testStatus401MapsToPermissionDenied` — **forced status; see limitation L1** | unit | yes | ⬜ |
| R6 | 404 → distinct already-gone toast + client cleanup, no deletion claim | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testStatus404MapsToAlreadyGone` + `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testAlreadyGoneRunsCleanupWithoutSuccessClaim` (branch-scoped) | unit | yes | ⬜ |
| R6 | A second delete of the same id on device really toasts *already gone* and keeps the item out of the queue | **NOT YET WRITTEN — the QA round must author `e2e3_alreadyGoneSecondDelete` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the E2E half of the round-1 T6a task was never written; only its unit leg ran) | e2e | **NO** | ⬜ |
| R7 | Transport / other-status failure → named failure, nothing claimed | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testTransportErrorMapsToNetworkFailure`, `#testOtherStatusMapsToServerFailure`, `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testFailureOutcomesChangeNothingButToast` | unit | yes | ⬜ |
| R8 | In-flight delete absorbs repeat activations; one request only | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testSecondRequestIgnoredWhileInFlight` + `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testInFlightGuardWrapsRequestAndReleasesOnOutcome` — **gap**: neither covers a *synchronous* throw from the API call, which leaves the latch armed for the life of the process (Reviewer M-1) | unit | yes | ⬜ |
| R9 | Success path refreshes Favorites without user action | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` — **does not pin R9 today**: the assertion is whole-file, so removing `FavoritesStore.notifyChanged()` from the `SUCCESS` branch alone stays green (Reviewer B4 / mutation M12). Must be scoped to the `SUCCESS` block | unit (source contract) | yes (SUCCESS-scoping pending) | ⬜ |
| R9 | The Favorites tab visibly refreshes on device after a real delete | **NOT YET WRITTEN — the QA round must author the Favorites-tab leg of `e2e1_happyPathCycle` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** | e2e | **NO** | ⬜ |
| R10 | Success clears a suppression guard that names the deleted id, and the advance is sequenced before the clear | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testSuccessClearsStaleSuppressionGuardOnlyForTheDeletedId` (SUCCESS-block-scoped; the inverted sequencing does go RED) | unit (source contract) | yes | ⬜ |
| R10 | No later `Sessions/Playing/Progress` report names the deleted id, and the restart shows the guard pref cleared | **NOT YET WRITTEN — the QA round must author `e2e5_noSessionReportsForDeadId` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`**, which needs the QA driver (G2) for its request-log leg. R10 has no device or server-side test today | e2e | **NO** | ⬜ |
| R11 | Success purges the Room `cache` row and the cached files; un-cached purge is a no-op | `app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java` (2 tests; asserts actual file removal, Reviewer ran them on the emulator: OK) + `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testCachePurgeIsSuccessOutcomeOnly` | instrumented (**not** in the unit gate — G1) + unit | yes | ⬜ |
| R11 | Cached state is gone in-app and on disk after a real delete cycle on the sacrificial track | **NOT YET WRITTEN — the QA round must author `e2e6_cachedSongPurged` in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java`** (the E2E half of the round-1 T10a task was never written; only the instrumented leg ran) | e2e | **NO** | ⬜ |
| R12 | New strings exist in default English only; locales untouched | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testNewStringsLiveInDefaultEnglishOnly` — **gap**: the key list is a hand-typed literal, so a new delete string added to a locale escapes the contract (Reviewer M-4); derive it from the `delete_from_library` name pattern | unit (source contract) | yes | ⬜ |

## What actually executes, and where the gaps are

- **G1 — the automated gate is the JVM unit gate only.** `verification.test_command` is
  `./gradlew testDebugUnitTest`, which runs `app/src/test/` and nothing else. Every
  `instrumented` row above (`DeleteMenuItemVisibilityTest`, `DeleteConfirmDialogTest`,
  `DeleteFromLibraryE2eTest`, `CachePurgeOnDeleteInstrumentedTest`) requires an explicit
  `am instrument` / `connectedAndroidTest` run and contributes nothing to a green gate. A
  requirement whose only rows are instrumented is therefore *unpinned* in the gate: R1's
  visibility helper can be forced to always show the item and the gate stays green
  (Reviewer M-5).
- **G2 — the QA driver does not exist.** `tools/qa-delete-cycle.sh` is declared `create:` in
  `.plan.md` and has never been written, so the P0→P8 backup/restore protocol below cannot be
  run today by anyone. It is QA-round work, and until it exists every destructive E2E case is
  unrunnable regardless of whether its Java leg gets written.
- **G3 — R3, R4, R9, R10 have no executed proof of their specified behavior.** Restated here
  because the matrix shape hides it: for each of those four, *every* row that would establish
  the end-to-end claim is `Exists? NO`, and the rows that exist are source-text contracts whose
  weakness is named in the row. Their verification path runs through `e2e1`/`e2e2`/`e2e5` plus
  the driver — real work still to be authored under P0→P8.
- **History (so a later reader is not misled):** through round 1 the matrix cited five
  never-authored methods as coverage, and T6a/T10a were ticked while naming two of them
  (`e2e3_alreadyGoneSecondDelete`, `e2e6_cachedSongPurged`). Both are now `Exists? NO` rows.
  The three properties the Reviewer killed by mutation (R2 request-before-confirm, R2
  destructive copy, R9 SUCCESS-branch refresh) had no executed coverage either; they are now
  explicit `Exists? NO` rows for the Builder's round-2 lane.

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
- **e2e4_cancelNoop** (R2) — **WRITTEN** (the only E2E method that exists today). Open the
  dialog, Cancel; server shows the item still present and no DELETE in the request log (L2 when
  available); queue untouched. Non-destructive, but runs P0/P7 as guard rails.
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

**Which parts of that sentence are demonstrated today:** the menu surface, the dialog, the
cancel path, the four outcome mappings, the latch, the cache-purge primitives and the
English-only string rule. **Not demonstrated:** that a confirmed delete reaches the server once
and 404s afterwards (R3), that playback advances / stops cleanly (R4), that Favorites refreshes
(R9), and that no session report names the dead id (R10) — those four need the unwritten E2E cases
and the unwritten driver (G2, G3, L5). R6 and R11 each keep one unwritten on-device leg too
(the second-delete toast and the end-to-end cached-state purge), but both already have an
executed unit/instrumented contract for the behavior itself; R3, R4, R9 and R10 do not.
