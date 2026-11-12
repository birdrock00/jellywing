# Delete the playing song from the Jellyfin library — Technical Plan

> Translates the `.spec.md` intent into design. Every table row cites the `R-id`(s) it serves.
> There are no ADRs in this repository (see the spec's *Architecture alignment*), so rows cite
> `R-id`s only.

## Stack & dependencies

- Language/framework: existing Java Android app (`app/`), Gradle, no new dependencies.
- Server access: the vendored apiclient `ApiClient` already exposes
  `DeleteItem(String, EmptyResponse)`; the app already signs requests with the canonical
  `Authorization: MediaBrowser …` header via `app/src/main/java/club/thatpetbff/gramophone/util/AuthHeaderFix.java`.
- Tests: plain JUnit 4 unit tests (JVM, **no Robolectric**), Espresso-style instrumented tests
  under `app/src/androidTest/**`, and an operator-driven E2E protocol (see `.tests.md`).
- Host build fact (needed by every role): `java -version` on this host reports **21.0.12.1**,
  not 17 — recorded with output because a prior spec pass asserted 17 here and was wrong:

```
$ java -version
openjdk version "21.0.12.1" 2026-08-18 LTS
OpenJDK Runtime Environment (Red_Hat-21.0.12.1.1-1) (build 21.0.12.1+1-LTS)
OpenJDK 64-Bit Server VM (Red_Hat-21.0.12.1.1-1) (build 21.0.12.1+1-LTS, mixed mode, sharing)
```

## Server-side ground truth (evidence — do not re-litigate; re-probe only if the fork changes)

All server claims below were probed on 2026-11-12 against the local checkout of the running
fork (tag `v12.4`, `git describe --tags` → `v12.4`) and against the live server with read-only
requests. Line numbers are from those files as checked out.

- The endpoint is plain `[Authorize]`, **not** policy-attributed, and refuses with **401**:

```
$ sed -n '362,363p' Jellyfin.Api/Controllers/LibraryController.cs
    [HttpDelete("Items/{itemId}")]
    [Authorize]
$ grep -n "CanDelete(user)\|Unauthorized(\|DeleteFileLocation = true" Jellyfin.Api/Controllers/LibraryController.cs
386:        if (user is not null && !item.CanDelete(user))
388:            return Unauthorized("Unauthorized access");
393:            new DeleteOptions { DeleteFileLocation = true },
```

- The gate: `CanDelete()` requires `IsFileProtocol`; `IsAuthorizedToDelete` requires
  `PermissionKind.EnableContentDeletion` **or** the item's collection folder listed in
  `EnableContentDeletionFromFolders` (`MediaBrowser.Controller/Entities/BaseItem.cs:868-878`).

```
$ grep -n "public virtual bool CanDelete()\|return IsFileProtocol;\|public virtual bool IsAuthorizedToDelete\|HasPermission(PermissionKind.EnableContentDeletion)" MediaBrowser.Controller/Entities/BaseItem.cs
868:        public virtual bool CanDelete()
875:            return IsFileProtocol;
878:        public virtual bool IsAuthorizedToDelete(User user, List<Folder> allCollectionFolders)
880:            if (user.HasPermission(PermissionKind.EnableContentDeletion))
```

- Only the **initial admin** gets `EnableContentDeletion` automatically
  (`Jellyfin.Server.Implementations/Users/UserManager.cs:766`):

```
$ sed -n '764,766p' Jellyfin.Server.Implementations/Users/UserManager.cs
                newUser.SetPermission(PermissionKind.IsAdministrator, true);
                newUser.SetPermission(PermissionKind.EnableContentDeletion, true);
```

- Playlist cleanup is the server's own cascade: `DeleteItem` deletes `LinkedChildren` rows by
  **both** `ParentId` and `ChildId`, and playlist entries are
  `LinkedChildEntity(ParentId=playlist, ChildId=track)` — the `ChildId` clause is what drops
  playlist references. `UserData` rows are **not** deleted; they are re-pointed to a
  `PlaceholderId` with a `RetentionDate`.

```
$ grep -n "context.LinkedChildren.WhereOneOrMany\|SetProperty(f => f.RetentionDate\|SetProperty(f => f.ItemId, BaseItemRepository.PlaceholderId)" Jellyfin.Server.Implementations/Item/ItemPersistenceService.cs
131:                .SetProperty(f => f.RetentionDate, date)
132:                .SetProperty(f => f.ItemId, BaseItemRepository.PlaceholderId));
145:        context.LinkedChildren.WhereOneOrMany(relatedItems, e => e.ParentId).ExecuteDelete();
146:        context.LinkedChildren.WhereOneOrMany(relatedItems, e => e.ChildId).ExecuteDelete();
```

- A file-backed `.m3u8` playlist is **not** rewritten by item deletion —
  `SavePlaylistFile` (`Emby.Server.Implementations/Playlists/PlaylistManager.cs:364`) is only
  reached from playlist-edit paths (`UpdatePlaylistInternal` call sites at lines 264, 293, 360,
  586, 611, 637, 653, 662). A stale `.m3u8` could therefore be re-imported by a later scan with
  the dead entry. E2E-2 asserts playlist cleanliness after delete **plus** a scan cycle.
- Live account-policy probe (read-only `GET /Users`, canonical `Authorization: MediaBrowser …`
  header, token from the operator's runtime procedure — **never** from the repository). Output
  names are masked; ids are reported by length only:

```
HTTP 200
b*** isAdmin= True EnableContentDeletion= True FoldersFromList= [] idlen= 32
p*** isAdmin= True EnableContentDeletion= True FoldersFromList= [] idlen= 32
```

  **Consequences (decisions, not guesses).** (1) The app's QA account has delete rights, so the
  happy path is E2E-testable with the app's own login — no permission elevation. (2) Both users
  on this server are admins with `EnableContentDeletion: true`, so the **401 branch is not
  E2E-testable on this server as configured** (there is no non-admin account to sign in as, and
  creating one would itself be a server-side change this feature is forbidden from making).
  Decision: the 401 branch is covered by a JVM unit test that forces the error status —
  option (a) of the Orchestrator's steer. This is a *named limitation*, repeated in `.tests.md`.
  (3) E2E on this server therefore does **not** prove the non-admin default-deny behavior; it
  exercises the granted-permission path only. Re-run this probe before any QA cycle; if the
  server's user set or policies change, the limitation statement must be re-checked.

- Live fixture probe (read-only): the sacrificial asset nominated by the operator exists and is
  in the nominated playlist — the QA protocol's precondition is real today:

```
GET /Items?SearchTerm=<sacrificial track>&IncludeTypes=Audio   → HTTP 200, TotalRecordCount: 1
  Type: Audio | Container: mov,mp4,m4a,3gp,3g2,mj2
GET /Items?IncludeItemTypes=Playlist                           → HTTP 200, 5 playlists, incl. <nominated playlist> (Container: None → DB-backed, not .m3u8)
GET /Items?ParentId=<nominated playlist>                       → HTTP 200, entries: 4, contains <sacrificial track>: True
```

  (Names of the track and playlist are given in `.tests.md` — they are the operator-nominated
  expendable test fixture, not PII.)

- Live backup-probe (read-only ranged `GET /Items/{itemId}/Download`): `HTTP 206`,
  `1024` bytes, `audio/mp4`, `file` reports `ISO Media, Apple iTunes ALAC/AAC-LC (.M4A) Audio`
  — the backup leg of the QA protocol works over the stock API.

- Apiclient (`~/scan-work/upstream/jellyfin-apiclient-java`, the artifact the app consumes):

```
$ grep -n "public void DeleteItem" library/src/main/java/org/jellyfin/apiclient/interaction/ApiClient.java
2056:    public void DeleteItem(String id, final EmptyResponse response)
$ sed -n '784p' library/src/main/java/org/jellyfin/apiclient/interaction/ApiClient.java
    public void GetUserAsync(String id, final Response<UserDto> response)
```

  `DeleteItem` sends `DELETE {server}/Items/{id}` with the app's auth headers; the repo already
  uses it in `PlaylistUtil.deletePlaylist` (`app/src/main/java/club/thatpetbff/gramophone/util/PlaylistUtil.java:55-57`).
  `GetUserAsync` exists but `LoginService` never calls it, so no policy is cached client-side —
  consistent with the spec's "never probe permission client-side" rule.

- Error-status plumbing: the app's HTTP layer already reads the status code off a failed
  request (`app/src/main/java/club/thatpetbff/gramophone/network/PatchedVolleyHttpClient.java:123-124`
  reads `error.networkResponse.statusCode`), so branching on 401 / 404 / other needs no new
  transport code.

## Client-side ground truth (repo probes, 2026-11-12)

- Menu surface: `app/src/main/res/menu/menu_player.xml` (4 items, all others
  `showAsAction="never"` in overflow); inflated at `CardPlayerFragment:273` and
  `FlatPlayerFragment:272` via `binding.playerToolbar.inflateMenu(R.menu.menu_player)`; handled
  in `AbsPlayerFragment.onMenuItemClick` (line 51), which reads
  `MusicPlayerRemote.getCurrentSong()`.
- `Song.id` is the **server item id** (a `String` `@PrimaryKey`; the `Song(BaseItemDto)`
  constructor copies it), and `Song.hasDisplayableTitle()` etc. already treat it as identity —
  this is the visibility gate's datum.
- Queue: `MusicPlayerRemote.removeFromQueue(int)` (line 386) delegates to
  `QueueManager.removeSong(position)`, which already re-bases the current index when the removed
  position precedes it but has **no remove-current-and-continue semantics** — this plan decides
  the mechanics in *Approach notes* (serves R4).
- Favorites refresh: `FavoritesStore.notifyChanged()` (weak-listener registry, line 45) is the
  existing instant-refresh path — reuse, do not replace.
- Report-suppression guard (v1.3.13 favorite fix): `MusicService.sSuppressServerReportsForItemId`
  (line 115), pref `favorites_guard` (line 117), `clearServerReportSuppression()` (line 134).
- Local cache: Room `cache` table (`database/Cache.java`, `song.id` primary key),
  `database/CacheDao.java` today exposes only `insertCache` / `getSongs` / `isCached` —
  **no delete query exists**, so R11 needs one. Cached file layout is under
  `PreferenceUtil.getLocationCache()` (line 428); the in-flight download sits at
  `<locationCache>/download/<song.id>` (`DownloadService.java:79-80`).
- Strings: `app/src/main/res/values/strings.xml` plus **37** localized `values-*` dirs
  (`ls app/src/main/res | grep -c "^values-"` → `37`). Toast idiom:
  `MusicUtil.showToast(...)`-style `Toast.makeText` (e.g. `MusicUtil.java:313`).
- Version: `app/build.gradle` lines 13-14 `versionCode 26`, `versionName '1.3.13'`.
- Test layout precedent: JVM tests in `app/src/test/**` (JUnit 4, includes source-scan tests
  like `AbsBaseActivityPromptGuardSourceTest`), instrumented in `app/src/androidTest/**`
  (`FullPlayerRealSongsTest`, `AppSessionGuard`, `JellyDatabaseInstrumentedTest`).

## Data model

No schema change. Rows describe the data the feature reads or removes.

| Entity | Field | Type | Notes | R-id |
|---|---|---|---|---|
| `Song` (model, not a table) | `id` | String | Server item id; blank/absent ⇒ entry is not a deletable server-backed item — the visibility gate's datum | R1 |
| Room `cache` | `id` | String (PK) | Row removed on successful delete when present; today `CacheDao` has no delete query, one is added | R11 |
| Suppression guard pref `favorites_guard` | `suppress_report_item_id` | String | Cleared on successful delete when it names the deleted id | R10 |

## API / interface

Client-consumed (the app) and operator-consumed (the QA driver) endpoints. All are stock;
none is modified by this feature. Auth for the driver rides the canonical
`Authorization: MediaBrowser …` header (legacy `X-Emby-Authorization` is ignored by this
server — see `progress/qa-emulator.md`).

| Method | Path | Request | Response | R-id |
|---|---|---|---|---|
| DELETE | `/Items/{itemId}` | empty body, app auth | 204 success (file destroyed) / 401 refused / 404 not there | R3, R5, R6, R7 |
| GET | `/Items/{itemId}` | app/driver auth | 200 item / 404 — second-oracle assert after delete | R4 |
| GET | `/Items/{itemId}/Download` | ranged GET | 206 file bytes — QA backup leg | R4 |
| GET | `/Items?SearchTerm=…&IncludeTypes=Audio`, `/Items?IncludeItemTypes=Playlist`, `/Items?ParentId={playlistId}` | driver auth | fixture discovery + "every playlist holding it" enumeration + restore polling | R4, R6 |
| POST | `/Library/Refresh` | admin auth (QA account qualifies — see probe) | rescan after restoring the file; may also be triggered by the operator in the dashboard | R4 |
| POST | `/Playlists/{playlistId}/Add?Ids={newItemId}` | admin/app auth | re-add the restored track to every playlist that held it | R4 |
| GET | `/Users` | admin auth | QA precondition probe: the signing-in account's `Policy.EnableContentDeletion` is true | R5 |

## Files to change

| File | Change | R-id |
|---|---|---|
| `app/src/main/res/menu/menu_player.xml` | modify: add `action_delete_from_library`, title string ref, `showAsAction="never"` | R1 |
| `app/src/main/java/club/thatpetbff/gramophone/fragments/player/AbsPlayerFragment.java` | modify: add an `onMenuItemClick` branch that opens the confirm dialog (never calls the API directly), plus a shared helper the two themes call to hide the item when the current song has no server id | R1, R2, R8 |
| `app/src/main/java/club/thatpetbff/gramophone/fragments/player/card/CardPlayerFragment.java` | modify: one call to the visibility helper after the existing `inflateMenu` line | R1 |
| `app/src/main/java/club/thatpetbff/gramophone/fragments/player/flat/FlatPlayerFragment.java` | modify: same one call after its `inflateMenu` line | R1 |
| `app/src/main/java/club/thatpetbff/gramophone/dialogs/DeleteFromLibraryDialog.java` | create: confirmation dialog modeled on `DeletePlaylistDialog` (same `MaterialDialog.Builder` idiom), destructive copy per R2, positive button delegates to `LibraryDeleteUtil` | R2 |
| `app/src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java` | create: the whole delete pipeline — in-flight guard, `App.getApiClient().DeleteItem(...)`, pure status-to-outcome mapping (a static function testable on the JVM), success/failure toasts, queue remove-and-advance, `FavoritesStore.notifyChanged()`, cache purge, suppression-guard clear | R3, R4, R5, R6, R7, R8, R9, R10, R11 |
| `app/src/main/java/club/thatpetbff/gramophone/database/CacheDao.java` | modify: add a Room `@Query` delete-by-id method (none exists today) | R11 |
| `app/src/main/res/values/strings.xml` | modify: add the English strings — menu title, dialog title/body/confirm/cancel, success toast, 401 toast, 404 toast, network-failure toast | R1, R2, R4, R5, R6, R7, R12 |
| `app/build.gradle` | modify: `versionCode` 26→27, `versionName` 1.3.13→1.3.14 (release hygiene shipped with the new user-visible strings) | R12 |
| `app/src/test/java/club/thatpetbff/gramophone/util/LibraryDeleteOutcomeTest.java` | create: JVM unit test for the pure outcome mapping (success / 401 / 404 / transport / other status) and the in-flight guard | R5, R6, R7, R8 |
| `app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java` | create: source-contract test (repo precedent for prose contracts) — menu entry present with `never`, handler branch opens the dialog rather than calling the API, all new string keys exist in the default `values/strings.xml`, no new keys in any localized `values-*` file | R1, R2, R12 |
| `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteMenuItemVisibilityTest.java` | create: instrumented — item visible with a server-backed song current; hidden when the queue is empty | R1 |
| `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteConfirmDialogTest.java` | create: instrumented — tapping the item opens the dialog with the destructive copy; Cancel leaves playback and queue unchanged and issues no request | R2 |
| `app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java` | create: instrumented Room/file test — the new delete query removes the row; the purge helper removes the cached file and tolerates its absence | R11 |
| `app/src/androidTest/java/club/thatpetbff/gramophone/ui/DeleteFromLibraryE2eTest.java` | create: the six E2E cases of `.tests.md`, wrapped in the backup/restore protocol | R3, R4, R6, R9, R10 |
| `tools/qa-delete-cycle.sh` | create: host-side QA protocol driver — backup / enumerate-holders / oracle-assert / restore / re-add around each on-device case; reads server URL, credentials and token **only** from the operator's runtime procedure, holds none in the repo | R3, R4, R6, R9, R10 |

## Deferred  (ADR-0009 — bolt-ons the stage excludes)

Deferred: none — stage is production (`specs/product.md` frontmatter `stage: production`;
read 2026-11-12 with `sed -n '1{/^---$/!q;};1,/^---$/p' .harness/specs/product.md | grep '^stage:'`).
No bolt-on (caching, retry policy, encryption, rate limiting) is added; nothing the plan adds
lacks an `R-id`.

## DO NOT TOUCH

- Any Jellyfin **server** artifact: source, database rows, configuration files, container or
  image, scheduled tasks, user policies. The server-side cascade (LinkedChildren `ChildId`) and
  the operator's own dashboard settings are relied upon, never edited.
- `app/src/main/java/club/thatpetbff/gramophone/util/PlaylistUtil.java` — the existing
  playlist-delete path stays exactly as it is (different feature, different confirm copy).
- `app/src/main/java/club/thatpetbff/gramophone/service/MusicService.java` report-suppression
  semantics — the feature only *calls* the existing `clearServerReportSuppression()`; it does
  not alter when suppression arms or what it guards.
- The 37 localized `values-*/strings.xml` directories (R12: English only; locales fall back).
- `app/src/main/res/menu/menu_player.xml`'s existing four items — append only; and the
  fork's inherited files at large: no reformatting (product constitution upgrade-path rule).
- `harness.config.yaml`, `state/tasks.json`, and other roles' files.

## Approach notes

1. **Visibility gate (R1).** `Song.id` is the server item id. The helper added to
   `AbsPlayerFragment` sets the menu item's visibility from
   `MusicPlayerRemote.getCurrentSong()` — present iff the current song has a non-blank id and
   a service connection exists (the no-connection clause is the no-crash guard R1 states). Card and Flat each call it right after `inflateMenu`; keeping
   the logic in the shared base is what makes both themes behave identically. The item is
   *not* gated on permission (operator decision — 401 is handled where it lands).
2. **Dialog first, network after (R2).** The handler branch only shows
   `DeleteFromLibraryDialog`; every network call lives behind its positive button in
   `LibraryDeleteUtil`. That ordering is what the wiring source test pins (handler must not
   mention `DeleteItem` directly) and what the instrumented cancel test observes.
3. **Queue mechanics for remove-current-and-continue (R4).** `QueueManager.removeSong` re-bases
   the index for removals *before* the current one but has no current-entry semantics. Decision:
   on success capture `pos = MusicPlayerRemote.getCurrentQueuePosition()`-equivalent, call
   `removeFromQueue(pos)`; if a next entry now exists at `pos` (or the re-based queue is
   non-empty), start it via the existing queue-open/play path at that index; if the queue became
   empty, stop playback and let the existing meta-changed broadcast clear the now-playing
   surface. Shuffle order and sleep timer ride whatever the queue object already carries —
   the feature must not rebuild the queue behind the user's back beyond dropping the dead
   entry. The Builder may choose `removeFromQueue` + play-at-index or reopen-trimmed-queue;
   the *observable behavior* of R4 is the contract, not the call sequence.
4. **Pure outcome seam (R3, R5-R8).** `LibraryDeleteUtil` exposes a static, Android-free
   mapping from request result to outcome — `SUCCESS` (2xx), `PERMISSION_DENIED` (401),
   `ALREADY_GONE` (404), `NETWORK_FAILURE` (transport/null status), `SERVER_FAILURE` (other
   statuses) — and a static in-flight latch keyed by item id (second activation while armed is
   ignored: R8). The JVM test drives *these*, because forcing a real 401/404 from this server
   is impossible (both accounts are admin — see the probe) and Espresso cannot fabricate a
   transport error on demand. UI code only turns outcomes into toasts and state changes.
5. **404 is a "quiet success" minus the claim (R6).** The 404 path runs the same client
   cleanup as success (queue drop, advance, `FavoritesStore.notifyChanged()`) with its own
   toast, because the item really is not on the server anymore; it does not purge the cache
   (the file may still exist on storage — only the library row vanished) and does not clear
   the suppression guard (R10/R11 are success-only, deliberately).
6. **Suppression guard and session reports (R10).** On success: call
   `MusicService.clearServerReportSuppression()` when the guard names the deleted id (else a
   stale guard persists across restarts against a dead id — the defect class the v1.3.13 work
   introduced), and rely on advance-to-next so every subsequent `Sessions/Playing/Progress`
   report names the *new* current item. E2E-5 asserts this against the server's request log
   (temporarily enabled; see `.tests.md` dependencies).
7. **Cache purge (R11).** Add `@Query("DELETE FROM cache WHERE id = :id")` to `CacheDao`;
   purge = delete row + delete the cached file under `PreferenceUtil.getLocationCache()` if
   present. An un-cached delete succeeds with nothing purged (idempotent).
8. **Testability of R5 (decision recorded).** Unit-forced (option (a) of the steer). A
   temporary non-admin QA user (option b) is rejected: creating or deleting a user is a
   server-side change this feature is forbidden from making. Option (c) (no coverage) is
   rejected: the mapping *is* unit-testable honestly. The named limitation stands: E2E proves
   the granted-permission path only; default-deny for non-admins is a fork property evidenced
   by the `UserManager.cs:766` probe, not by any test in this feature.
9. **Version bump.** 26→27 / 1.3.13→1.3.14, matching the repo's release-hygiene convention
   (release notes are cut per version by the operator; no changelog file exists in-repo).
10. **Verification gate note.** `harness.config.yaml` already names
    `./gradlew testDebugUnitTest` / `lintDebug` / `compileDebugKotlin compileDebugJavaWithJavac`
    while `E00-F01` (which formalizes them) is still `pending`. The Builder and Reviewer run
    those scalars as configured; if the Orchestrator adopts a different gate first, this line
    is where the discrepancy shows up.
