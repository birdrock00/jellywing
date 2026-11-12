# Reviewer — E01-F01 round 3 (verification of C-A.1 … C-A.5)

Date: 2026-11-12 (UTC). Round **3**, a verification pass against round 2's §7 C-A and §4 F1-F9.
Diff under review: `git diff 4b9498d6..HEAD` (`e88b33fa`, `17d91a00`, `7a3743c8`, `8e1ff1ee`,
`10e1e297`, plus the matrix commit `fe54b230`).

**Verdict at the bottom. Short form: all five C-A items landed; four are fully cleared; C-A.5's
R2-B1 half is cleared and its F5 half is only half cleared — one of my two round-2 survivors is
still GREEN, and I have a demonstrated one-plus-five-line replacement.**

Scratch: `scratchpad/E01-F01-reviewer3/` (gitignored; every mutant script, every Gradle log, every
`cp` backup and the per-run instrument logs are there, so any row below can be re-run verbatim).

## 0. Baseline and tree hygiene

Not repeated (the Orchestrator's baseline stands). What I did confirm **after** my own campaign, on a
fully restored tree, because the campaign touched four files:

| Check | Result |
|---|---|
| `./gradlew testDebugUnitTest` on the restored tree | **exit 0, 150 tests, 0 failures, 1 skipped** (`final_baseline.log`) — identical to the Orchestrator's baseline |
| `git diff --stat -- app/` | **empty** after every single mutant and at the end |
| Device left as found | `run-as … find cache/music -type f` → **no files**; committed APKs reinstalled; final `am instrument` → `OK (3 tests)` |
| Method | `cp` backups (`bak/*.mutbak`), never `git checkout -- app/`; `adb install -r -d` + `am instrument -w -e class …`; **no** AGP `connectedAndroidTest`, **no** reboot, **no** destructive cycle, **no** credential or the operator's credential store read |

My runner (`run_mut.sh`) clears `app/build/test-results/testDebugUnitTest` before each run and parses
the XML, so a GREEN means "the test task executed and produced XML with zero failures" — not "the task
was up-to-date". For every GREEN reported below I re-checked that `> Task :app:testDebugUnitTest`
appears in that run's log **without** `UP-TO-DATE`.

## 1. The structural question: does `removeFromQueueAndAdvance` actually consume `removalOrder()`?

**Yes at HEAD — and no, the suite does not prove it.** Both halves of that answer matter, so:

`app/src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java:208-230`:

```java
List<Integer> positions = positionsOfItem(MusicPlayerRemote.getPlayingQueue(), itemId);
if (positions.isEmpty()) { return; }
int first = positions.get(0);
boolean wasCurrent = positions.contains(MusicPlayerRemote.getPosition());
List<Integer> order = removalOrder(positions);          // ← line 218: consumed
for (int i = 0; i < order.size(); i++) {
    MusicPlayerRemote.removeFromQueue(order.get(i));    // ← line 220: iterates the helper's list
}
```

`positions` is never passed to `removeFromQueue` anywhere in the method, and `removalOrder` is called
exactly once, so the shipped order **is** the helper's. Three checks in
`testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries` guard that (`:288` contains
`removalOrder(`, `:291` forbids `removeFromQueue(positions.get(`, `:298` requires
`removeFromQueue(order.get(`), and mutating the region back to round 2's inline ascending loop is RED.

But the guard reads text, and two mutants that keep every token keep the suite GREEN while reproducing
the real defect (both in `scratchpad/E01-F01-reviewer3/`):

| Mutant | Result |
|---|---|
| `m_f1d.py` — `removalOrder(positions);` still called, but `List<Integer> order = new ArrayList<>(positions);` | **GREEN** — the queue removals go ascending again, the guard refuses the out-of-range index, a dead entry stays queued, 150 tests pass |
| `m_f1c.py` — `for (int i = order.size() - 1; i >= 0; i--)` (double inversion, same net effect) | **GREEN** |

So the answer to "could the helper test pass while the call site ignores it" is **yes, by rebinding the
local variable rather than by deleting the call**. The helper's *order* is executed-pinned (F1b below,
`expected:<[3, 1]> but was:<[1, 3]>`); the call site's *consumption* is pinned only textually, and
textual pins survive a rebinding. Rank: Minor as a shipping risk (no shipped code is wrong today) but
it is the exact hole round 2 asked to close, so it is recorded as **R3-B1** and the matrix row must say
it. A behavioural pin needs the device (`C-B` B5's lone-entry leg), which is why C-B stays the gate.

## 2. Per-item ruling on C-A.1 → C-A.5

Every "RED/GREEN" below is a run, not an inference, unless the sentence says **reasoned**.

### C-A.1 — pin the removal order (F1), `e88b33fa` → **CLEARED, with one bound (R3-B1)**

Round-2 acceptance was: *"my mutant `for (int i = 0; i < positions.size(); i++)` must go RED."* Met, in
both places it can be applied:

| Mutant | What it does | Result | Killed by |
|---|---|---|---|
| `m_f1a.py` | round 2's exact survivor: the consumed `removalOrder` loop replaced by the inline ascending walk over `positions` | **RED** (30 tests, 1 failure) | `DeleteFromLibraryWiringSourceTest.testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries:291` — the `assertFalse(body.contains("removeFromQueue(positions.get("))` clause |
| `m_f1b.py` | helper itself goes ascending: `Collections.reverse` removed from `removalOrder` | **RED** (30 tests, 1 failure) | `LibraryDeleteDuplicateQueueTest.testRemovalOrderIsHighestIndexFirst:56` — `expected:<[3, 1]> but was:<[1, 3]>` |
| `m_f1c.py` | call site walks the descending list backwards | **GREEN** | nothing (R3-B1) |
| `m_f1d.py` | call site calls the helper and ignores its return | **GREEN** | nothing (R3-B1) |

So the kill for the *helper* is executed behavior, and the kill for the *call site* is a textual pin —
which is honest to say out loud, because that is precisely where F1c/F1d live. `removalOrder([1,3]) ==
[3,1]`, `[0] == [0]`, `[] == []` all hold at HEAD (the test asserts all three shapes and passes).

**One-line hardening that closes R3-B1** (reasoned, deliberately not applied by me — Reviewer
boundary): add to the same test
`assertTrue(body.contains("List<Integer> order = removalOrder(positions)"))` and
`assertTrue(body.contains("for (int i = 0; i < order.size(); i++)"))`.
Both strings appear verbatim in the shipped method, and each of F1c and F1d rewrites exactly one of
them. I did not run this, so it is labelled as reasoned and the Builder should confirm in one Gradle run.

### C-A.2 — widen the pre-request region (F7), `17d91a00` → **CLEARED**

Round-2 acceptance: both survivors (`toast(...success)` before the request, and
`removeFromQueueAndAdvance(itemId)` before the request) must go RED. The region now forbids all five
success-side effects (`DeleteFromLibraryWiringSourceTest:376-388`), and I ran each one **into the region
immediately before `App.getApiClient().DeleteItem(`**:

| Mutant | Inserted before the request | Result |
|---|---|---|
| `m_f7a.py` | `toast(R.string.delete_from_library_success);` | **RED** (16 tests, 1 failure) |
| `m_f7b.py` | `removeFromQueueAndAdvance(itemId);` | **RED** (16 tests, 1 failure) |
| `m_f7c.py` | `purgeCacheEntry(itemId);` | **RED** (16 tests, 1 failure) |
| `m_f7d.py` | `FavoritesStore.notifyChanged();` | **RED** (16 tests, 1 failure) |
| `m_f7e.py` | `clearSuppressionIfOurs(itemId);` | **RED** (16 tests, 1 failure) |

All five fail in `testInFlightGuardWrapsRequestAndReleasesOnOutcome:382` (the new loop), and each run was
scoped to that class, so no neighbouring test is doing the killing. Bound, stated for the record:
the region is **lexical** — the code-only text between `deleteFromLibrary` and `DeleteItem(` — so a
premature effect that lives inside a helper the pre-request path calls is outside the region and survives.
That bound is **reasoned, not demonstrated**; it is the same class of limit as R3-B1 and it does not
change the ruling, because every shape round 2 actually named is now dead.

### C-A.3 — pin the absent-`songs`-row purge (F3), `7a3743c8` → **CLEARED, executed on the emulator**

| Run | Result |
|---|---|
| `CachePurgeOnDeleteInstrumentedTest`, committed tree, `install -r -d` + `am instrument` | **`OK (3 tests)`**, logcat `run started: 3 tests` / per-test `started:`/`finished:` for all three legs / `run finished: 3 tests, 0 failed, 0 ignored`, **no `assumption failed` line** |
| Mutant `d_f3.sh` = `if (song == null) { return; }` inserted before `cacheDao().deleteFromCache(itemId)` in `LibraryDeleteUtil.purgeCacheEntry` | **RED on device**: `Tests run: 3, Failures: 1` at `CachePurgeOnDeleteInstrumentedTest.java:109` — the new leg's own R11 assertion, *"a missing songs row must not skip the Room cache row"* |
| Restore, rebuild, reinstall | **`OK (3 tests)`** |

The Builder's disclosed deviation is honest and I checked it rather than accepting it: the leg never
creates a `songs` row (it could not delete one anyway — `SongDao.deleteSongs()` is a whole-table
`DELETE FROM songs`, which would wipe a development emulator's real library), so the state is "cache row
present, songs row never existed". For `purgeCacheEntry` the observable state is identical, and the leg
**asserts its own precondition** (`:104-105`, `assertNull(getSong(id))`), so it cannot silently degrade
into the already-covered cached case. Non-destructive, and it leaves no row behind (`finally`).

### C-A.4 — self-restoring R11 device class (F4), `8e1ff1ee` → **CLEARED, demonstrated by running it twice**

This is the item round 2 flagged as most likely claimed-but-not-shown, so here is the actual sequence,
all four runs on the emulator, logs in `scratchpad/E01-F01-reviewer3/dev_*.log`:

1. **Purge deliberately broken** (`d_purgeaudio.sh`: the `new File(MusicUtil.getFileUri(song)).delete()`
   arm removed from `purgeCacheEntry`), rebuilt, installed → **run 1**: `Tests run: 3, Failures: 1`,
   failing on `downloaded audio file must be gone (R11)` (`:76`).
2. **Same broken build, run 2, no other change** → `Tests run: 3, Failures: 1`, again
   `downloaded audio file must be gone (R11)` (`:76`) — **not** on `fixture file must be creatable`.
   That is round 2's acceptance, met.
3. **The mechanism proven load-bearing, in the other direction.** With the committed code I planted a
   stale fixture at exactly the path that does not carry the run-unique id
   (`cache/music/QA Fixture/QA Fixture/0.0 - QA Purge Fixture.m4a`, verified present by `ls`) and ran
   the class: **`OK (3 tests)`**, and the planted file was gone afterwards — `reclaimStaleFixture`
   reclaimed it and the leg re-created its own.
4. **The trap reproduced with the mechanism removed** (`d_noreclaim.sh`: the two `reclaimStaleFixture(…)`
   call sites deleted, `finally` cleanup left in place) with a stale fixture planted:
   `Tests run: 3, Failures: 1` on **`fixture file must be creatable: …0.0 - QA Purge Fixture.m4a`** —
   the round-2 failure, verbatim. So the reclaim is not decoration.

Precision for the record: for the acceptance scenario (runs 1-2) the load-bearing half is
`finally { restoreFixtureState(…) }`, which deletes the audio file even when the assertions fail; the
reclaim is the half that survives a process that dies before `finally` (run 4). Both are needed, both
are now demonstrated, and neither is doing its work by accident. Device left clean (step: `find
cache/music -type f` → empty; committed APKs reinstalled; final `OK (3 tests)`).

### C-A.5 — shared `KEY_FRAGMENT` (F5) and the exact `setVisible` expression (R2-B1), `10e1e297`

**R2-B1 half: CLEARED.** Round-2 acceptance: `setVisible(serverBacked || true)` must go RED.

| Mutant | What it does | Result | Killed by |
|---|---|---|---|
| `m_r2b1.py` | `deleteItem.setVisible(serverBacked \|\| true);` | **RED** (16 tests, 1 failure) | `testVisibilityGateRequiresAServerBackedSongId:166` — the new exact-expression `assertTrue(body.contains("setVisible(serverBacked)"))` |
| `m_r2b1b.py` | flag routed through a local: `boolean alwaysShow = serverBacked \|\| true; setVisible(alwaysShow);` | **RED** | same assertion:166 (the token disappears) |
| `m_r2b1c.py` | `boolean serverBacked = song != null \|\| true;` | **RED** | same test:156 (`song.id != null` disappears) |
| `m_r2b1d.py` | `boolean serverBacked = song != null \|\| (song != null && song.id != null && !song.id.trim().isEmpty());` | **GREEN** | nothing |

`m_r2b1d` is the honest survivor: the disjunct is folded into the *initializer*, so every token each
assertion looks for is still present and `setVisible(serverBacked)` is untouched, while the flag is
always true whenever a song exists — i.e. Delete-from-library shown for local-only songs. One surviving
shape, narrower than round 2's, and it is the intrinsic limit of a textual gate. Rank Minor; the real
proof of R1's shown-when-server-backed property is the device leg (matrix G4, C-B B2), which still
self-skips without a live session. Note the Builder's own residual bound predicted the local-variable
shape would survive; it does not (`m_r2b1b` is RED), so the disclosure was slightly pessimistic in the
safe direction.

**F5 half: PARTIALLY cleared — one of my two named round-2 survivors is still GREEN.**

| Mutant | What it does | Result |
|---|---|---|
| `m_f5b.py` | the shared constant itself: `KEY_FRAGMENT = "delete_from_library_ZZZ"` | **RED** (16 tests, **2** failures: `testDeleteStringKeyListIsDerivedNotEmpty:51` and `testNewStringsLiveInDefaultEnglishOnly:71`) — the new anti-vacuity floor inside the locale test fires, which is exactly what round 2 asked for |
| `m_f5d.py` | locale call site re-split to the **identical** literal `stringKeysMatching(locale, "delete_from_library")` | **RED** — `testBothStringKeyCallSitesShareTheOneFragment:188`, `expected:<1> but was:<2>` |
| `m_f5a.py` | locale scan pointed at a fragment that matches nothing: `stringKeysMatching(locale, "delete_from_library_ZZZ")` | **GREEN** — isolated class *and* the full 150-test suite |
| `m_f5a2.py` | same defect, spelled off the shared constant: `stringKeysMatching(locale, KEY_FRAGMENT + "_ZZZ")` | **GREEN** |
| `m_f5c.py` | same defect, re-wrapped across lines with concatenation | **GREEN** |

So every claim in the Builder's table is true as written — "locale call site re-split to its own literal
→ RED" is real for the *identical* literal (`m_f5d`), and "KEY_FRAGMENT matches nothing → RED" is real
(`m_f5b`) — but it is not the mutant round 2 named. My F5 survivor was *"changing only the locale scan to
a fragment that matches nothing"*, and that is still GREEN in every spelling that does not repeat the
literal verbatim. Why the fix does not reach it: the one-literal pin counts lines containing the quoted
`"delete_from_library"` (`:184-186`), so a transformed or different fragment is invisible to it, and the
anti-vacuity floor asserts on `deleteFromLibraryKeys()` — the **default-resources** derivation — which
still matches whether or not the locale loop is scanning for anything. The floor guards a list the locale
loop does not use. Net effect of `10e1e297`: **drift** by duplicating the literal is now impossible to
land silently (good, and worth having); a **deliberate** re-pointing of the locale scan is still
uncaught. Rank Minor (a test-side vacuity in a hygiene rule, R12, no product behavior at stake) — but it
is an unmet acceptance criterion, so it is a condition, not a note.

**C-C1 — the fix, demonstrated rather than proposed.** I prototyped it in the scratch campaign and
reverted it (the tree is clean). Two parts, in
`DeleteFromLibraryWiringSourceTest.testNewStringsLiveInDefaultEnglishOnly`:

1. give the locale loop a scan of its own so there is one place to aim:
   `private static List<String> translatedDeleteKeys(String localeXml) { return stringKeysMatching(localeXml, KEY_FRAGMENT); }`
   and call `translatedDeleteKeys(locale)` in the loop;
2. add a **positive control** on that scan — the rule is "zero matches per locale", which a scan that
   cannot match anything satisfies for free:

```java
List<String> derived = deleteFromLibraryKeys();
String plantedXml = "<resources><string name=\"" + derived.get(0) + "\">x</string></resources>";
assertEquals("the locale scan must report a planted delete_from_library* key, otherwise "
        + "\"no translations\" means nothing (R12/F5)",
        1, translatedDeleteKeys(plantedXml).size());
```

Runs: `fixc1` (the change alone, HEAD otherwise untouched) → **GREEN, 16/0**, so it does not break the
existing rule; `fixc1sab` (that change plus the locale scan aimed at `"delete_from_library_ZZZ"`) →
**RED**, `testNewStringsLiveInDefaultEnglishOnly: expected:<1> but was:<0>`. The same sabotage in the
`KEY_FRAGMENT + "_ZZZ"` and re-wrapped forms goes through the same single scan, so all three of my GREEN
survivors die together.

What does **not** work, and I proved that too rather than asserting it: a source-text pin of the form
`source.contains("stringKeysMatching(locale, KEY_FRAGMENT)")`. With that pin in place (`pinonly`, GREEN at
HEAD as expected), `m_f5a` and `m_f5a2` **both stayed GREEN** (`pinf5a`, `pinf5a2`) because the assertion's
own literal satisfies its own `contains` — a self-referential pin. Anyone implementing C-C1 must use the
positive control, not the textual match.

After reverting the prototype, the restored tree re-ran **150 tests, 0 failures, 1 skipped**.

## 3. Is the matrix refresh (`fe54b230`) *true*?

Largely yes, and honestly hedged: every "IN PROGRESS / UNVERIFIED" row was true **at the commit the
Architect observed** (`8e1ff1ee`), and the Architect disclosed it ran no Gradle, emulator or mutation.
The rows it marked *executed* are the ones resting on my logs, and I checked each against this campaign:

**Rows whose "executed" claim my evidence covers (confirm):** the four outcome mappings, the
synchronous-throw latch (`LibraryDeleteSyncThrowLatchTest`), the dialog source contracts, the destructive
copy content, the English-only rule and its non-empty derived list, both-player-themes gate, the
`purgeCacheEntry` narrowness, and the `CachePurgeOnDeleteInstrumentedTest` legs — all executed by me
across rounds 2 and 3 (round 3: `OK (3 tests)` with per-test logcat, twice, plus the two device mutants).
The "33 locale files today" count checks out (`app/src/main/res/values-*/strings.xml` = 33, inside 37
`values-*` directories). `e2e4_cancelNoop` is genuinely the only E2E method that exists
(`grep "public void e2e"` → one hit), so the `Exists? NO` / NOT RUN rows are honest.

**Rows that are stale in the optimistic direction now that I have ruled — one doc edit each:**

| # | `.tests.md` line | Current text says | Replace with |
|---|---|---|---|
| 1 | 61 (R4 removal order) | "UNVERIFIED … treat as IN PROGRESS, not done" | **CLEARED (round 3)**: ascending loop inline RED (`testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries:291`) and ascending helper RED (`testRemovalOrderIsHighestIndexFirst:56`, `expected:<[3, 1]> but was:<[1, 3]>`). **Bound R3-B1:** a call site that calls `removalOrder` but binds `order` to `positions`, or walks `order` backwards, stays GREEN — the consumption pin is textual |
| 2 | 58 (R4 pre-request region) | "C-A.2 … no Reviewer has re-run F7's two survivors … unverified" | **CLEARED (round 3)**: all five success-side effects inserted before `DeleteItem(` are RED at `:382` — success toast, `removeFromQueueAndAdvance`, `purgeCacheEntry`, `FavoritesStore.notifyChanged`, `clearSuppressionIfOurs` |
| 3 | 76 (R11 absent-`songs`-row) | "UNVERIFIED and NOT RUN … never executed on a device" | **CLEARED (round 3, executed)**: leg runs (`OK (3 tests)`, per-test logcat) and `if (song == null) return;` before `deleteFromCache(itemId)` is RED on device at `:109`. Also fix the description: the leg **never creates** a `songs` row (a whole-table `deleteSongs()` would wipe the emulator's library) and asserts that absence as its precondition |
| 4 | 77 (R11 self-restoring fixture) | "NOT PROVEN today … acceptance has not been performed on a device" | **CLEARED (round 3, executed twice)**: with the purge broken, run 1 **and** run 2 fail on `downloaded audio file must be gone (R11)`; a planted stale fixture is reclaimed (`OK (3 tests)`), and deleting the two `reclaimStaleFixture` calls reproduces `fixture file must be creatable` |
| 5 | 49 and 95 (R1 gate, bound R2-B1) | "C-A.5 … had not landed … no mutation evidence — not done" | **R2-B1 CLOSED**: `setVisible(serverBacked \|\| true)` RED at `:166`, and so are the local-variable and initializer `\|\| true` shapes. Remaining bound, narrower: `serverBacked = song != null \|\| (…full id test…)` keeps every token and stays GREEN; R1's shown-when-server-backed property still has no executed device leg (G4 unchanged) |
| 6 | 80 (R12 / F5) | "Open until the round-3 review shows F5's and R2-B1's mutants RED" | **Split ruling**: R2-B1 RED (row 5). **F5 is not closed** — a locale scan aimed at a non-matching fragment (`stringKeysMatching(locale, "delete_from_library_ZZZ")`, `KEY_FRAGMENT + "_ZZZ"`, or the re-wrapped concatenation) is GREEN, isolated and in the full suite. What `10e1e297` did close is drift-by-identical-duplication (`m_f5d` RED via the one-literal pin) and the constant itself going vacuous (`m_f5b` RED ×2). Condition **C-C1** closes the rest with a positive control on the scan, and note the trap: a `source.contains("stringKeysMatching(locale, KEY_FRAGMENT)")` pin is self-satisfying and does **not** work (verified GREEN) |
| 7 | 145-161 (IN PROGRESS THIS ROUND block) | "C-A.1 → C-A.5. None of them is done" | Supersede: C-A.1, C-A.2, C-A.3, C-A.4 and C-A.5's R2-B1 half are **CLEARED with Reviewer mutation evidence (round 3)**; C-A.5's F5 half is **open as C-C1**; keep the operative rule (a C-A item is coverage only when its named mutant is RED) because it is what produced this result |
| 8 | 130-133 (G4 ledger parenthetical) | "Two legs added after that run … have **never executed** on any device" | Both executed on 2026-11-12 (round 3, `am instrument`, per-test `started:`/`finished:`, `run finished: 3 tests, 0 failed, 0 ignored`). Keep them on C-B's must-run list for the shipping run; drop the "never executed" claim |
| 9 | 37 (header bullet) | "**Four** of the six device legs in this matrix skipped on the 2026-11-12 run" | **Three** of the six, and the file's own G4 ledger three pages below says exactly that ("three executed and three did not"). My round-2 logcat agrees: the three that skipped are `deleteItemShownWhenServerBackedSongIsCurrent`, `DeleteConfirmDialogTest` (both `assumption failed: Need an existing real Jellyfin session`) and `e2e4_cancelNoop` (needs driver args) |
| 10 | 339-341 (QA must-run device set) | "…including its two new never-executed legs: the C-A.3 absent-`songs`-row leg and the C-A.4 self-restoring fixture behaviour (break the purge, run twice…)" | Split these two, or QA will try to sabotage production code during a shipping run. The **C-A.3 leg is a shipping leg** and belongs in C-B. "Break the purge, run the class twice" is a **review-time mutation probe**, already performed (round 3); it is not something to do against a shipping tree, and C-B must not ask for it |
| 11 | new row | `testBothStringKeyCallSitesShareTheOneFragment` appears in **neither** `.tests.md` nor `.tasks.md` (0 occurrences each) | Add a mapped row (R12). It is an executed test nobody has mapped — the same failure shape round 2 filed as F8, recurring one round later |

**Pair-commit test drift (C-A.6's rule, applied to round 3).** `test-drift-check.sh` run *at each pair
baseline* (`e88b33fa`, `17d91a00`, `7a3743c8`) exits **1** with **8 findings, 0 waived**, all in
`CachePurgeOnDeleteInstrumentedTest.java` (`assert-removed` on the two fixture-creation assertions,
`assert-modified` on the four R11 assertions and the two no-op assertions). At `8e1ff1ee`, `10e1e297` and
`HEAD` it exits **0**, and the whole-feature run is 0. Disposition, evidenced rather than asserted: the
findings are **line-move artifacts of the C-A.3/C-A.4 `try`/`finally` restructuring, not weakened
coverage** — (a) every named assertion still exists verbatim at HEAD (each of `cache row must be gone
after purge (R11)`, `cached file at cache root must be gone (R11)`, `in-flight download file must be gone
(R11)`, `downloaded audio file must be gone (R11)`, `fixture must start cached`, `un-cached purge must not
disturb other rows/files (R11)` grep-counts ≥1, and the file carries **15** `assert*` call sites at HEAD
against **10** at `4b9498d6` — strictly more coverage, not less; and two of the eight findings (`:118`,
`:120`) pair a real assertion with a *javadoc* line (`* {@code finally} so a failing assertion…`), which is
the tool's line-matcher sliding into a comment),
and (b) the moved assertions still **fire**: the broken-purge device run failed on `downloaded audio file
must be gone (R11)`, one of the lines reported "removed". Recommended fix so the pair runs go green
without anyone re-reading my prose: one `waive-test-drift:` line in `.tasks.md` naming
`app/src/androidTest/java/club/thatpetbff/gramophone/database/CachePurgeOnDeleteInstrumentedTest.java`
with "round-3 C-A.3/C-A.4 restructuring moved these assertions into the `try`/`finally` fixture wrapper;
each still exists and still fires on device". Note the two existing waivers cover only
`DeleteFromLibraryWiringSourceTest.java`, so this is a new, uncovered case — and it is the tool behaving
as designed on a moved line, not a tool defect.

## 4. F9 — the filing did misstate two things; corrected in place

`.harness/progress/inbox/QUEUE-1-shuffle-index-corruption.md` is **not fixed** (out of scope, correctly)
but it is **re-worded**, because two of its sentences were looser than the evidence and that looseness is
what carries the scheduling decision:

1. **"The two lists are independently ordered" — false as a blanket claim.** They are copies, and every
   mutator that keeps them in step leaves them index-identical; with no prior divergence
   `removeSong(p)` removes the *same* song from both, which is correct behaviour, not corruption. The
   defect needs a divergence, and I named the two ordinary sources: `moveSong` (`QueueManager:233-238`)
   reorders **only** `getPlayingQueue()`, so a single drag-reorder with shuffle off leaves
   `shuffledQueue` stale — simulated `[a,b,c,d,e,f]` → `moveSong(0,3)` → visible `[b,c,d,a,e,f]` /
   hidden `[a,b,c,d,e,f]` → `removeSong(1)` drops `c` from the queue and `b` from the shuffle list — and
   `restoreQueue()` loads the two lists from **separate** DB reads (`getQueue(0)`, `getQueue(1)`) with no
   re-derivation. So: divergence is easy to reach, but it is *reachable*, not *always present*, and
   "removing N entries evicts N unrelated entries" is true only in the diverged state.
2. **"Mostly latent" — right conclusion, and it now rests on a stated mechanism instead of a hunch.**
   While shuffle is off nothing reads `shuffledQueue` at all: `getPlayingQueue()` (`:50-52`) returns
   `playingQueue`, `getSongAt`/`getCurrentSong` route through it, and the only other mention of the field
   anywhere in `app/src/main` is the persistence call `QueueSongDao.updateQueues(playingQueue,
   shuffledQueue)`. **Both** transitions into a state where it is visible re-derive it —
   `setShuffleMode(SHUFFLE)` → `shuffleQueue()` (`:128-134`, `:162-179`) overwrites it from
   `playingQueue`, and `setShuffleMode(NONE)` assigns `shuffledQueue = new ArrayList<>(playingQueue)`
   (`:150`) — and the shuffle preference is only ever written by `setShuffleMode`, so a process kill
   leaves the preference at NONE and the next toggle re-derives. There is therefore no path that makes
   the corrupted list *displayed*: the honest wording is **silent corruption of the persisted hidden
   queue rows**, not "sometimes visible, mostly not". Latency rests entirely on that re-derivation
   accident and nothing tests it (`QueueManagerRepeatModeTest` covers repeat mode; no test in the repo
   exercises `removeSong` in either mode) — which is why the ticket stays worth keeping while not being
   worth scheduling inside a release whose user-visible surface is unaffected.
3. Added one adjacent observation, explicitly marked as **not** part of the finding, so the queue owner
   sees the whole two-list-copy shape: `moveSong` while shuffle is **on** reorders only `shuffledQueue`,
   and `setShuffleMode(NONE)` (`:150`) then overwrites it from the stale `playingQueue`, so a
   user's drag-reorder made while shuffling is silently discarded when shuffle is switched off. Whether
   that is intended is a product question; I did not assert it is a defect.

Also corrected: the reason F01 matters here is *more corrupted rows per action once diverged*, not
"more frequent reach" of a visible symptom.

## 5. Residual risks

- **R3-B1 (Minor, test-side).** `removeFromQueueAndAdvance` can be made to ignore `removalOrder()`'s
  return (or walk it backwards) with the whole suite GREEN. HEAD is correct; the pin is textual.
  One-line hardening named in §2 C-A.1 (reasoned, not run by me).
- **C-C1 (Minor, test-side, unmet acceptance).** The locale English-only rule can still be aimed at a
  fragment that matches nothing. Demonstrated GREEN in three spellings; demonstrated fix in §2 C-A.5.
- **R2-B1 survivor (Minor).** `serverBacked = song != null || (…)` keeps every token. Intrinsic to a
  source-contract gate; the real proof is R1's device leg, which still self-skips (G4).
- **Region bound on C-A.2 (Minor, reasoned, not demonstrated).** The pre-request region is lexical, so a
  premature success effect living inside a helper called before `DeleteItem(` is outside it.
- **R11's file removals remain unpinned in the gate (F2, unchanged).** The device class is the only pin;
  deleting the three `File.delete()` lines leaves `testDebugUnitTest` green. I re-confirmed the device
  side: breaking one of them is RED on device.
- **F6 unchanged and load-bearing for QA.** Instrumented legs `assumeTrue`-skip and both runners call that
  success. Every C-B leg needs its per-test `started:`/`finished:` lines plus the absence of any
  `assumption failed` line, or it did not run.
- **Device state left as found.** `install -r -d` only — never an uninstall, never AGP
  `connectedAndroidTest`, never a reboot — so the app's data (and whatever session state it holds)
  survived the campaign; the Room DB demonstrably survived (fixture rows inserted and cleaned). QA still
  re-confirms the live-session precondition at B0 as written. No fixture file or row was left behind.
- **Matrix truth.** Eleven specific edits in §3, including one internal contradiction (header says four
  device legs skipped, its own ledger says three) and one unwritten-test-named-as-coverage shape
  recurring (a new executed test with no matrix row).

## 6. Verdict

C-A.1, C-A.2, C-A.3, C-A.4 and C-A.5's R2-B1 half are cleared with the named mutant shown RED, on the
emulator where the acceptance said on the emulator, and by re-running rather than by reading the Builder's
claim. C-A.5's F5 half is not cleared: one of the two survivors round 2 named is still GREEN, the
Builder's claim about it is true only for the identical-literal shape, and I have a replacement pin that I
demonstrated kills all three variants while keeping HEAD green. Nothing here is a product defect, and no
shipped behaviour regressed: the restored tree is 150 tests / 0 failures / 1 skip with `app/` clean.

**VERDICT: APPROVE WITH CONDITIONS**

Conditions, both cheap and neither destructive:
- **C-C1 (Builder, before the PR, ~6 lines in `DeleteFromLibraryWiringSourceTest`):** give the locale loop
  a named scan and add the positive control from §2 C-A.5, then show `KEY_FRAGMENT + "_ZZZ"` and the
  re-spelled-literal variants RED. Do **not** implement it as a `source.contains(...)` textual pin —
  I verified that form is self-satisfying and leaves the hole open.
- **C-C2 (Architect, doc-only, before the PR):** the eleven matrix edits in §3 (including the
  four-vs-three contradiction, the `found.add(0, i)` mis-citation — the shipped code is `found.add(i)` at
  `LibraryDeleteUtil:241` and `found.add(0, i)` is the mutant, which I confirmed RED on both duplicate
  legs — the two now-stale "never executed" claims, the superseded IN-PROGRESS block, a row for
  `testBothStringKeyCallSitesShareTheOneFragment`, splitting the review-time purge-sabotage probe out of
  C-B's shipping device list, and one `waive-test-drift:` line for
  `CachePurgeOnDeleteInstrumentedTest.java` so the pair-commit drift runs exit 0).
- **C-B (QA, destructive) stands exactly as round 2 wrote it, and remains the shipping gate.**

**The only remaining gate is C-B** — the destructive P0→P8 delete-and-restore cycle with per-test device
evidence, because R3/R4/R9/R10 still have no executed behavioral proof anywhere and R11's file removals
still have no gate coverage. **C-B as written is executable**: the sacrificial item is nominated by name
("Jai Hind" in playlist `aurral-udta-teer`, probed present 2026-11-12), the abort rules are stated (abort
if the item is missing or held by no playlist; never substitute another item), the backup/restore
mechanism and the expected-as-fact items (new id, favorites and play history do not survive) are all
specified, and B2's per-test-evidence rule matches exactly how I ran the non-destructive legs today.
Two things to know before starting, neither of which is an improvisation *during* the delete: B1's driver
`tools/qa-delete-cycle.sh` must be authored first (it does not exist — G2), and B7/B8 name
`e2e3`/`e2e5`/`e2e6`, which also do not exist — only `e2e4_cancelNoop` does — so those three cases get
authored with the driver, per the matrix's own E2E work order. And per §3 row 10, "break the purge and run
the class twice" is **not** a shipping step; it was a review-time mutation probe and it has been performed.
