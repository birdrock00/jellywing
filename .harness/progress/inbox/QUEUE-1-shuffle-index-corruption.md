# Candidate fix: QueueManager index-based removal corrupts the hidden shuffle list

Recorded 2026-11-12 (UTC) by the Orchestrator, from Reviewer finding **F9** during `E01-F01` round 2.
**Not a board entry yet** — promote it with the lightweight fix lane when it is worth scheduling. It is
deliberately kept out of `E01-F01`, which is scoped to server library management.

## Symptom

`QueueManager.removeSong(position)` (`QueueManager.java:213-219`) with shuffle **off** removes index `p`
from `playingQueue` **and** from `shuffledQueue` — by position, in two lists that are not guaranteed to
be in the same order. When the two lists have diverged, the element evicted from the shuffle list is a
different song from the one the user asked to remove, and the song that was actually removed **stays**
in the shuffle list.

**Corrected 2026-11-12 by the round-3 Reviewer (round-2 wording overstated the trigger).** The two
lists are *not* independently ordered by default: every mutator that keeps them in step
(`setPlayingQueue*`, `addSong(s)`, `clearQueue`, and `removeSong` itself) leaves them index-identical,
and in that state removing index `p` from both removes the **same** song from both — which is correct.
The defect needs a prior divergence, and divergence has two ordinary sources:

- `QueueManager.moveSong(from, to)` (`:233-238`) reorders **only** `getPlayingQueue()`. With shuffle off
  that is `playingQueue`, so one drag-reorder in the playing-queue sheet leaves `shuffledQueue` in the
  old order — and from then on every removal evicts an unrelated shuffle entry. Simulated:
  playing `[a,b,c,d,e,f]` → `moveSong(0,3)` → playing `[b,c,d,a,e,f]`, shuffled unchanged
  `[a,b,c,d,e,f]`; `removeSong(1)` then drops `c` from the visible queue and `b` from the hidden one.
- `QueueManager.restoreQueue()` loads the two lists from **separate** DB reads
  (`getQueue(0)`, `getQueue(1)`) with no re-derivation, so the persisted pair is whatever the last
  session left behind.

## Evidence

Reproduced in simulation by the Reviewer (round 2, re-run and extended in round 3, both the diverged
and the non-diverged case). The **shipped** Remove-from-queue UI path behaves identically —
`PlayingQueueAdapter` → `MusicPlayerRemote.removeFromQueue(queuePosition)` → `QueueManager.removeSong` —
so this predates the delete-from-library feature and is not caused by it.

**Why it is latent, precisely** (this is the sentence that justifies not scheduling it, so it is stated
as a mechanism rather than a hunch): while shuffle is off, `shuffledQueue` is not the visible list and
nothing reads it — `getPlayingQueue()` (`:50-52`) returns `playingQueue`, `getSongAt`/`getCurrentSong`
route through it, and the only other mention of the field anywhere in `app/src/main` is the persistence
call `QueueSongDao.updateQueues(playingQueue, shuffledQueue)` — and **both** transitions into a state where it is read
re-derive it: `setShuffleMode(SHUFFLE)` calls `shuffleQueue()` (`:128-134`, `:162-179`) which overwrites
`shuffledQueue` from `playingQueue`, and `setShuffleMode(NONE)` assigns
`shuffledQueue = new ArrayList<>(playingQueue)` (`:150`). The shuffle-mode preference is only ever set by
`setShuffleMode`, so there is no path that makes the corrupted list visible: a process kill leaves the
preference at NONE, and the next toggle-on re-derives. The damage is therefore **silent data corruption
of the persisted hidden queue rows** (`saveQueue` persists both lists via `updateQueues`), not a
user-visible wrong-song-gone today.

The latency argument rests entirely on that re-derivation accident. Nothing tests it, and the same family
already has a visible sibling: `moveSong` performed while shuffle is **on** reorders only `shuffledQueue`,
and `setShuffleMode(NONE)` (`:150`) then overwrites it from the stale `playingQueue`, so a drag-reorder
the user made while shuffling is silently discarded when shuffle is switched off. Whether that second
behavior is intended is a product question, not part of this finding; it is recorded so whoever takes the
ticket sees the whole two-list-copy shape rather than only the removal half.

## Why it matters more now

`E01-F01`'s duplicate-queue handling (`fe0d190a`, Reviewer `M-3`) removes **every** queue entry naming a
deleted item, so it calls this method N times in one user action. Same latent defect, more corrupted rows
per action once the lists have diverged. Nothing in the current suite covers it either way
(`QueueManagerRepeatModeTest` covers repeat mode only; no test in the repo exercises `removeSong`, in
either shuffle mode).

## Shape of a fix (not a decision)

Remove from the shuffle list by identity, not by positional index — or stop touching `shuffledQueue`
here entirely and let the existing re-derivation on toggle handle it. Either way the invariant to pin is:
removing an item from the visible queue never removes a *different* item from the shuffle list.

Suggested lane: a non-spec maintenance fix, with a JVM test over the two-list state before and after a
removal in both shuffle-on and shuffle-off modes. No server involvement.

## Constraint on whoever takes it

`QueueManager` and the queue/playing state are shared by every playback surface. Keep it to the
identity-vs-position question; do not opportunistically refactor queue ordering.
