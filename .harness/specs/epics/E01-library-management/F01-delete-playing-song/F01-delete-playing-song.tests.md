# Delete the playing song from the Jellyfin library — Test Contract

> The traceability matrix: every `R-id` maps to a concrete, executable test. The Reviewer
> fails the feature if any `R-id` lacks a passing test. E2E rows run under the QA protocol
> below — destructive by design, restored every cycle.

| R-id | Behavior | Test (file::name) | Type | Status |
|---|---|---|---|---|
| R1 | Overflow shows **Delete from library** iff a server-backed song is current | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java` | instrumented | ⬜ |
| R1 | Menu entry statics: id present, `showAsAction="never"` | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testMenuItemPresent` | unit (source contract) | ⬜ |
| R2 | Confirm dialog precedes any network; Cancel is a no-op | `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteConfirmDialogTest.java` + e2e: `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java::e2e4_cancelNoop` | instrumented + e2e | ⬜ |
| R2 | Handler opens the dialog and never calls the API directly | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testHandlerOpensDialogNotApiDirectly` | unit (source contract) | ⬜ |
| R3 | Confirm issues exactly one `DELETE /Items/{id}` via the existing apiclient | e2e: `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java::e2e1_happyPathCycle` (server oracle: the item 404s; driver request-log leg shows a single DELETE) | e2e | ⬜ |
| R4 | Success toast; queue drops the entry and advances (lone entry: clean stop) | e2e: `::e2e1_happyPathCycle` (incl. its lone-entry leg) + `::e2e2_playlistCleanAfterScan` | e2e | ⬜ |
| R5 | 401 → permission-refusal toast naming the fix; nothing claimed or changed | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testStatus401MapsToPermissionDenied` — **forced status; see limitation L1** | unit | ⬜ |
| R6 | 404 → distinct already-gone toast + client cleanup, no deletion claim | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testStatus404MapsToAlreadyGone` + e2e: `::e2e3_alreadyGoneSecondDelete` | unit + e2e | ⬜ |
| R7 | Transport / other-status failure → named failure, nothing claimed | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testTransportErrorMapsToNetworkFailure` and `::testOtherStatusMapsToServerFailure` | unit | ⬜ |
| R8 | In-flight delete absorbs repeat activations; one request only | `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java::testSecondRequestIgnoredWhileInFlight` | unit | ⬜ |
| R9 | Success/404 path triggers `FavoritesStore.notifyChanged()` (Favorites refresh unprompted) | e2e: `::e2e1_happyPathCycle` (Favorites-tab leg) | e2e | ⬜ |
| R10 | Success clears a stale suppression guard; no later session-progress report names the dead id | e2e: `::e2e5_noSessionReportsForDeadId` | e2e | ⬜ |
| R11 | Success purges the Room `cache` row and cached file | `app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java` + e2e: `::e2e6_cachedSongPurged` | instrumented + e2e | ⬜ |
| R12 | New strings exist in default English only; locales untouched | `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java::testNewStringsLiveInDefaultEnglishOnly` | unit (source contract) | ⬜ |

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
  (`Container: None`), so the risk is practical-low; E2E-2 pins it. If a re-import is ever
  observed, it is recorded as a residual server-side risk — the client is forbidden from
  "fixing" it (server read-only).
## QA protocol (operator-mandated — runs for every destructive E2E, every time)

Destructive tests on real library data; the protocol leaves the library exactly as found so the
cycle is repeatable per run. QA target is the **emulator** (`progress/qa-emulator.md`: AVD
`latest_api36`, headless launch, debug-suffixed package, `POST_NOTIFICATIONS` granted, battery
dialog dismissed, **no reboots**); the **server is the second oracle** — app claims are never
the only evidence. The driver is `tools/qa-delete-cycle.sh`; it reads server URL, credentials
and token **only** from the operator's runtime procedure (`progress/qa-emulator.md`) and holds
none in the repository; no hostname, user id, token, NAS path or device identifier is ever
written into a committed file or a driver log that lands in the repo.

Sacrificial fixture (operator-nominated, expendable, testing only): the track
**"Jai Hind"** in the playlist **aurral-udta-teer**. Verified present 2026-11-12 (probe output
in `.plan.md`: 1 Audio record; playlist exists, DB-backed, 4 entries including the track).
Never delete any other item; never touch items that are operator favorites.

Each destructive run executes the full cycle:

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

- **e2e1_happyPathCycle** (R3, R4, R9) — the full P0→P8 cycle around one confirmed delete,
  including the **lone-entry leg**: play the sacrificial track by itself, delete, and assert
  playback stops cleanly with the now-playing surface not presenting the dead item (R4's
  where-clause), before running the from-a-playlist cycle.
- **e2e2_playlistCleanAfterScan** (R4, R9) — delete, scan cycle, P3's playlist-cleanliness leg
  including the stale-`.m3u8` assertion (L4), then P5→P8.
- **e2e3_alreadyGoneSecondDelete** (R6) — within one cycle: delete succeeds, the app is pointed
  at the same dead id again (re-queued restored-queue trick or second activation after re-open),
  the second attempt toasts *already gone*, distinct from success, item stays out of the queue;
  then P5→P8.
- **e2e4_cancelNoop** (R2) — open the dialog, Cancel; server shows the item still present and
  no DELETE in the request log (L2 when available); queue untouched. Non-destructive, but runs
  P0/P7 as guard rails.
- **e2e5_noSessionReportsForDeadId** (R10) — after delete-and-advance, no
  `Sessions/Playing/Progress` request line names the deleted id for 30 s (request-log leg, L2);
  app restart shows the `favorites_guard` suppression pref no longer naming the deleted id;
  embedded in a full cycle.
- **e2e6_cachedSongPurged** (R11) — cache the sacrificial track via the app's existing cache
  action, verify cached state; run the delete cycle; assert the cached state is gone in-app and
  the on-disk cached file under the app's cache location is removed; then P5→P8.

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
