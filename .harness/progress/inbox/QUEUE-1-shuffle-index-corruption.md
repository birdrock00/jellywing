# Candidate fix: QueueManager index-based removal corrupts the hidden shuffle list

Recorded 2026-11-12 (UTC) by the Orchestrator, from Reviewer finding **F9** during `E01-F01` round 2.
**Not a board entry yet** — promote it with the lightweight fix lane when it is worth scheduling. It is
deliberately kept out of `E01-F01`, which is scoped to server library management.

## Symptom

`QueueManager.removeSong(position)` with shuffle **off** removes index `p` from `playingQueue` **and**
from `shuffledQueue`. The two lists are independently ordered, so the element removed from the shuffle
list is unrelated to the one the user asked to remove. Removing N entries evicts N unrelated entries
from the hidden shuffle list.

## Evidence

Reproduced in simulation by the Reviewer. The **shipped** Remove-from-queue UI path behaves
identically, so this predates the delete-from-library feature and is not caused by it. It stays mostly
latent because `shuffleQueue()` re-derives the list whenever shuffle is toggled, which silently repairs
the damage.

## Why it matters more now

`E01-F01`'s duplicate-queue handling (`fe0d190a`, Reviewer `M-3`) removes **every** queue entry naming a
deleted item, so it calls this method N times in one user action. Same latent defect, more frequent
reach. Nothing in the current suite covers it either way.

## Shape of a fix (not a decision)

Remove from the shuffle list by identity, not by positional index — or stop touching `shuffledQueue`
here entirely and let the existing re-derivation on toggle handle it. Either way the invariant to pin is:
removing an item from the visible queue never removes a *different* item from the shuffle list.

Suggested lane: a non-spec maintenance fix, with a JVM test over the two-list state before and after a
removal in both shuffle-on and shuffle-off modes. No server involvement.

## Constraint on whoever takes it

`QueueManager` and the queue/playing state are shared by every playback surface. Keep it to the
identity-vs-position question; do not opportunistically refactor queue ordering.
