# Builder run notes — E01-F01 round 3 (Reviewer conditions C-A.1 … C-A.5)

Date: 2026-11-12 (UTC). Builder, round 3 after **APPROVE WITH CONDITIONS**
(`progress/20261112-E01-F01/review-round2.md` §1–§4, §7). Scope: `app/**` only. No `.harness/specs/`
edit, no task-box tick, no `git add -A`, no version bump (`versionCode 27` / `1.3.14` unchanged).
No destructive delete, no P0→P8, no credential/session read, no `adb reboot`, no AGP
`connectedAndroidTest` (device work was `install -r -d` + `am instrument -w -e class` only).
C-A.6 (`waive-test-drift:` lines in `.tasks.md`) left to the Architect as instructed.

## Commits — one per pair, in order

| Commit | Condition | Files |
|---|---|---|
| `e88b33fa` | **C-A.1 / F1** — removal ORDER pinned in a pure helper | `LibraryDeleteUtil` + `LibraryDeleteDuplicateQueueTest` + `DeleteFromLibraryWiringSourceTest` |
| `17d91a00` | **C-A.2 / F7** — pre-request region forbids every success-side effect | `DeleteFromLibraryWiringSourceTest` |
| `7a3743c8` | **C-A.3 / F3** — absent-songs-row purge pinned on device | `CachePurgeOnDeleteInstrumentedTest` |
| `8e1ff1ee` | **C-A.4 / F4** — R11 device class self-restoring | `CachePurgeOnDeleteInstrumentedTest` |
| `10e1e297` | **C-A.5 / F5 + R2-B1** — shared `KEY_FRAGMENT`, exact `setVisible` expression | `DeleteFromLibraryWiringSourceTest` |

`git diff --stat 4b9498d6..HEAD` = 4 files, all under `app/` (242 insertions, 32 deletions).

## Gates

| Check | Result |
|---|---|
| `./.harness/init.sh` | **0** (same pre-existing warn-only escalation note as round 2) |
| `./gradlew testDebugUnitTest` | **0** — **150** tests, 0 failures, 1 pre-existing skip (round-2 baseline 148 + `testRemovalOrderIsHighestIndexFirst` + `testBothStringKeyCallSitesShareTheOneFragment`) |
| `./gradlew compileDebugKotlin compileDebugJavaWithJavac` | **0** |
| `./gradlew assembleAndroidTest` | **0** |
| `am instrument … CachePurgeOnDeleteInstrumentedTest` (final, committed tree, `install -r -d`) | **OK (3 tests)**, twice; logcat per-test `started:`/`finished:` for all three legs in both runs — no `assumption failed`, nothing self-skipped |
| PII screen on `git diff 4b9498d6..HEAD` + all five commit messages | clean; one regex hit is the word "token" in prose about assertions |
| `git status -- app/` after every mutant | clean (`cp` restore, verified each time) |

## Observed RED per pair, and the mutant each new assertion kills

### C-A.1 / F1 — removal order

RED first, for the right reason: the helper was extracted **as the Reviewer's defect** (identity →
ascending), so the new assertion failed with the actual bug rather than a compile error.

```
LibraryDeleteDuplicateQueueTest > testRemovalOrderIsHighestIndexFirst FAILED
  java.lang.AssertionError: two dead entries must be removed highest-index-first — an ascending pass
  makes the second index out of range and the guard silently skips it, leaving a dead entry in the
  queue (R4) expected:<[3, 1]> but was:<[1, 3]>
```

Then GREEN (149/0/1) with `Collections.reverse`. Three mutants re-run against the committed pair:

| Mutant | Result | Killed by |
|---|---|---|
| Reviewer's literal: inline `for (i = 0; i < positions.size(); i++) removeFromQueue(positions.get(i))` (GREEN all of round 2) | **RED** | `testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries` — "the removal loop must not walk the ascending lookup directly: … `0 <= p < size()` guard silently refuses it, and a dead entry stays queued with the suite green (R4)" |
| `removalOrder` returns ascending | **RED** | `testRemovalOrderIsHighestIndexFirst` (message above) |
| remove only the first entry (`removeFromQueue(first)`) | **RED** | same wiring test — "the removal loop must consume the list removalOrder() returns" |

`removeFromQueueAndAdvance` now calls the helper (`List<Integer> order = removalOrder(positions)`),
so the asserted order is the executed order.

### C-A.2 / F7 — pre-request region

Round-2 defect reproduced first, with the **unmodified** test file: `toast(R.string.delete_from_library_success)`
inserted before `DeleteItem(` → `BUILD SUCCESSFUL`, 15 tests / 0 failures. So the survivor was real.

After widening the region to forbid `delete_from_library_success`, `removeFromQueueAndAdvance(`,
`purgeCacheEntry(`, `FavoritesStore.notifyChanged(`, `clearSuppressionIfOurs(`, each of the five was
inserted before the request and each run was **RED in isolation**
(`--tests '*…testInFlightGuardWrapsRequestAndReleasesOnOutcome'`, so the kill is the assertion, not a
crash):

```
no success-side effect may run before the request is issued (R3/R4: …); found: delete_from_library_success
no success-side effect may run before the request is issued (R3/R4: …); found: removeFromQueueAndAdvance(
… found: purgeCacheEntry(         … found: FavoritesStore.notifyChanged(      … found: clearSuppressionIfOurs(
```

### C-A.3 / F3 — absent-songs-row purge (device)

Reviewer mutant `if (song == null) return;` placed before `deleteFromCache(itemId)`, built, installed,
run on the emulator:

```
Error in purgeRemovesCacheRowWhenTheSongsRowIsAbsent:
java.lang.AssertionError: a missing songs row must not skip the Room cache row — the cache row delete
is unconditional in purgeCacheEntry (R11)   (CachePurgeOnDeleteInstrumentedTest.java:92)
Tests run: 3,  Failures: 1
```

Restore → `OK (3 tests)`. The leg asserts its own precondition (`getSong(id) == null`) so it cannot
silently degrade into the covered case.

**Deviation, stated:** the brief said "delete the matching `songs` row". `SongDao` exposes only
`deleteSongs()` = `DELETE FROM songs`, a whole-table truncate that would wipe the emulator's real
indexed library — forbidden here. The row is therefore **never created**, which produces the identical
observable state (cache row present, songs row absent); the precondition is asserted, not assumed. No
production code changed (it was already correct — this pair added only the missing evidence).

### C-A.4 / F4 — self-restoring R11 class

Trap reproduced on the pre-fix class with the `getFileUri(song)` delete removed from `purgeCacheEntry`:

```
=== RUN 1: java.lang.AssertionError: downloaded audio file must be gone (R11)      Tests run: 3, Failures: 1
=== RUN 2: java.lang.AssertionError: fixture file must be creatable: 0.0 - QA Purge Fixture.m4a
```

— exactly the Reviewer's hazard: the second run fails on the fixture, not on R11.

After `reclaimStaleFixture()` before every `createNewFile()` and `restoreFixtureState()` in `finally`
(all three paths + `cache` row + the fixture's own `songs` row, deleted **by its run-unique id** via
`getWritableDatabase().delete("songs","id = ?"…)` because `SongDao.deleteSongs()` truncates), same
broken purge:

```
=== RUN 1: downloaded audio file must be gone (R11)   Tests run: 3, Failures: 1
=== RUN 2: downloaded audio file must be gone (R11)   Tests run: 3, Failures: 1     ← acceptance met
```

Purge restored: `OK (3 tests)` twice, and `find cache -type f -name 'qa-*'` inside the debug app's
own data dir returns **0** afterwards — the class now leaves nothing behind.

*Device cleanup:* 27 stale `qa-purge-*` / `qa-keep-*` fixture files left by pre-fix runs (this
campaign's broken-purge runs and earlier ones) were removed from the debug app's private cache dir,
plus the two empty `cache/music/QA Fixture` dirs. Library/server data untouched.

### C-A.5 / F5 + R2-B1 — two vacuity holes

Both survivors reproduced **GREEN** against the pre-fix test file first: locale loop pointed at
`"delete_from_library_ZZZ"` → 15 tests / 0 failures; `setVisible(serverBacked || true)` → 15 / 0.

Fix: one `private static final String KEY_FRAGMENT = "delete_from_library"` used by
`deleteFromLibraryKeys()` **and** the locale scan; `assertFalse(deleteFromLibraryKeys().isEmpty())`
inside `testNewStringsLiveInDefaultEnglishOnly`; plus one extra pin the condition did not ask for but
implies — `testBothStringKeyCallSitesShareTheOneFragment` allows exactly **one** quoted fragment
literal in the file (the declaration), so re-splitting the two call sites cannot come back quietly.

| Mutant | Result | Killed by |
|---|---|---|
| `KEY_FRAGMENT` matches nothing | **RED** | `testDeleteStringKeyListIsDerivedNotEmpty` *and* `testNewStringsLiveInDefaultEnglishOnly` ("the locale rule is vacuous if the delete_from_library_ZZZ key list is empty") |
| locale call site re-split to its own literal | **RED** | `testBothStringKeyCallSitesShareTheOneFragment` (`expected:<1> but was:<2>`) |
| `deleteItem.setVisible(serverBacked \|\| true)` (round-2 survivor R2-B1) | **RED** | `testVisibilityGateRequiresAServerBackedSongId` — "the gate must pass exactly the server-backed flag to setVisible — a term that ORes anything onto it … shows Delete-from-library for local-only songs and the server refuses them (R1, R2-B1)" |

## Settled items touched by nothing here (review §8)

Single stock `DeleteItem` with zero retries and no non-GET caching; main-loop callback delivery so the
toast/queue/Room work in `handleOutcome` is legal; the lone-entry `clearQueue()` path; the R11 class
asserting real file removal at three paths. None was "fixed"; the only production change this round is
the `removalOrder` extraction, which is behavior-preserving (the loop was already highest-index-first).

## F9 — recorded, not fixed

`QueueManager.removeSong(p)` with shuffle **off** removes index `p` from `playingQueue` *and* from the
independently-ordered `shuffledQueue`, so removing N entries evicts N unrelated entries from the hidden
shuffle list. Pre-existing (the shipped Remove-from-queue UI reproduces it), mostly latent because
`shuffleQueue()` re-derives on toggle, and outside F01's change set. **The relevant F01 fact: this
feature now calls `removeSong` once per queue entry naming the deleted item (N calls where the old code
made 1), which raises the visibility of that existing defect.** Per the brief, recording only — the
Orchestrator has filed the follow-up (`progress/inbox/QUEUE-1-shuffle-index-corruption.md`).

## Residual bounds (what green does NOT prove)

1. **C-A.1's loop-side pin is textual.** The helper's *output* order is executed-and-asserted; that the
   removal loop consumes it forward is pinned by three source assertions. A double inversion
   (`removalOrder` returns descending **and** the loop walks it in reverse) would survive the JVM suite.
   Real removal-on-a-live-queue remains a device/E2E property — QA's C-B leg.
2. **R1's gate pin stays textual** even with `setVisible(serverBacked)`: an always-visible
   implementation restructured to keep that exact expression (e.g. `boolean v = serverBacked || cond;
   setVisible(v)`) survives. The executed device leg (`DeleteMenuItemVisibilityTest`) is still the real
   R1 proof and is still in C-B, still self-skipping without a live session (F6).
3. **The R11 class is pinned on device only** (G1 unchanged): the JVM gate still asserts cost+logging
   for the purge, not file removal. F2's `.tests.md` wording and C-B's must-run list are the Architect's
   / QA's pen.
4. **`.tests.md` drift introduced by this round (for the Architect):** the R11 instrumented class is now
   **3** legs (added `purgeRemovesCacheRowWhenTheSongsRowIsAbsent`) and is self-restoring; the queue
   helper row should name `removalOrder`; `testBothStringKeyCallSitesShareTheOneFragment` is a new test;
   and the round-2 assertion in `testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries`
   (`removeFromQueue(positions.get(`) was **replaced by tighter assertions**, as was nothing else —
   pair-commit drift runs may report those as changed/removed assertions. That is tightening demanded by
   C-A.1, and it belongs to C-A.6's `waive-test-drift:` lines, which are not mine to write.
5. `scratchpad/E01-F01-builder3/` holds the mutation runner, backups and diffs; left in place per
   harness policy (never self-removed). Note again `scratchpad/` is untracked and not gitignored — a
   `git add -A` on this repo would commit campaign scratch. Not fixed here (out of ownership).

## Disclosure

One careless command of mine — `git checkout --` with **no pathspec** — was executed during the pair-5
mutant loop. It was a no-op on this repo state (verified immediately: `git status -- app/` showed only
the intended mutant, all pair-5 work intact, and the pair committed clean), but it is exactly the
revert path the harness forbids; I had already taken `cp` backups and used them for every restore, and
I removed the pattern from the rest of the run. Nothing in this file rests on state that command could
have destroyed.
