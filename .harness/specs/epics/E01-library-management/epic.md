---
id: E01
title: Server library management from jellywing
status: pending          # draft → planned → in-progress → done (pending = legacy alias of planned; rollup of its features)
---

# Epic E01 — Server library management from jellywing

## Business brief

jellywing can browse, play, favorite, cache and playlist items from a Jellyfin server, but it
cannot retire anything. Once a song is in the library the only ways to get rid of it are outside
the app: the Jellyfin web UI, the server database, or the NAS. A user who is listening to a track
and decides it should not be in their library at all — a duplicate, a wrong-tagged file, a song
that slipped into a playlist from a bad import — has to stop the music, find the item somewhere
else, and delete it there.

This epic delivers destructive library management **from inside the player**: the user acts on the
item they are currently hearing, and the server — and every playlist holding that item — reflects
it immediately, with no round trip and no server-side change. The epic is deliberately narrow. It
is about removing an item the user owns and can see, at the point of listening, with the
destruction made legible before it happens.

## Success criteria (epic level)

- A listener can retire the song they are currently hearing from the player in one deliberate,
  confirmed action, without leaving the app or opening the Jellyfin web UI.
- After a successful retirement the item is gone from everywhere the app shows the server: Songs,
  Albums, Artists, Search, Favorites, the mini-player, and every playlist that held it.
- Nothing is reported as deleted that was not deleted. Where the server refuses (the logged-in user
  may not delete) or the item is already gone, the user is told which of those happened.
- The action is irreversible by design, and the app never asks the user to guess that: the
  consequence is stated before it is carried out.
- All of it is client-side. The Jellyfin server — its project, its database, its configuration, its
  image — is never modified by this epic, and behavior relies only on stock, documented endpoints.

## Features

| id | title | status | sdd | depends_on |
|---|---|---|---|---|
| F01 | Delete the playing song from the Jellyfin library (and every playlist holding it) | pending | true | — |

## Notes

- Sequencing: F01 is the whole epic for now. Deliberately **not** in this epic, until someone asks
  for them as their own features: bulk/multi-select deletion, folder- or library-scoped deletion,
  "remove from this playlist only" from the player, and any restore or undo capability.
- Undo is out of scope for the epic, not by oversight: the stock delete endpoint destroys the media
  file as well as the library row, and there is no server-side trash the client can restore from
  without changing the server.
- Hard operating constraint for every feature here (operator-stated, restated in each brief): the
  Jellyfin server is read-only. No change to server source, database rows, configuration, or
  running images. Reading server state and calling stock endpoints from the app is in scope;
  changing what the server does is not.
- `specs/product.md` is still the seeded template — the product constitution is not written yet, so
  epic-level language here is provisional and the Architect should reconcile it with product.md if
  that gets drafted first.
