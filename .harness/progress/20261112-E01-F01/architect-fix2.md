# Architect — E01-F01 round-3 spec pass (Reviewer F8 + F2/F6 wording + C-A.6)

Date: 2026-11-12 (UTC). Files changed: **only**
`specs/epics/E01-library-management/F01-delete-playing-song/F01-delete-playing-song.tests.md` and
`…/F01-delete-playing-song.tasks.md`. No `app/`, no `tools/`, no `state/tasks.json`, no other spec
file. Only read-only git was used (`status`/`log`/`show`/`diff`); **nothing added, committed,
stashed or checked out**; feature status untouched.

**The Builder was committing `app/**` throughout this pass**, so "what exists" is dated rather than
absolute: the round-2 surface described in F8/F2/F6 is `4b9498d6`; the C-A work landed under
`e88b33fa` (C-A.1 removal order), `17d91a00` (C-A.2 pre-request region), `7a3743c8` (C-A.3
absent-`songs`-row leg) and `8e1ff1ee` (C-A.4 self-restoring fixture), with C-A.5 still uncommitted at
`8e1ff1ee` (2026-11-12 ~21:26 UTC). **None of the five is credited as coverage anywhere in this pass**
— each row says "written, unverified", because coverage here means *the Reviewer showed the named
mutant RED*, which has not happened for any of them, and C-A.3/C-A.4 have never been run on a device.

## F8 — the seven stale rows, fixed to HEAD

| Row | Was | Now |
|---|---|---|
| R2 (dialog contract) | `NOT YET WRITTEN … Exists? NO` | `DeleteFromLibraryDialogSourceTest::testConstructingTheDialogIssuesNoRequest` + `#testDeleteCallSitsInsideOnPositive`, executed, RED for **both** B2 shapes |
| R2 (copy content) | `NOT YET WRITTEN … Exists? NO` | `#testConfirmCopyCarriesTheThreeWarnings` (content, not key existence); plus a new row for `#testConfirmAndCancelButtonsAreDistinctVerbs` |
| R4 (SUCCESS scoping) | "weak as written … pending" | scoped since `9fb1f19b`; independent of the `ALREADY_GONE` kill |
| R9 (SUCCESS scoping) | "does not pin R9 today" | scoped; RED for the SUCCESS-only `notifyChanged()` removal |
| R8 (sync throw) | "gap: neither covers a synchronous throw" | `LibraryDeleteSyncThrowLatchTest` (2 tests) — new row, incl. the discriminating shape (catch kept, `endDelete` removed) |
| R12 (key list) | "hand-typed literal … derive it" | pattern-derived since `f68d6eea` with the non-vacuity floor; F5's locale-side hole stated as open |
| G1 (surviving mutant) | "always-show survives (M-5)" | M-5 closed by the two executed JVM contracts; what survives is **R2-B1** `setVisible(serverBacked \|\| true)` |

## F8 — the previously unmapped tests, now rows

Added: `DeleteFromLibraryDialogSourceTest` (×3 rows incl. the distinct-verbs test),
`LibraryDeleteDuplicateQueueTest`, `testVisibilityGateRequiresAServerBackedSongId`,
`testBothPlayerThemesApplyTheGateOnInflateAndSongChange`,
`testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries`,
`testDeleteStringKeyListIsDerivedNotEmpty`, `LibraryDeleteSyncThrowLatchTest`, and — **not on the
Reviewer's list** — `testCachePurgeIsNarrowAndLogsItsCatch` (added by round-2 commit `0a148494`, also
previously zero-mentions). Every `public void` added by `git diff 8162e3c6..HEAD -- app/src/test app/src/androidTest`
is now named in the matrix; I verified that by diff rather than by the review's list, which
under-counted by one.

Rows added for **open** properties too, so "unmapped" is not mistaken for "covered": R4 removal order
(F1), R4 pre-request success-side effects (F7), R11 absent-`songs`-row purge (F3), R11 self-restoring
fixture (F4) — each marked written-but-unverified where a C-A commit exists, and `Exists? NO` in
substance where it does not. Also added to the G4 ledger: the two `CachePurgeOnDeleteInstrumentedTest`
legs that postdate the round-2 device run and have **never executed**.

## F6 — self-skipping legs, treated as a class

Matrix rows for the three skipped legs now read **"exists, self-skips without a live session /
driver args — NOT RUN"**: `DeleteConfirmDialogTest`, `deleteItemShownWhenServerBackedSongIsCurrent`,
and `e2e4_cancelNoop` (marked **has never executed** — its args come from the driver that does not
exist, G2). New **G4** states the rule and carries a per-leg executed-ledger table (3 ran / 3 did
not, 2026-11-12) plus: *a green instrumented run is evidence only with per-test `started:`/`finished:`
lines and no `assumption failed` line*, and **"an ignored test is a missing test, and this harness's
runners do not distinguish the two."** The closing "demonstrated today" paragraph no longer claims
the menu surface, the dialog or the cancel path as demonstrated.

## F2 — R11's file removals

New **G5** and a rewritten R11 instrumented row say it plainly: delete all three `File.delete()`
lines and `./gradlew testDebugUnitTest` stays GREEN; the JVM purge contracts pin cost, success-scoping
and logging only. The class is on C-B's must-run list (also restated at the end of the file).

## C-A.6 — drift waivers recorded

Two `waive-test-drift:` lines in `.tasks.md`, plus a prose block naming the exact findings, counts and
pair commits. **Deviation from the brief's wording, on evidence:** C-A.6 suggested naming
`testSuccessOutcomeRunsQueueDropAndFavoritesRefresh` / `testNewStringsLiveInDefaultEnglishOnly`. For
these Java findings the tool reports the **file path** in its test-name column, and a waiver matches
byte-for-byte, so a method-name waiver waives nothing (verified empirically: `4 findings, 0 waived`).
The waiver lines therefore name
`app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java` and carry
the method names + replaced assertion labels in the reason, which is what a human needs. Every pair run
now exits 0 on evidence (output below). Caveat written into the file: the waiver is **file-scoped**, so
new drift in that file is auto-waived — the Reviewer must read the waived line, and the waivers should
be deleted once those pair commits are behind the default branch.

## C-A.1 → C-A.5 marked in progress

One block in "What actually executes" lists all five with its finding id, its dated commit (or
"uncommitted"), and the **operative rule**: a C-A item counts as coverage only when the review shows
its named mutant RED — C-A.1 the ascending removal loop, C-A.2 a pre-request success toast and a
pre-request `removeFromQueueAndAdvance(itemId)`, C-A.3 `if (song == null) return;` before
`deleteFromCache(itemId)` on device, C-A.4 break-the-purge-then-run-twice, C-A.5 a locale fragment that
matches nothing and `setVisible(serverBacked || true)`. The affected rows (R1 `|| true`, R4 pre-request
region, R4 removal order, R11 absent-songs-row, R11 fixture, R12 locale-side vacuity) each carry that
treatment, so the QA round cannot read any of them as done.

## Gates (all re-run after the edits)

```
$ python3 .harness/tools/tasks-pair-check.py \
    .harness/specs/epics/E01-library-management/F01-delete-playing-song/F01-delete-playing-song.tasks.md \
    --spec .harness/specs/epics/E01-library-management/F01-delete-playing-song/F01-delete-playing-song.spec.md
tasks-pair-check: ok 12 R-ids, 23 tasks
exit: 0

$ python3 .harness/tools/readiness-check.py \
    .harness/specs/epics/E01-library-management/F01-delete-playing-song --root .
readiness-check: ok .harness/specs/epics/E01-library-management/F01-delete-playing-song/F01-delete-playing-song.plan.md: 83 reference(s) resolved, 9 created, tasks: read, tests: read
exit: 0

$ ./.harness/init.sh            # structure/store/plan-cite/readiness
exit: 0   (one pre-existing warn-only escalation-arming note)
```

Test-drift, with the new waivers (`--tasks-md <this feature's .tasks.md> --feature-scope origin/master`,
head = working tree, as `agents/reviewer.md` check 7 runs it):

```
9561be6b  6 findings, 6 waived  exit 0     0a148494  0 findings            exit 0
b3fa2137  6 findings, 6 waived  exit 0     fe0d190a  1 finding,  1 waived  exit 0
9fb1f19b  2 findings, 2 waived  exit 0     4cb17c6a  1 finding,  1 waived  exit 0
e2207e40  2 findings, 2 waived  exit 0     origin/master (whole feature) 0 findings  exit 0
ef65b0d2  2 findings, 2 waived  exit 0     9fb1f19b^..9fb1f19b  4 findings, 4 waived  exit 0
f68d6eea  0 findings (head=worktree) / 2 findings, 2 waived (pair form)   exit 0
```

**Read one of those before trusting the pattern:** `fe0d190a` and `4cb17c6a` reported **0** findings
when the Reviewer ran them and report **1** now. That single finding is at
`DeleteFromLibraryWiringSourceTest.java:238` — the Builder's *uncommitted* C-A.1 edit to
`testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries`, swept by my file-scoped waiver. At
`4b9498d6` itself those runs are clean. It is exactly the risk the caveat in `.tasks.md` names; the
Reviewer should read that line rather than the exit code, and the waivers should be retired after C-A
lands.

## Per requirement: what executes today, and what is still unproven

- **R1** — Executes (JVM): menu-item statics, the gate's four clauses incl. non-blank server id, and
  both themes' inflate + song-change calls; executes (device, per-test verified): hidden-on-empty-queue
  only. Unproven: the **shown**-when-server-backed leg (self-skipped), and any *semantic* gate — a
  token-complete `setVisible(serverBacked || true)` survives (R2-B1, C-A.5 open).
- **R2** — Executes (JVM): handler-opens-dialog, dialog-issues-no-request in both B2 shapes,
  destructive-copy content, distinct confirm/cancel verbs. Unproven: **every device leg for R2** —
  `DeleteConfirmDialogTest` self-skipped and `e2e4_cancelNoop` has never run in the feature's history
  (G2/G4), so "Cancel is a no-op on a real session" has zero executed evidence.
- **R3** — Executes (JVM): one stock `DeleteItem` call site, no hand-rolled HTTP, no request outside
  the dialog's `onPositive`, no outcome dispatched before the request. Unproven: that one confirmed
  delete puts exactly one `DELETE /Items/{id}` on the wire and the item 404s (`e2e1`, driver — both
  unwritten).
- **R4** — Executes (JVM): SUCCESS-block toast/queue-drop/refresh, the cleanup helper body
  (every-entry removal, current-index awareness, lone-entry stop), the `positionsOfItem` lookup as
  behavior. Unproven: the **descending removal order** (ascending leaves a dead entry and stays GREEN,
  F1, C-A.1), a pre-request success toast or queue edit (F7, C-A.2), and the clean stop / advance on a
  real device (`e2e1` lone-entry leg, `e2e2`).
- **R5** — Executes: forced-401 mapping unit test. Unproven: real non-admin refusal — impossible on
  this server as configured (L1), stated, not hidden.
- **R6** — Executes (JVM): 404 → `ALREADY_GONE` mapping and the branch-scoped cleanup-without-claim
  contract. Unproven: the second-delete-already-gone toast on device (`e2e3`, unwritten).
- **R7** — Executes: transport and other-status mappings + the failure-outcomes contract. Unproven:
  nothing claimed beyond that; no device leg is specified for R7.
- **R8** — Executes (JVM): latch absorbs repeats, arms before the request, releases on every terminal
  outcome, and now survives a **synchronous throw** without wedging or escaping (2 behavior tests, RED
  in both M-1 shapes). Unproven: only the real-network timing shape, which no JVM test can bind.
- **R9** — Executes (JVM): `FavoritesStore.notifyChanged()` pinned inside the SUCCESS block.
  Unproven: the Favorites tab visibly refreshing on device after a real delete (`e2e1` leg).
- **R10** — Executes (JVM): SUCCESS clears the suppression guard for the deleted id, advance sequenced
  before the clear. Unproven: that no `Sessions/Playing/Progress` report ever names the dead id, and
  the restart-cleared pref (needs `e2e5` + the driver + request-log oracle L2).
- **R11** — Executes (device, per-test verified): Room-row purge, all three cached-file removals,
  un-cached no-op; executes (JVM): purge narrowness + logging, success-outcome-only. Unproven: **any
  gate coverage of the file deletions** (delete all three → unit gate GREEN, F2/G5), the
  missing-`songs`-row case (F3, C-A.3), and the class being self-restoring after a broken run (F4,
  C-A.4); the end-to-end cached-state purge (`e2e6`) is unwritten.
- **R12** — Executes (JVM): English-only rule over pattern-derived keys with a non-vacuity floor, and
  the derived list's own non-empty/nine-frozen-keys floor. Unproven: the locale loop can still be
  pointed at a fragment matching nothing and stay GREEN (F5, C-A.5 open).

## Where I might still be optimistic

The word **"executed"** in this matrix means "the Reviewer ran it and reported per-test output on
2026-11-12", not "I re-ran it". I ran no Gradle, no emulator and no mutation myself; the three
"ran" rows in the G4 ledger rest entirely on the round-2 review's logcat readings, and if any of those
was misread the matrix overstates again. The gate counts (148 tests / 0 failures / 1 skip) are quoted
from the review, not re-measured — and the Builder's concurrent `app/**` work means the number has
very likely moved by the time this is committed.
