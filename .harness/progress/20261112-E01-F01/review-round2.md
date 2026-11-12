# Review — E01-F01 (delete the playing song from the library), round 2

Reviewer, 2026-11-12 (UTC). App diff re-reviewed: `git diff 8162e3c6..HEAD` (9 pair commits);
frozen-artifact fix `4739daa4`. Round 1 (`review.md`) was REJECT.
No commits, no push, no PR, no TaskStore/status write, no destructive delete, no credential read.
Working tree after the campaign: `git diff --stat -- app/` empty; only this untracked file and the
reviewer scratch directory.

## Verdict: APPROVE WITH CONDITIONS

Conditions are in the last section, as two executable checklists: **C-A** (Builder, before the PR)
and **C-B** (QA, before the version bump ships). C-B is destructive and is a hard shipping gate.

## 1. The round-1 GREEN set: 7 / 7 now RED, and they die for the right reason

Each mutant applied alone, restored from a `cp` backup, `git diff --stat -- app/` verified empty
after every one. 148 tests / 0 failures / 1 pre-existing skip on every green baseline sample.

| # | Round-1 survivor (mutation) | Now | Killed by — and the assertion that fired |
|---|---|---|---|
| 1 | **B2** `deleteFromLibrary(...)` moved from `onPositive` into `DeleteFromLibraryDialog.onCreateDialog` | **RED** | `DeleteFromLibraryDialogSourceTest::testConstructingTheDialogIssuesNoRequest` — "R3: building the confirm dialog must not delete anything — the request belongs to the positive button", plus `testDeleteCallSitsInsideOnPositive` — "the positive button must issue the library delete" |
| 2 | **B3** `delete_from_library_body` → "Remove this song?" | **RED** | `testConfirmCopyCarriesTheThreeWarnings` — "confirm copy must name the server as the place the file leaves (R2): remove this song?" (content, not key existence) |
| 3 | **B4** `FavoritesStore.notifyChanged()` removed from the `SUCCESS` branch only | **RED** | `testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` — "SUCCESS must refresh Favorites without user action (R9)", read from the `SUCCESS` block |
| 4 | **M-1** `try/catch` removed so a synchronous throw escapes and wedges the latch | **RED** | `LibraryDeleteSyncThrowLatchTest` ×2 — "a synchronous throw must release the R8 latch …" and "after two contained activations the latch must be free (R8)" |
| 5 | **M-4** untracked `delete_from_library_undo` added to `values-de` | **RED** | `testNewStringsLiveInDefaultEnglishOnly` — "values-de must not translate any delete_from_library* string …; found 1: [delete_from_library_undo]" |
| 6 | **M-5** `applyDeleteFromLibraryVisibility` forced to always show | **RED** | `testVisibilityGateRequiresAServerBackedSongId` — "the gate must read the current song (R1)" |
| 6b | **M-5** variant: gate checks `song != null` only, no server id | **RED** | same test — "the gate must require a server id, not merely a non-null song (R1)" |
| 6c | **M-5** variant: id read but blank accepted | **RED** | same test — "a blank/whitespace id must not count as server-backed (R1)" |
| 7 | **Optimistic success**: `SUCCESS` dispatched before the request | **RED** | `testInFlightGuardWrapsRequestAndReleasesOnOutcome` — "no outcome may be dispatched before the request is issued (R3/R4…)" — see §2 |

**Extras that were also GREEN in round 1 and are now RED:** the sneaky B2 variant (call left in
`onPositive` *and* added to `onCreateDialog` → "no code path other than onPositive may call
deleteFromLibrary"); the lone-entry `clearQueue()` removal (→ "a lone deleted entry must stop
playback rather than sit on a dead item (R4)"); removing only the first queue entry (→ "the helper
must drop EVERY entry naming the deleted item"); dropping the gate call from one player theme
(→ "FlatPlayerFragment.java must refresh the gate when the current song changes (R1)").

**M-1 discriminating pair.** The kill is the *latch*, not merely the crash: with the `catch` kept
and only `endDelete(itemId)` deleted from it, both latch tests still go RED. So the pin is
"every request path leaves the latch terminal", which is the R8 property.

## 2. Wrong-reason adjudication (the Builder's disclosed case, plus B4 and M-5)

**Optimistic success — cleared.** The Builder reported the mutant originally went red only because
dispatching `SUCCESS` on a JVM with no bound service crashes. I re-ran it with **only the contract
test in the run** (`--tests '*DeleteFromLibraryWiringSourceTest.testInFlightGuardWrapsRequestAndReleasesOnOutcome'`):
1 test, 1 failed, message "no outcome may be dispatched before the request is issued (R3/R4: the
success claim must follow the server)", stack frame inside that test. The contract fails on its own;
the crash-driven red in the full run is a second, independent failure. Not a wrong-reason kill.

**B4 — cleared with a discriminator.** Removing `notifyChanged()` from `ALREADY_GONE` only goes RED
in a *different* test (`testAlreadyGoneRunsCleanupWithoutSuccessClaim`, "404 still refreshes
Favorites"), while removing it from `SUCCESS` only goes RED in the SUCCESS-scoped test. Two branches,
two independent kills — the round-1 "the neighbour satisfies it" defect is closed for real.

**M-5 — cleared for realistic mutants, with a stated bound.** Three realistic shapes of the same
bug (always show; non-null song without the id; blank id accepted) each die in the executed gate on
a different clause. Bound: the pin is textual, so a semantically-always-visible gate that keeps
every required token survives — `deleteItem.setVisible(serverBacked || true)` stayed **GREEN**.
The only semantic pin for R1 is the instrumented visibility test, which is not in the gate (§6).
Not a regression from round 1 (which had no executed pin at all); recorded as bound **R2-B1**.

## 3. New round-2 surface (behavior I had not seen before)

### M-3, every-entry queue removal — arithmetic correct, ordering unpinned (F1)

`positionsOfItem` returns ascending indices; `removeFromQueueAndAdvance` removes them highest-first.
I simulated the shipped machinery exactly (`QueueManager.removeSong` index/position rebasing +
`MusicPlayerRemote.removeFromQueue`'s `0 <= p < size()` guard) over 8 queue shapes: twice-queued with
the current entry being the first dead one, the last dead one, or another song; lone entry; all
entries dead; dead at head; dead at tail; absent. Shipped descending order: **every shape ends with
zero dead entries**, the absent case is a clean no-op, and `clearQueue()` fires for both the lone
entry and the all-dead queue — so the R4 clean-stop still triggers when the last entry goes.
`playSongAt(Math.min(first, remaining.size() - 1))` never goes negative (the empty case returns first).
Ascending order is **provably wrong**: the second removal index is out of range once the list shrinks,
the guard silently refuses it, and a dead entry stays — in 6 of 6 multi-entry shapes.

**Answer to the brief's question: no, the round-2 tests would not catch a partial fix that removes
two entries in ascending order.** Mutating the loop to `for (int i = 0; i < positions.size(); i++)`
left the gate **GREEN** (148 / 0). The Builder's RED (`expected:<[1,3]> but was:<[1]>`) came from
`LibraryDeleteDuplicateQueueTest`, which asserts the *lookup* — `positionsOfItem` — and never reaches
the removal loop (that needs a bound service). The source contract checks
`body.contains("removeFromQueue(positions.get(")`, which the ascending loop satisfies verbatim.
So M-3 is half-pinned: "find every entry" yes, "remove them in the order that keeps indices valid" no.

The lookup's ascending order *is* pinned (`found.add(0, i)` → RED on both duplicate-test legs), which
makes the miss more surprising to a reader — the pair looks complete and is not.

### M-2, purge narrowing — verified on device, three gaps in what pins it (F2, F3)

Read: the purge now keys on `songDao().getSong(itemId)` instead of scanning `getSongs()`, and
`cacheDao().deleteFromCache(itemId)` runs **unconditionally**, before the `song != null` guard, so a
missing songs row cannot silently skip the Room row. All three file locations are still there:
`<cacheRoot>/<id>`, `<cacheRoot>/download/<id>`, and `MusicUtil.getFileUri(song)` (the last one
inherently requires the songs row, since the name is derived from it).

Executed, on the emulator, against a build of HEAD (`install -r` + `am instrument`, no AGP
`connectedAndroidTest`, no reboot): `CachePurgeOnDeleteInstrumentedTest` **OK (2 tests)**, both legs
really run (verified per-test in `logcat`, no assumption skip). Then, on device, one mutant at a time:

| Device mutant | Result |
|---|---|
| drop `<cacheRoot>/<id>` + `download/<id>` deletes | **RED** — "cached file at cache root must be gone (R11)" |
| drop only the `download/<id>` delete | **RED** — "in-flight download file must be gone (R11)" |
| drop only the `getFileUri(song)` delete | **RED** — "downloaded audio file must be gone (R11)" |
| gate the Room-row delete on `song != null` | **GREEN** — nothing covers "cache row exists, songs row absent" |

So R11's three file locations are genuinely pinned — **on device only**: the JVM gate stays GREEN
with all three `File.delete()` lines deleted (F2). That is G1's known shape, but the `.tests.md`
R11 row does not say the JVM contract asserts only cost+logging, and the QA round is not currently
told to run this class. And the "missing song" half of the brief's question has no proof anywhere
(F3) — neither the JVM contract nor the instrumented fixture inserts a cache row without a songs row.

**A real defect in the R11 device test, found by this campaign (F4).** The audio fixture path comes
from `MusicUtil.getFileUri(song)`, which is built from artist/album/disc/track/title/container — not
from the unique id — so it is the *same path every run*. When a run's purge is broken, that file
survives, and **every later run then fails at `assertTrue("fixture file must be creatable")` (line 53)
instead of at the R11 assertions.** I hit exactly that: one mutant's red poisoned the next run, and
the suite only went green again after I deleted the leftover file under the app's cache dir. A test
whose failure poisons subsequent runs, with a message that points at the fixture rather than the
requirement, is a hazard for a destructive QA cycle. Fix is one line: remove any stale fixture file
before `createNewFile()` (or derive the title from the unique id), and clean up in `finally`.

### M-4, derived key pattern — anchored enough, not vacuous, one hole (F5)

The contract matches `<string name="…">` and tests `key.contains("delete_from_library")`:
- **`delete_playlist_x` is not swept in.** Verified two ways: the shipped tree already carries a
  translated `delete_playlist_x` in `values-de` and the gate is green, and a control mutant adding an
  unrelated translated key to `values-de` stayed **GREEN** (correct behavior — the rule is about
  delete-from-library strings, not about all delete strings). It is substring, not prefix, so
  `action_delete_from_library` is (correctly) included and any future `*_delete_from_library_*` key is too.
- **Cannot pass vacuously as written.** `testDeleteStringKeyListIsDerivedNotEmpty` asserts the derived
  list contains all nine frozen keys and is at least nine long, and `testNewStringsLiveInDefaultEnglishOnly`
  asserts `localeFilesChecked > 0` (33 locale files today). Mutating the shared fragment so it matches
  nothing → **RED** ("an empty match makes the English-only rule vacuous"). Good.
- **The hole (F5):** the guard covers the *helper* and the default-resources call site, not the locale
  loop's own argument. Changing only the locale scan to a fragment that matches nothing stayed
  **GREEN** — the English-only rule silently stops applying to any key. One-line fix: one shared
  `KEY_FRAGMENT` constant for both call sites, and assert the derived list is non-empty inside the
  locale test itself.

## 4. Findings (all new this round; none is a proven runtime defect)

| id | Sev | Finding | Cheapest fix |
|---|---|---|---|
| **F1** | Major | M-3's **descending** removal order is unpinned: the ascending loop (which provably leaves a dead entry behind — the guard silently refuses the out-of-range index) keeps the gate GREEN. The Builder's RED was the lookup, not the order | extract `removalOrder(ascending)` and assert `[1,3] → [3,1]` in the JVM suite |
| **F2** | Major | The R11 **file removals** have no pin inside `testDebugUnitTest`: deleting all three `File.delete()` lines keeps the gate green. They are pinned only by the instrumented class (which I ran: green) | say so in the R11 row, and put the instrumented class in C-B's must-run list |
| **F3** | Major | "A missing songs row must not skip the Room cache row" is true in code and pinned **nowhere**: gating `deleteFromCache(itemId)` on `song != null` is GREEN in both the JVM gate and the device class | instrumented leg: cache row present, songs row absent → `isCached` false after purge |
| **F4** | Major | `CachePurgeOnDeleteInstrumentedTest` is **not self-restoring**: its audio fixture path comes from `getFileUri(song)` (artist/album/title/container, not the unique id), so a run whose purge is broken leaves the file and every later run fails at "fixture file must be creatable" (line 53) instead of at R11. Hit and confirmed during this campaign | delete stale fixture files before `createNewFile()`; clean up in `finally` |
| **F5** | Minor | R12's English-only rule can go **vacuous on the locale side**: pointing the locale loop at a fragment that matches nothing stays GREEN, because the anti-vacuity floor covers the default-resources call site only | one shared `KEY_FRAGMENT` constant + a non-empty assert inside the locale test |
| **F6** | Major | Instrumented/E2E legs **self-skip and the runner calls it OK**. On this emulator today: R2's `DeleteConfirmDialogTest` → `assumption failed: Need an existing real Jellyfin session`; R1's `deleteItemShownWhenServerBackedSongIsCurrent` → same; `e2e4_cancelNoop` → `E2E-4 needs driver args (qa_server/qa_token/qa_user/qa_item)` — i.e. the one E2E method that exists has never executed, since its args come from the driver that does not exist (G2). `am instrument` prints `OK (2 tests)` and logcat prints `run finished: 2 tests, 0 failed, 0 ignored` either way | the matrix must mark these rows "exists, self-skips without a live session/driver args", and C-B must require per-test executed evidence |
| **F7** | Minor | Optimistic-success is pinned by mechanism name, not by property: a pre-request `toast(delete_from_library_success)` or `removeFromQueueAndAdvance(itemId)` stays GREEN | extend the pre-request region assertion to every success-side effect (C-A.2) |
| **R2-B1** | Bound | R1's gate pin is textual: `setVisible(serverBacked || true)` — always visible, all tokens present — stays GREEN | assert the exact expression `setVisible(serverBacked)` (kills it cheaply), and keep the device leg in C-B |
| **F8** | Major (docs) | `4739daa4` is stale against the round-2 commits it postdates: 7 rows/sections still describe closed round-1 gaps as open work, and none of the 5 new executed test classes is mapped (§5) | Architect: refresh the rows + add the missing rows |
| **F9** | Pre-existing | Out of F01's call-only scope, flagged not blocking: `QueueManager.removeSong(p)` with shuffle **off** removes index `p` from `playingQueue` *and* from the independently-ordered `shuffledQueue`, so removing N entries evicts N unrelated entries from the hidden shuffle list; F01's multi-removal now calls it N times. I reproduced it in simulation and the shipped Remove-from-queue UI reproduces it identically, so it is not an F01 defect (and `shuffleQueue()` re-derives the list on toggle, which is why it is mostly latent) | record as a follow-up item for the queue owner; QA observes, does not "fix" it in F01 |

## 5. Does `4739daa4` make the gap visible without this report? Yes — but it now runs the other way

**The four requirements are plainly marked.** A cold reader finds, in order: the header bullet
("R3, R4, R9 and R10 have no executed proof of the behavior their R-id specifies … What runs today
for those requirements is JVM *source contracts* only"), the `Exists?` column with every unwritten
device case in capital letters, **G1** (the gate is `./gradlew testDebugUnitTest`, so instrumented
rows contribute nothing to green), **G2** (the driver has never been written, so no destructive
cycle can run at all), **G3** (R3/R4/R9/R10 restated because "the matrix shape hides it"), **L5**,
the `.tasks.md` status note naming T4a/T6c/T9a/T10c as the open authoring tasks, and the closing
"Which parts of that sentence are demonstrated today / **Not demonstrated**". Round-1 blocker **B1 is
satisfied**: nobody can mistake the four for covered, and the ticked rows (`T6a`/`T10a`) now say
"unit/instrumented leg only". The Architect's correction of the Orchestrator's "no test of any kind"
is also right, and the file's framing ("executed source contracts exist; the specified behavior is
unproven") is the accurate one.

**But the file now understates, in 7 places** — it was authored after all nine round-2 commits and
never reconciled with them:

| `.tests.md` | Says today | True at HEAD |
|---|---|---|
| L38 (R2) | "NOT YET WRITTEN — the Builder must author it … Exists? **NO**" | Written and RED-pinned: `DeleteFromLibraryDialogSourceTest::testConstructingTheDialogIssuesNoRequest`, `#testDeleteCallSitsInsideOnPositive` |
| L39 (R2) | "NOT YET WRITTEN — the Builder must author the content assertion" | `#testConfirmCopyCarriesTheThreeWarnings` (kills the neutered copy) |
| L42 (R4) | "weak as written … does not pin the success path … Must be scoped to the SUCCESS block", Exists "yes (SUCCESS-scoping pending)" | Scoped; the SUCCESS-only mutant is RED |
| L50 (R9) | "does not pin R9 today … Must be scoped to the SUCCESS block" | Scoped; RED in the SUCCESS test, and RED in the ALREADY_GONE test for its own branch |
| L49 (R8) | "gap: neither covers a *synchronous* throw … leaves the latch armed for the life of the process" | Covered by `LibraryDeleteSyncThrowLatchTest` (2 tests, both RED under the mutant) |
| L56 (R12) | "gap: the key list is a hand-typed literal … derive it from the pattern" | Derived from the pattern, with a non-vacuity floor |
| L66 (G1) | "R1's visibility helper can be forced to always show the item and the gate stays green (Reviewer M-5)" | That mutant is now RED; the surviving variant is the `|| true` shape, not the one named |

**And it overstates in three places**, all the same root cause as F6: the R1 row promises "2 tests:
shown with a server-backed song current, hidden on an empty queue", the R2 row promises the
instrumented dialog + `e2e4_cancelNoop`, and nothing says these legs assume-skip today. On this
emulator only the *hidden* leg executed; the other three skipped and the runner still said OK.

**And it under-maps:** `DeleteFromLibraryDialogSourceTest`, `LibraryDeleteDuplicateQueueTest`,
`LibraryDeleteSyncThrowLatchTest`, `testVisibilityGateRequiresAServerBackedSongId`,
`testBothPlayerThemesApplyTheGateOnInflateAndSongChange`,
`testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries` and
`testDeleteStringKeyListIsDerivedNotEmpty` appear **zero times** in `.tests.md` and `.tasks.md`, so
the very tests that closed B2/B3/B4/M-1/M-4/M-5 are invisible to anyone reading the matrix (that is
how F8 slipped in). Traceability check 2 still passes — every R-id maps to a test that exists and
passes — so this is a stale-matrix defect, not an unmapped requirement.

## 6. Gates

| Check | Result |
|---|---|
| `./.harness/init.sh` | **0** (one pre-existing warn-only escalation note) |
| `./gradlew testDebugUnitTest` (final, after every restore) | **0** — 148 tests, 0 failures, 1 pre-existing skip |
| `./gradlew lintDebug` / `compileDebugKotlin compileDebugJavaWithJavac` | **0** / **0** |
| `tasks-pair-check.py` | **0** — 12 R-ids, 23 tasks. The `--grandfathered` **1** is now moot: that mode only decides a *non-zero* lint, per `agents/reviewer.md` check 7 — B5 closed |
| `plan-cite-check.py` | **0** — 26 rows cited, deferred: none |
| `readiness-check.py` | **0** — 72 references, 9 declared-created |
| `test-drift-check.sh origin/master` | **0 findings**, 11 test files |
| `test-drift-check.sh <pair>` × 9, `--feature-scope origin/master` | **0** at `f68d6eea`/`0a148494`/`fe0d190a`/`4cb17c6a`; **1, 6 findings** at `9561be6b` and `b3fa2137`; **1, 2 findings** at `9fb1f19b`/`e2207e40`/`ef65b0d2`. Read each: all are the *replacement* of the round-1 whole-file R4/R9 assertions and the round-1 R12 key-list assertion by the round-2 scoped/derived versions the Reviewer demanded — tightening, not weakening, and both confirmed RED in §1. Not waived in `.tasks.md`; recorded here as the decision (C-A.6) |
| `change-size.sh` | tier **ok** — 928 production lines / 14 files (budget 1500/25), 1435 test lines. No split asked |
| Device | `am instrument` (never AGP `connectedAndroidTest`, never a reboot): `CachePurgeOnDeleteInstrumentedTest` **OK (2 tests)** executed against a build of HEAD; `DeleteMenuItemVisibilityTest` 1 executed + 1 assumption-skipped; `DeleteConfirmDialogTest` and `e2e4_cancelNoop` assumption-skipped (F6) |
| PII screen | `git diff 8162e3c6..HEAD` and every round-2 commit message: no URL/host, no email, no token/credential text, no library or NAS path, no device identifier. Two regex hits are prose about PII policy and the *existing* `.tests.md` line requiring credentials to come only from the operator's runtime procedure |
| Machine health (3e) | Repo volume 629 G free before the first mutant and 629 G free after the last; scratch on the same volume; no mass-failure or parse-failure run |
| Revert discipline | Backups taken from the mutation list, one `.mutbak` per file (path-encoded so the two `strings.xml` files could not collide), restored with `cp`, `git diff --stat -- app/` printed **empty after every mutant**; `git checkout --`/`git restore` never used. The device mutants were reverted the same way, the debug APK rebuilt from the restored tree and reinstalled with `install -r`, and the instrumented class re-run green afterwards |

## 7. Conditions

### C-A — Builder, before the PR (cheap, non-destructive; F1-F5, F7, R2-B1)

1. **Pin the removal order (F1).** Extract the order into a pure static helper (e.g.
   `LibraryDeleteUtil.removalOrder(List<Integer> ascending)`) that `removeFromQueueAndAdvance`
   actually calls, and assert `removalOrder([1,3]) == [3,1]` (and `[0] == [0]`, `[] == []`) in the JVM
   suite. Acceptance: my mutant `for (int i = 0; i < positions.size(); i++)` must go RED.
2. **Widen the pre-request region (F7).** In
   `DeleteFromLibraryWiringSourceTest.testInFlightGuardWrapsRequestAndReleasesOnOutcome`, keep the
   existing two assertions and additionally forbid, in the code-only region before `DeleteItem(`:
   `delete_from_library_success`, `removeFromQueueAndAdvance(`, `purgeCacheEntry(`,
   `FavoritesStore.notifyChanged(`, `clearSuppressionIfOurs(`.
   Acceptance: both of my survivors (`toast(...success)` before the request,
   `removeFromQueueAndAdvance(itemId)` before the request) must go RED.
3. **Pin the absent-songs-row purge (F3).** Add one non-destructive instrumented leg: insert a `cache`
   row, delete the matching `songs` row, call `purgeCacheEntry`, assert `isCached(id)` is false.
   Acceptance: my mutant `if (song == null) return;` before `deleteFromCache(itemId)` must go RED on
   device.
4. **Make the R11 device class self-restoring (F4).** Before `createNewFile()`, delete any stale
   fixture file at each of the three paths; clean the three paths plus the inserted rows in `finally`.
   Acceptance: run the class twice in a row after deliberately breaking the purge once — the second
   run must fail on an R11 assertion, never on "fixture file must be creatable".
5. **Close the locale-side vacuity (F5) and the `|| true` bound (R2-B1).** One shared
   `private static final String KEY_FRAGMENT = "delete_from_library"` used by both `stringKeysMatching`
   call sites, plus `assertFalse(deleteFromLibraryKeys().isEmpty())` inside the locale test; and in
   `testVisibilityGateRequiresAServerBackedSongId`, assert the exact expression
   `setVisible(serverBacked)` in addition to the existing clauses.
   Acceptance: my two survivors (locale fragment matching nothing, `setVisible(serverBacked || true)`)
   must go RED.
6. **Record the drift decision.** Add `waive-test-drift:` lines naming
   `testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` and `testNewStringsLiveInDefaultEnglishOnly`
   (or the exact names the tool prints) with "round-2 tightening demanded by Reviewer B4/M-4; the
   replaced whole-file assertion is not coverage", so the pair-commit drift runs exit 0 rather than
   resting on my prose.

### C-B — QA, destructive, before the 1.3.14 version bump ships (the shipping gate)

R3, R4, R9 and R10 have no executed behavior proof and cannot get one from the gate. This cycle is
**destructive**: it deletes a real track from a real library. Run it exactly in this order, and stop
on the first failure rather than improvising with another item.

- [ ] **B0. Preconditions.** Read `.tests.md` P0→P8, L1-L5 and `.tasks.md` T4a/T6c/T9a/T10c. Confirm
      C-A is merged and `./gradlew testDebugUnitTest` is green. Confirm a **live app session** on the
      emulator — without it R1/R2's device legs and every E2E leg assume-skip (F6). Never read or
      copy the operator's credential store: the driver takes its arguments at run time and holds none.
- [ ] **B1. Author the driver** `tools/qa-delete-cycle.sh` implementing P0-P8 verbatim (it does not
      exist — G2), with per-phase PASS/FAIL/SKIPPED output, and keep its log and all backups
      **outside** the repo and outside any library share.
- [ ] **B2. Non-destructive device baseline, with per-test evidence.** Run
      `CachePurgeOnDeleteInstrumentedTest`, `DeleteMenuItemVisibilityTest`, `DeleteConfirmDialogTest`
      and `e2e4_cancelNoop` via `adb install -r` + `am instrument -w -e class …` (AGP connected runs
      uninstall the app and wipe the session — forbidden). Evidence = per-test
      `started:`/`finished:` lines plus `run finished: N tests, 0 failed`; **any
      `assumption failed` line for a leg means that leg did NOT run** and must be recorded as NOT RUN,
      never as a pass (F6). No reboot.
- [ ] **B3. P0 pre-probe.** `GET /Users` policy probe (L1). Find the nominated sacrificial item by
      search; enumerate **every** playlist holding it (the holder set H) with each entry count.
      **Abort the run** if the item is missing or held by no playlist — fix the fixture, do not
      substitute another item.
- [ ] **B4. P1 backup.** Ranged `GET /Items/{id}/Download` to a host temp dir outside the repo,
      sha256 recorded; record the original library folder path and H at run time only.
- [ ] **B5. P2 act + P3 server assertions.** Lone-entry leg first (play the item alone, delete,
      confirm, assert playback stops cleanly and no surface presents the dead item), then the
      from-a-playlist cycle. Then `GET /Items/{deletedId}` → **404**; every holder in H no longer
      lists the id; `POST /Library/Refresh`, wait, re-assert (the stale-`.m3u8` leg, L4).
      **This is R3's one-DELETE-on-the-wire proof and R4's clean-stop proof — the only place either
      can be demonstrated.**
- [ ] **B6. P4 app assertions.** Success toast; queue advanced and both surfaces showing the new
      current item; Favorites tab refreshed unprompted (**R9**); each playlist in H shows the entry
      gone.
- [ ] **B7. R10 leg (`e2e5`).** For 30 s after delete-and-advance, **no** `Sessions/Playing/Progress`
      request line names the deleted id (request-log oracle, L2 — if logging has been reverted, keep
      the in-app leg and record the log leg as SKIPPED, never fabricated); after an app restart the
      suppression pref no longer names the deleted id.
- [ ] **B8. R6 and R11 device legs (`e2e3`, `e2e6`).** Second delete of the same id → distinct
      already-gone toast, nothing claimed, item stays out of the queue. Cached song: cache via the
      app, delete cycle, cached state gone in-app **and** the cached files gone on disk.
- [ ] **B9. P5-P8 restore and audit.** Put the backed-up file back through the operator's admin
      procedure, refresh (L3), poll until re-indexed, record the **new** id; re-add to every playlist
      in H; assert each holder's count equals its B3 value; assert the item exists exactly once and no
      playlist outside H changed. Expected-as-fact, never as failure: new id, and favorites/play
      history do not survive (re-favorite only if it was favorited). Driver log + backups stay outside
      the repo; emulator reset by relaunch, never by reboot.
- [ ] **B10. Bookkeeping before the version bump ships.** Tick T4a/T6c/T9a/T10c only for legs that
      really executed; flip each matrix row's Status only on executed evidence; retire or amend the
      `Exists? NO` rows and G2 in the same commit. If any leg is not run, leave it visibly open and
      do **not** ship the version bump on the strength of the JVM gate alone.
- [ ] **B11. Final PII screen** on the QA diff and any committed report: no host, no user id/name, no
      token, no serial, no NAS/library path.

## 8. What I verified is correct (code read, not green)

Unchanged from round 1 unless noted: single stock `DeleteItem` call with zero retries and no cache for
non-GET; Volley delivers callbacks on the main loop, so the toast/queue/Room work in `handleOutcome` is
legal; the latch releases first thing in `handleOutcome` **and** now on the synchronous-throw path; the
401/404/transport mapping matches the wire shapes; SUCCESS is dispatched only from `onResponse`, so a
success claim cannot precede the server (and §2 shows the pre-request region now pins it); the 404
branch keeps cleanup but claims nothing and purges nothing; R10 stays call-only and sequenced
advance-then-clear. New in round 2, read and confirmed: the every-entry removal is correct for every
queue shape I could construct (§3), the purge is keyed on the deleted id and logs its catch, and the
`deleteFromCache` call is unconditional so a missing songs row cannot skip the Room row.

## 9. Disclosures and deviations

1. One result in my own runner was initially an **instrument artifact**, not a kill: a mutant that
   renamed a private method broke compilation and the runner read the previous run's XML. I fixed the
   runner (clear the results dir, treat a compile/merge failure as an instrument problem, real XML
   parsing for test-name↔message pairing) and re-ran; nothing in §1-§3 rests on a stale result.
   Two mutants of mine were re-run after cleanup: the `delete_playlist_x` control (it already exists
   in `values-de`, so a duplicate-resource merge error is not evidence) and the Room-row purge mutant
   (its first run hit F4's leftover fixture).
2. On the device I installed the current debug + androidTest APKs with `install -r` and used
   `am instrument` only: no AGP connected run, no uninstall, no reboot, no destructive case, no
   credential read. After each device mutant I restored from the `cp` backup, rebuilt and reinstalled
   the restored APK, and re-ran the class green. I deleted one leftover **test fixture** file under
   the debug app's own cache dir (created by the instrumented test, per F4) — library data untouched.
3. I did not edit any frozen spec file; F8 is the Architect's pen.
4. Scratch: `scratchpad/E01-F01-reviewer2/` (runner, backups, logs, the queue simulation), left in
   place, never removed (per `agents/reviewer.md` 3f). Note `scratchpad/` is untracked and **not** in
   `.gitignore` — a `git add -A` on this repo would commit the reviewer campaign. That is a harness
   hygiene gap worth one line in the installer's `.gitignore`, not an F01 defect.
