---
status: ready
# stage: discovery | pilot | production — absent means production (ADR-0009)
stage: production
---

# Jellywing — Product Constitution

> Layer 0. The stable, high-level "what & why" for Jellywing. Detail lives in
> feature specs; contradictions here win until a human rewrites this page.

## What this product is
Jellywing is a native Android music player for Jellyfin media servers
(application id `club.thatpetbff.gramophone`, licensed GPLv3), based on Gelli,
itself based on Phonograph. It browses a Jellyfin server's library —
artists, albums, songs and genres — with search, favorites and playlists,
playback-history reporting, gapless playback through Media3 ExoPlayer,
per-song and batch caching, filtering by library (category) and audio
language, and a media-session notification. It ships as version 1.3.13
(`versionCode 26`, `minSdk 23`, `targetSdk 36`) through F-Droid and GitHub
Actions APK builds.

## Who it is for
A person who self-hosts — or is invited to — a Jellyfin server carrying
their own music, and wants a proper native Android player for it on a phone
or tablet: no subscription, no Google services, and the freedom to read and
change the source. Today that is largely the author; contributions are
welcomed, on issues first.

## Principles & hard constraints
- **P1 — the Jellyfin server's library is read-only; every feature-visible
  mutation lands in the client's own local store.** Jellywing never creates,
  updates, or deletes library content, images, or server-side playlist
  entities on someone else's server (favorites, playlists, queue, up-next,
  sorting and cache are all client-side). Reading server data and reporting
  playback status/usage are not library mutations and remain allowed.
- **GPLv3 stays.** The license stays GPLv3; no dependency or code import that
  is incompatible with it.
- **Self-hosted and credential-local.** Server URLs, users and access tokens
  stay on the device; no telemetry, no accounts, no third-party server, no
  secrets in the repository.
- **No PII or credentials in the repository** (the committed `AGENTS.md`
  rule): device identifiers, hostnames, usernames, cluster/context names and
  vault key names stay out of tracked files and out of commits.
- **Don't break the fork's upgrade path.** This tree carries a renamed fork
  of Gelli; mass-reformatting or wholesale rewrites of inherited files raise
  the cost of taking upstream fixes.
- **Known open defects, stated not fixed** (snapshot of `README.md` as of
  2026-11-12; date-stamp any edit to this bullet): artist sorting is not
  available through the API, and playlists/favorites do not refresh
  automatically when changed elsewhere.
