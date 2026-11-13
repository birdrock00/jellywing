# E01-F01 Review Round 4 (focused verification)

Scope: C-C1 mutation re-run + C-C2 truth-check + R3-B1 ruling + final executable C-B checklist.
Not a re-review of the feature. No commits, no status writes, no destructive deletes, no P0-P8 run.

## 0. Acceptance list (to be filled in place)
- [ ] A1 C-C1 re-run: mutants a/b/c/d each independently RED; HEAD green focused 16/0 and full 150/0/1; app/ clean.
- [ ] A2 C-C2 truth-check: device-leg count reconciled; found.add(i) vs found.add(0,i); rows upgraded to CLEARED citing mutants; R3-B1/R2-B1 survivors + lexical-region bound present; Orchestrator status mirrors not overstated.
- [ ] A3 Verdict on "Reviewer re-run pending" rows: upgradeable or not, with exact wording.
- [ ] A4 R3-B1 ruling: accept intrinsic bound vs require one-line hardening, with one grounded reason.
- [ ] A5 Final executable C-B checklist (verbatim-runnable by Orchestrator), incl. /Playlists/{id}/Items?UserId= gotcha, pre-flight state, e2e authoring needs, per-test evidence, Orchestrator vs operator split, abort conditions.
- [ ] A6 Verdict line.

## 1. C-C1 re-run
(result appended below)

## 2. C-C2 truth-check
(result appended below)

## 3. R3-B1 ruling
(result appended below)

## 4. Final executable C-B checklist
(result appended below)

## 5. Verdict
(pending)
Baseline before mutating: focused class at HEAD -> 16 tests / 0 failures (gradle rc 0), tree clean.
Mutants applied to the body of `translatedDeleteKeys(String localeXml)` (the single named locale scan,
test file only), each restored from /tmp cp backup immediately after its run; focused run =
`./gradlew testDebugUnitTest --tests '*DeleteFromLibraryWiringSourceTest*' --tests
'*LibraryDeleteDuplicateQueueTest*'`, read from the class XML (not from the Builder's report).

| # | Mutant (locale scan aimed elsewhere) | Result | Failing assertion(s) | Builder round-4 claim | Verdict on claim |
|---|---|---|---|---|---|
| ma | `stringKeysMatching(localeXml, "delete_from_library_ZZZ")` | **RED** (16 tests, 1 failure) | `testNewStringsLiveInDefaultEnglishOnly` — "the locale scan must find a planted delete_from_library* key … expected:<1> but was:<0>" | RED, 1 failure | confirmed |
| mb | `stringKeysMatching(localeXml, KEY_FRAGMENT + "_ZZZ")` | **RED** (16, 1 failure) | same assertion, `expected:<1> but was:<0>` | RED, 1 failure | confirmed |
| mc | `stringKeysMatching(localeXml, "delete_from" + "_library" + "_ZZZ")` | **RED** (16, **1** failure) | positive control only | "killed by the positive control alone, not by the one-literal pin" | **confirmed true** — exactly one failure; the pin counts lines containing the quoted `"delete_from_library"`, and this spelling adds none, so the pin stays green. The control is doing all the work, which is what C-C1 asked for. |
| md | `stringKeysMatching(localeXml, "delete_from_library" + "_ZZZ")` (verbatim re-wrap) | **RED** (16, **2** failures) | `testBothStringKeyCallSitesShareTheOneFragment` (`a second "delete_from_library" literal …`, expected 1 got 2) **and** the positive control `expected:<1> but was:<0>` | 2 failures: pin 1!=2 and the control | confirmed |

All four round-3 F5 spellings (m_f5a / m_f5a2 / m_f5c and the identical-literal m_f5d family) are now RED;
the vacuity C-C1 named is closed. The named scan takes no fragment parameter, so the only way to aim it
at nothing is to edit that one body, which the positive control in the same test covers.

### 1e. HEAD green after restore (re-run, not inherited)

- Focused classes at HEAD after all four mutants: `DeleteFromLibraryWiringSourceTest` 16 / 0 failures,
  `LibraryDeleteDuplicateQueueTest` 3 / 0 (gradle rc 0).
- Full `./gradlew testDebugUnitTest --rerun` at HEAD (all 31 result XMLs re-written in one run,
  timestamps verified fresh): **150 tests / 0 failures / 0 errors / 1 skipped** — baseline held.
- `git status --porcelain` empty after the campaign; both mutated files restored from `cp` backups
  (`/tmp/HEAD_*.bak`), so `app/` is clean and nothing was committed.
- Not independently re-run (Builder-disclosed, and consistent with the design): the **m4 bypass** — named
  scan left correct while the *loop* calls `stringKeysMatching(locale, ...)` directly with a dead
  fragment — Builder reported GREEN and I have no reason to doubt it, because the positive control
  exercises the named scan only. It stays a stated bound of a source-contract gate; the fix for it is
  the Builder's own `deleteKeysIn(String xml)` consolidation, which is not required by C-C1.

**C-C1 ruling: CLEARED.** All four spellings independently RED at the assertion C-C1 specified, the
concatenation killed by the control alone exactly as the Builder claimed, HEAD green in both scopes,
and the implementation avoided the self-satisfying `source.contains(...)` trap.

## 2. Is the refreshed matrix (C-C2) *true*?

Checked against the shipped source, not against the Architect's notes. Findings by the item I filed in
round-3 §3 / §6:

| Check | Where in `.tests.md` | Truth |
|---|---|---|
| Four-vs-three device-leg contradiction | header bullet at `:37-42` + G4 ledger `:139` | **TRUE, not cosmetic.** Header now says **three** of six skipped, names all three (`deleteItemShownWhenServerBackedSongIsCurrent`, `DeleteConfirmDialogTest`, `e2e4_cancelNoop`) with the reason for each, and states the earlier "four" was the error. It agrees with the G4 ledger's "three executed and three did not", and with the per-row Exists? cells (R1 hidden-leg executed, R11 legs executed). Internally consistent. |
| `found.add(i)` vs the `found.add(0, i)` mutant | R4 duplicate row `:65` | **TRUE.** `app/src/main/java/.../LibraryDeleteUtil.java:241` is `found.add(i)`; I grepped it — `found.add(0,` does not occur in the file. The row cites the shipped line and labels `found.add(0, i)` explicitly as the mutant that is RED on both duplicate legs. The mis-citation class is closed. |
| Rows upgraded to CLEARED, each citing the mutant it rests on | SUPERSEDED block `:164-182` | **TRUE.** C-A.1 cites the inline-ascending RED at `:291` and the ascending-helper RED at `:56`; C-A.2 cites `m_f7a`-`m_f7e` RED at `:382` plus the lexical-region bound; C-A.3 cites the device RED at `CachePurgeOnDeleteInstrumentedTest.java:109`; C-A.4 cites the two-run self-restoring failure and the `reclaimStaleFixture` reproduction; C-A.5/R2-B1 cites `:166` and names the surviving `serverBacked = song != null \|\| (…)` shape. No row is cleared on prose. |
| R3-B1 survivor present | `:212` + `:169-171` + behavioural summary `:377` | **Present**, and correctly described as "the order is executed-pinned, the consumption only textual", with the behavioral backstop pointed at C-B B5's lone-entry leg. |
| R2-B1 survivor present | R1 gate row `:54`, `:213` | **Present**, with the exact surviving shape spelled out and the reason it survives (disjunct folded into the initializer). |
| Lexical-region bound (C-A.2) | `:170`, `:213` | **Present** as one of "three stated textual bounds — R3-B1, the R2-B1 survivor, the lexical extent of C-A.2's pre-request region". |
| Purge-sabotage split out of the shipping set | `:389-399` | **TRUE**, and emphatic: "Explicitly NOT a C-B step (do not do this to a shipping tree)", with the round-3 evidence recorded as already performed. |
| `waive-test-drift:` line for `CachePurgeOnDeleteInstrumentedTest.java` | `.tasks.md:97` | **Present** and substantiated (15 `assert*` sites vs 10 at the pair base; the moved assertions still fire). Note `tools/…`-style invocation needs a base ref (`test-drift-check.sh <base-ref> --tasks-md <file>`); I did not re-run it with the pair SHA, so I take exit 0 from the Orchestrator's gate log, not from my own run. |
| Orchestrator's status mirror overstates anything? | R12 row `:85` Exists? cell, CLOSED block `:189-205` | **No.** Both places say explicitly "Builder-run evidence; the round-4 Reviewer re-run is still owed before this row is quoted as reviewer-verified" and "Not yet Reviewer-confirmed". The mirrored numbers (four mutants RED; concatenation killed by the control alone; verbatim re-wrap twice; focused 16/0; full suite 150/0/1) are all now independently reproduced in §1 above, so the mirror is accurate — but the hedge is honest and is the right shape for mirrored status. |

### 2a. The two rows left at "Reviewer re-run pending"

They can be upgraded now. §1 is a Reviewer-run re-creation of all four mutants with the same
results, plus independent confirmation of the focused and full-suite baselines. Suggested wording, for
the Orchestrator or a later Architect pass to apply (I did not edit the spec — Architect-owned):

- R12 locale row, Exists? cell: `yes (R2-B1 cleared; F5 CLOSED at 738820b1 — **Reviewer re-ran all four
  mutants in round 4: each RED (the concatenation by the positive control alone, the verbatim re-wrap
  twice), focused class 16/0, full suite 150/0/1, app/ clean**)`
- CLOSED — C-C1 block: replace "Not yet Reviewer-confirmed: this closure rests on Builder-run evidence,
  so round 4 must re-run the four mutants before the row is quoted as reviewer-verified" with
  "**Reviewer-confirmed (round 4, `.harness/progress/20261112-E01-F01/review-round4.md` §1): all four
  mutants re-run independently, same results; HEAD green focused and full.**"
Nothing else in the matrix needs a status change from this round; R3-B1 stays a stated bound (see §3),
and no C-B row may be ticked by anything in this report.

## 3. Ruling on R3-B1 — accepted as an intrinsic bound; the one-line hardening is **not** required

I did not rule on principle; I ran it. The hardening was applied temporarily to
`testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries` (two `assertTrue` clauses on the
extracted `removeFromQueueAndAdvance` body), each mutant applied to `LibraryDeleteUtil.java` from a `cp`
backup and restored; nothing committed, tree clean at the end.

| Run | Shape | Result |
|---|---|---|
| h0 | hardening alone at HEAD | **GREEN** (16/0, plus `LibraryDeleteDuplicateQueueTest` 3/0) — so it is safe to add |
| d1 | call site calls the helper and ignores its result (`removalOrder(positions); List<Integer> order = positions;`) | **RED** — "binding" clause (this is round 3's `m_f1d`) |
| d2 | loop header inverted (`for (int i = order.size() - 1; i >= 0; i--)`) | **RED** — "loop" clause |
| d3 | header and binding verbatim, **direction flipped in the index**: `removeFromQueue(order.get(order.size() - 1 - i));` | **GREEN with the hardening and GREEN without it** |

**Reason, grounded in what the pin actually pins:** the two clauses pin the *form* of the call site —
that a local named `order` is bound from `removalOrder(positions)` and that the loop header counts
upward — and `d3` keeps both strings character-for-character while iterating `order` from the far end,
which *is* the exact defect the pin exists to prevent (a lower index removed first shifts the next one
out of range, `removeFromQueue`'s `0 <= p < size()` guard silently refuses it, and a dead entry stays
queued with the suite green). So the hardening converts two sloppy shapes into failures and leaves the
one deliberate shape untouched; requiring it would add two more textual assertions of precisely the kind
this feature just spent a round learning can carry no meaning, and would let the matrix read "call-site
consumption pinned" while `d3` — a real regression — still ships green. The direction of consumption is
only observable by executing it, and executing it needs a seam through the
`MusicPlayerRemote` statics (`removeFromQueue`/`getPlayingQueue`/`getPosition`), i.e. a production
refactor for testability plus a fake queue, not one line.

**Ruling: accept R3-B1 as an intrinsic bound of a source-contract gate.** Condition of acceptance,
doc-only and one line: the matrix bound at `.tests.md:212` / `:169-171` must name the shape, not just
the category, so no later reader assumes the hardening is outstanding and sufficient — add
"`d3`: `order.get(order.size() - 1 - i)` keeps both pinned lines verbatim and stays GREEN with the
hardening applied, so the hardening was rejected as insufficient rather than left undone; the executed
backstop is C-B B5's lone-entry / twice-queued leg". Recorded as **Minor, accepted**; not a shipping
condition.

## 4. Final executable C-B checklist (the Orchestrator runs this verbatim)

Kept in round-3's structure (B1 author → B2 evidence rule → B3 device legs → B4-B8 destructive cycle +
E2E legs → B9 teardown/gate), corrected for what pre-flight found. **Destructive.** Nothing in §1-§3
ticks any row here.

### Rules that override everything below
- **R-EV Per-test evidence or it did not happen.** Every instrumented/E2E leg needs its own
  `started:` and `finished:` lines in logcat (or the `am instrument` per-test stream). Any
  `assumption failed` / `ignored` line for a leg ⇒ that leg is **NOT RUN**, and the class result may not
  be quoted as `OK (n tests)`. Both runners report a self-skip as success; that is the failure mode this
  whole feature has been fighting.
- **R-NONASTR Any "assumption failed" is a NOT RUN, never a pass; any step that cannot be executed as
  written is a gap to report, never a step to improvise around.**
- **R-SEC** Server URL, user and token come only from the operator's runtime procedure
  (`progress/qa-emulator.md`) held in the operator's own credential store. The Orchestrator never reads
  that store, never echoes a token into a log, and never writes a hostname, user id, email, device
  serial, NAS path or token into any repo file, driver log, commit message or QA report. Driver logs
  live outside the repo.
- **R-NODELTA** No edit to `app/**` at any point in C-B. A run that requires sabotaging production code
  first is a mutation campaign, not a shipping check (the purge-sabotage probe was round 3's review-time
  work and is already done — see `.tests.md:389-399`).
- **R-NOREBOOT** Emulator only; never reboot a physical device; never use AGP
  `connectedAndroidTest` (round 3's route: `adb install -r` + `am instrument -w -e class …`).

### B1 — Author the driver and the five missing E2E cases (before any destructive call)
- **B1.1** `tools/qa-delete-cycle.sh` does **not exist** (verified: `tools/` contains only
  `classify.py`, `language-hints.json`, `test_bootstrap.sh`). It must be written first and implements
  P0→P8 exactly as `.tests.md:268-300` specifies. Owner: Orchestrator (author), operator (supplies the
  runtime credential/admin procedure reference only).
- **B1.2** Only `e2e4_cancelNoop` exists in `app/src/androidTest/java/club/thatpetbff/gramophone/ui/`
  `` `DeleteFromLibraryE2eTest.java` ` (verified: one `@Test`, `e2e4_cancelNoop`, line 77) and it has
  **never executed** — it `assumeTrue`s on `qa_server/qa_token/qa_user/qa_item` instrument args that no
  driver supplies yet. **`e2e1_happyPathCycle`, `e2e2_playlistCleanAfterScan`,
  `e2e3_alreadyGoneSecondDelete`, `e2e5_noSessionReportsForDeadId` and `e2e6_cachedSongPurged` must be
  authored alongside the driver** (e2e1/e2e2 are also missing, not just the three named by round-3
  B7/B8). No E2E row may be ticked before its method exists *and* runs with evidence.
- **B1.3 Endpoint gotcha the driver MUST use (pre-flight discovery).** Playlist membership is read with
  **`GET /Playlists/{playlistId}/Items?UserId={user}&Fields=…`**. `GET /Items/{playlistId}/Items` returns
  **HTTP 404** for a playlist id. A P0/P3/P6 probe written against the wrong endpoint reports the fixture
  as held by **no** playlist — which under P0's abort rule would abort a perfectly valid run, and under
  P3's assert rule would "prove" cleanliness that never happened. Encode the correct endpoint in the
  driver and make the driver **fail loudly** if a holder-list query returns 404 or an empty item list,
  rather than treating it as "not in any playlist".
- **B1.4** The driver takes the fixture by *name* ("<redacted fixture track>" in playlist `<redacted fixture playlist>`) and
  resolves the id at run time; it must refuse to act if the name resolves to zero or more than one
  Audio record. Never substitute another item.

### B2 — Pre-flight (already verified 2026-11-13; re-run the cheap parts, do not re-do the destructive parts)
Verified today, and re-confirmed by me where I could do so harmlessly:
- sacrificial item present, exactly one Audio record, downloadable (ranged GET → 206, m4a bytes);
- **H = exactly one playlist** (`<redacted fixture playlist>`) holding it, **4 entries**, matching the host backup
  snapshot; backup file present with **sha256 recorded and OK**;
- emulator online (I re-checked `adb devices`: exactly one emulator device, no physical serial recorded);
- server reachable.
**Abort (pre-flight):** any of — fixture absent; fixture resolves to >1 item; H empty *or* H does not
match the recorded snapshot; backup file missing or sha256 mismatch; fixture flagged as an operator
favorite; emulator not the sole target device; server unreachable. Fix the fixture, or stop and report.
**Do not proceed with a different item, a different holder set, or a "close enough" backup.**

### B3 — Non-destructive device legs (as committed, live session first)
Run each with per-test evidence; a live app session is a precondition, otherwise the leg self-skips:
- `CachePurgeOnDeleteInstrumentedTest` — **all three legs as shipping legs**:
  `purgeRemovesCacheRowAndCachedFiles`, `purgeOfUncachedItemIsANoOp`,
  `purgeRemovesCacheRowWhenTheSongsRowIsAbsent` (R11's file removals have no other pin anywhere —
  deleting the three `File.delete()` lines leaves the JVM gate green).
- `DeleteMenuItemVisibilityTest` — **both** legs, including
  `deleteItemShownWhenServerBackedSongIsCurrent` (the leg that self-skipped on 2026-11-12; R1's
  shown-when-server-backed property has no other proof — this leg is the real answer to the R2-B1
  survivor).
- `DeleteConfirmDialogTest` — confirm/copy legs.
- `e2e4_cancelNoop` — first-ever execution, with the four instrument args supplied by the driver;
  non-destructive but runs P0 and P7 as guard rails.
Abort if any of these report a skip; the run is incomplete, not green.

### B4-B8 — The destructive cycle (P0→P8), once per E2E case, in this order
| Step | Who | What | Evidence / abort |
|---|---|---|---|
| **B4 = P0+P1** | Orchestrator (with operator-supplied token at runtime) | probe policy; resolve fixture id; enumerate H **via B1.3's endpoint** + entry counts; ranged-GET backup outside repo and outside any library share; record sha256 | abort per B2 |
| **B5 = e2e1 happy path** (lone-entry leg **first**) | Orchestrator on emulator | play the track **by itself**, delete, confirm; assert playback stops cleanly and no dead item is presented (this is the executed backstop for **R3-B1** and for R4's where-clause); then the from-a-playlist cycle | per-test `started:`/`finished:` + server `GET /Items/{id}` → **404** |
| **B6 = e2e2 playlist clean after scan** | **library scan trigger is an operator step** (or an operator-run `POST /Library/Refresh` under the operator's procedure if they delegate the call); Orchestrator polls and asserts | after delete: every holder in H no longer lists the id; after scan + completion, no holder resolves the dead id; the stale `.m3u8` leg (L4) | abort if the scan is not triggered *and completed* — asserting cleanliness without a scan is the vacuity this feature keeps hitting |
| **B7 = e2e3 already-gone second delete** (R6) | Orchestrator | within one cycle, point the app at the same dead id again; assert the toast says *already gone*, distinct from success, item stays out of the queue; then P5→P8 | abort mid-cycle ⇒ run P5 restore immediately, then report; never leave the item deleted |
| **B8a = P5 restore** | **operator only** — the server-admin shell procedure that places the backup file back into its original library folder is not an app mechanism and not an Orchestrator capability; Orchestrator polls until re-indexed and records the **new** id | abort if the item does not re-index in the expected window: **STOP, do not retry the delete, hand the operator the backup path + sha256** |
| **B8b = P6 re-add** | Orchestrator (stock add-to-playlist with the new id) | every holder in H back to its P0 entry count (order recorded, not asserted) | abort on any count mismatch |
| **B8c = P7 as-found audit** | Orchestrator | item exists exactly once; every H holds it; **no playlist outside H changed**; assert-as-facts (never as failures): the restored id is **new**, and favorites/play-history do **not** survive — a test asserting favorites survive is wrong by design | any P7 failure ⇒ the cycle is NOT COMPLETE and the feature does not ship until state is repaired with the operator |
| **B9 = e2e5 / e2e6** | Orchestrator (authored in B1.2) | e2e5 (R10): after delete-and-advance, no `Sessions/Playing/Progress` request line names the dead id for 30 s (request-log leg L2), and the `favorites_guard` suppression pref no longer names it after restart. e2e6 (R11): cache the fixture via the app's own cache action, verify cached state, run the cycle, assert cached state gone in-app **and** the on-disk cached file removed | each embedded in a full P0→P8 cycle; per-test evidence; abort same rules |
| **B10 = teardown + report** | Orchestrator | driver logs and backups stay outside the repo; emulator reset by relaunch only (no reboot); QA report records every leg with its `started:`/`finished:` lines, the H set by count, the before/after sha256, and **no** hostname / id / email / token / serial / path | report with any leg missing ⇒ C-B is not satisfied |

### What C-B satisfaction means
Every leg in B3 executed with per-test evidence and no assumption failure, **and** at least one complete
P0→P8 cycle (B4→B8c) with a clean as-found audit, **and** e2e1-e2e6 each executed at least once after
being authored. Until then the honest status of R3/R4/R9/R10/R11-file-removals/R1-shown-leg is **NOT
DEMONSTRATED**, whatever the unit gate says. The version bump does not ship before that.

## 5. Residual risks (carried, none of them new blockers)
1. **m4 bypass (Minor, disclosed by the Builder, not re-run by me):** a fabricated second locale scan
   beside the named one stays uncaught. Consolidating to a single `deleteKeysIn(String xml)` would close
   it; the positive control would then cover both sides. Not required this round.
2. **d3 (Minor, accepted per §3):** the call site's consumption direction is unpinable textually; the
   executed backstop is B5's lone-entry leg.
3. **R2-B1 survivor (`serverBacked = song != null || (…)`)**: unchanged, intrinsic to a source-contract
   gate; B3's `deleteItemShownWhenServerBackedSongIsCurrent` leg is the real proof and is now mandatory.
4. **C-B is the whole remaining risk surface:** the driver and five E2E cases do not exist yet, and the
   destructive cycle has never run end-to-end. Pre-flight is green but pre-flight is not coverage.
5. **I did not re-run `test-drift-check.sh` with the pair SHA** (it needs `<base-ref>`); the
   `waive-test-drift` entries are verified present and substantiated, exit 0 is taken from the
   Orchestrator's gate log.
6. Round-3's `waive-test-drift` / `.tasks.md` status ticks were made by other roles; I verified their
   presence, not their authorship policy.

## 6. Verdict

C-C1 is closed on Reviewer-run evidence: all four spellings RED, the concatenation killed by the positive
control alone, HEAD green focused (16/0) and full (150/0/1), `app/` clean, nothing committed. C-C2 is
true, not merely present: the four-vs-three contradiction is reconciled against the ledger, `found.add(i)`
is the shipped line at `LibraryDeleteUtil.java:241` with `found.add(0, i)` correctly labelled as the
mutant, every CLEARED row cites the mutant it rests on, and the R3-B1 survivor, the R2-B1 survivor and
the lexical-region bound are all stated. The two "Reviewer re-run pending" markers can be upgraded, with
the wording in §2a. R3-B1 is accepted as an intrinsic bound on the demonstrated ground that the proposed
hardening leaves the shape that matters (`order.get(order.size() - 1 - i)`) green.

**VERDICT: APPROVE WITH CONDITIONS**

C-B is now the only remaining shipping gate — the destructive P0→P8 delete-and-restore cycle with
per-test device evidence — and the §4 checklist is safe to execute verbatim as written, provided the
operator performs the two steps marked operator-only (the library scan trigger and the P5 admin restore
path) and the abort rules in B2/B8a/B8c are honored rather than worked around.
