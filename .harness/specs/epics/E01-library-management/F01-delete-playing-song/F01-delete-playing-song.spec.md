---
id: E01-F01
title: "Delete the playing song from the Jellyfin library (and every playlist holding it)"
epic: E01-library-management
status: in-progress        # pending → spec-ready → in-progress → in-review → done
sdd: true
autonomous: false        # workflow.require_spec_approval: true — the human gate applies
complexity: complex      # basis stated in Context (E17-F03)
depends_on: []
owner: birdrock00
---

# Delete the playing song from the Jellyfin library — Functional Spec

## Context

The listener (the device owner; Jellyfin fork v12.4 server) is playing a song and decides it
should not be in their library at all — a duplicate, a bad-import track, something that slipped
into a playlist. Today jellywing has no way to retire an item: they must stop the music and
delete it in the Jellyfin web UI or on the NAS. The player's overflow menu
(`app/src/main/res/menu/menu_player.xml`, inflated by both player themes and handled in
`AbsPlayerFragment.onMenuItemClick`) offers favorite / clear queue / save queue / sleep timer,
and nothing that removes the thing they are hearing.

This feature adds a **Delete from library** overflow item, a destructive-action confirmation
dialog, one stock `DELETE {server}/Items/{itemId}` call via the existing apiclient
(`ApiClient.DeleteItem(String, EmptyResponse)` — already used in-repo by
`PlaylistUtil.deletePlaylist`), and the client-side aftermath: the deleted entry leaves the
queue, playback advances, stale surfaces refresh, and each failure class (401 permission, 404
already-gone, transport/server) is named to the user instead of falsely claiming success.
Playlist cleanup itself is **server-side and automatic** on this fork (verified cascade — see
the `.plan.md` evidence section); E2E asserts it rather than the client implementing it.

**Complexity basis (E17-F03).** `complex`. The decomposition pressed the budget — it lands at
12 of the 12 allowed `R-id`s without splitting — and speccing it required resolving a
contradiction between two existing components: the research handover called the endpoint
admin-policy-gated with a 403 refusal, while the v12.4 source shows plain `[Authorize]` with an
item-level gate that refuses with **401** (plan evidence). The queue also has no
remove-current-and-continue primitive, so the playback-after-delete mechanics are a design
decision, not a reuse.

**P1 reconciliation (stated, for the human gate).** `specs/product.md` principle P1 reads
"the Jellyfin server's library is read-only; every feature-visible mutation lands in the
client's own local store" and lists server-side playlist entities among what jellywing
"never … deletes". This feature is the first to delete server-side content, and it does so
under the operator's restated reading of P1, which epic E01 and the intake brief record as
binding: *the server as a system is never modified (no source, DB, config, container or image
changes), while stock API calls the signed-in user explicitly triggers against their own
library are permitted.* The literal P1 wording predates this epic; the Orchestrator's task
brief states the reconciled reading is authoritative. Recorded here so the human gate approves
the reading along with the spec, rather than the spec silently redefining a product principle.

## Business rules

- **Hard delete, stated plainly.** The stock endpoint destroys the media file on the server's
  storage as well as the library row (`DeleteOptions { DeleteFileLocation = true }`, verified —
  plan evidence), and there is no server-side trash. The confirm dialog must say in plain words
  that the file is removed from the server and from every playlist holding it and cannot be
  undone from the app. There is no undo, by design (epic-level decision).
- **Client-side only.** The feature ships zero server changes: no server source, database,
  configuration, container or image change, and no server-side permission provisioning. The only
  server interaction is the stock `DELETE /Items/{itemId}` call the user confirms, plus the
  existing auth/session calls. (Operator-stated, applies to every role on this feature.)
- **Permission is not probed client-side.** The operator's chosen default: the menu item is
  always offered when a real server-backed song is current; a refusal comes back as a clear
  toast. Pre-gating on `UserDto.getPolicy()` would cost an extra API call per surface refresh
  and is out of scope.
- **The playback must not die.** The deleted entry leaves the queue and playback advances to the
  next queued track; a lone deleted entry stops playback cleanly rather than leaving the
  mini-player pointing at a dead item.
- **Nothing is reported as deleted that was not deleted.** Success copy appears only on the
  success path; 401, 404 and transport failures each name what happened.
- **QA destroys a real file, so QA restores it.** Every destructive test run follows the
  backup → delete → assert → restore → re-add protocol in `.tests.md`, on the emulator, with
  the server as second oracle, and leaves the library as found.
- **No PII or credentials** in any committed file of this feature: no hostnames, user ids,
  device identifiers, tokens or passwords — the QA driver takes them from the operator's
  runtime procedure at run time, never from the repository.

## Architecture alignment

> **Not required on this spec** — graceful degradation (`agents/architect.md`), recorded so the
> omission is deliberate rather than accidental.
>
> Verified absent 2026-11-12: `.harness/specs/architecture.md` does not exist and no `adr/`
> directory exists anywhere under `.harness/specs/` (`find .harness/specs -name adr` prints
> nothing; the only hit for `architecture.md` is the untouched template stub
> `specs/_templates/architecture.md`). This repository has never run `/sdd-plan`, so there is
> no recorded decision to honor and no citation is fabricated. The binding constraints for this
> feature are product principle P1 (see Context for the reconciliation) and the epic's
> server-read-only operating rule.

## Acceptance criteria (EARS)

> Each is one testable behavior with a stable id. Test mapping in `.tests.md`; the 401 clause
> carries the named testability limitation recorded under *Testability limitations* below.

- **R1** — When the player's overflow menu is opened, the system shall show a
  **Delete from library** item if the current queue entry carries a non-blank server item id
  and the playback service is connected, and shall not show it when no song is current, the
  playback service is not connected, or the current entry has no server item id.
- **R2** — When the user selects **Delete from library**, the system shall show a confirmation
  dialog *before issuing any network request*, stating that the file is removed from the
  server and from every playlist holding it and that the deletion cannot be undone; and when
  the user cancels, the system shall issue no request and leave queue, library and playback
  unchanged.
- **R3** — When the user confirms, the system shall issue exactly one
  `DELETE {server}/Items/{itemId}` request for the current song's server item id, using the
  existing apiclient call `ApiClient.DeleteItem(String, EmptyResponse)` — no new HTTP plumbing.
- **R4** — When the delete request completes successfully, the system shall show a success
  toast, remove the deleted entry from the playing queue, and advance playback to the next
  queued track; where the deleted entry was the only entry, the system shall stop playback and
  leave the now-playing surface not presenting the deleted item.
- **R5** — If the delete request fails with HTTP 401, then the system shall show a toast naming
  a server-permission refusal (media deletion is not enabled for this user; ask a server
  administrator) and shall not claim deletion, change the queue, or change library state.
- **R6** — If the delete request fails with HTTP 404, then the system shall show a toast
  distinct from the success toast naming that the item is no longer on the server, and shall
  still perform the client-side queue and refresh cleanup described in R4 and R9 without
  claiming this action deleted anything.
- **R7** — If the delete request fails with any transport error or any status other than 401
  or 404, then the system shall show a toast naming a server/network failure and shall not
  claim deletion, change the queue, or change library state.
- **R8** — While a delete request is in flight, the system shall ignore further activations of
  the delete action and issue no second request.
- **R9** — When a delete completes via the success path or the 404 path, the system shall call
  `FavoritesStore.notifyChanged()` so the Favorites tab refetches without user action.
- **R10** — When a delete succeeds, the system shall clear the favorite-report suppression
  guard (`MusicService.clearServerReportSuppression()`) if it is armed for the deleted item id,
  and shall issue no subsequent playback-session progress report naming the deleted item id.
- **R11** — When a delete succeeds and the deleted item has a local cache entry, the system
  shall remove the item's Room `cache` row and its cached file.
- **R12** — The user-visible strings this feature adds shall exist only in the default English
  `app/src/main/res/values/strings.xml`; the system shall add no entries to the localized
  `values-*` directories (the 37 existing locale dirs fall back to English).
