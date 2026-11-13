# Builder — E01-F01 round 4 (condition C-C1 only)

Date: 2026-11-12 (UTC). Round 4. Test-only change in
`app/src/test/java/club/thatpetbff/gramophone/util/DeleteFromLibraryWiringSourceTest.java`.
No `app/src/main/**` change, no spec edit, no task ticking, no version bump.

## The change (per review-round3 §2 C-A.5, C-C1)

1. **Named scan for the locale loop**: `translatedDeleteKeys(String localeXml)` — it takes *no*
   fragment argument, so the fragment can only come from `KEY_FRAGMENT`; there is exactly one place
   to aim, and the loop calls it.
2. **Positive control** inside `testNewStringsLiveInDefaultEnglishOnly`: plant a real derived key
   into a synthetic `<resources>` document and assert the *same* scan reports it. A scan aimed at a
   fragment that matches nothing now fails `expected:<1> but was:<0>` instead of passing vacuously.

Deliberately **not** used: a `source.contains(...)` textual pin (review-round3 §2 C-A.5 proved
`pinf5a` / `pinf5a2` GREEN — the assertion's own literal satisfies its own `contains`).

## Acceptance list (filled in as observed)

- [ ] `KEY_FRAGMENT + "_ZZZ"` at the locale scan → RED
- [ ] literal `"delete_from_library_ZZZ"` at the locale scan → RED
- [ ] re-wrapped concatenation spelling → RED
- [ ] HEAD stays green (focused class 16/0, then full suite 150 / 0 failures / 1 skip)
- [ ] `compileDebugKotlin compileDebugJavaWithJavac` clean
- [ ] one commit, explicit paths only

## Observed RED evidence (appended live, one entry per mutant)

(none yet)

## Gate runs

(pending)

## Residual bounds

(pending)
All mutants are applied to the *named scan* body only (`translatedDeleteKeys`), which after this
change is the single locale call site. Focused run each time:
`./gradlew testDebugUnitTest --tests '*DeleteFromLibraryWiringSourceTest*'`, read from
`app/build/test-results/testDebugUnitTest/TEST-*DeleteFromLibraryWiringSourceTest.xml`, then the file
is restored from a `cp` backup of the fixed HEAD candidate.

- **m1 — literal `stringKeysMatching(localeXml, "delete_from_library_ZZZ")`** → **RED** (16 tests,
  1 failure): `testNewStringsLiveInDefaultEnglishOnly` —
  `java.lang.AssertionError: the locale scan must find a planted delete_from_library* key, otherwise
  "no translations in any locale" means nothing (R12/F5) expected:<1> but was:<0>`.
- **m2 — `stringKeysMatching(localeXml, KEY_FRAGMENT + "_ZZZ")`** (round 3's `m_f5a2`) → **RED**
  (16 tests, 1 failure), same assertion, same `expected:<1> but was:<0>`.
- **m3a — re-split literal concatenation `stringKeysMatching(localeXml, "delete_from" + "_library" + "_ZZZ")`**
  (round 3's `m_f5c`, spelled so no line repeats the whole fragment) → **RED** (16 tests, 1 failure),
  same assertion, `expected:<1> but was:<0>`. Killed by the positive control alone, not by the
  one-literal pin.
- **m3b — verbatim re-wrap `stringKeysMatching(localeXml, "delete_from_library" + "_ZZZ")`** → **RED**
  (16 tests, **2** failures): `testBothStringKeyCallSitesShareTheOneFragment`
  `expected:<1> but was:<2>` **and** the positive control `expected:<1> but was:<0>`.

- **m4 — bypass, disclosed not fixed:** the named scan left correct while the *loop* calls
  `stringKeysMatching(locale, "delete_from" + "_library_ZZZ")` directly → **GREEN** (16 tests,
  0 failures). So the rule is now aimed at exactly one place, but a fabricated second scan beside
  that place is still uncaught by anything but code review. Candidate one-step hardening for the
  Reviewer to weigh (reasoned here, deliberately not run this round): collapse the two helpers into
  a single no-fragment scan `deleteKeysIn(String xml)` so no locale or default call site can carry a
  fragment argument at all; the positive control would then cover both sides.

## Gate runs

- `./.harness/init.sh` → exit 0 ("harness structure intact", "environment ready"; the pre-existing
  escalation warn-only notice is unchanged and unrelated to this round).
- `./gradlew testDebugUnitTest --tests '*DeleteFromLibraryWiringSourceTest*'` on the fix alone →
  **GREEN 16 tests / 0 failures / 0 skipped** (matches the Reviewer's `fixc1` run).
- `./gradlew testDebugUnitTest` (full suite, `--rerun`) → **150 tests / 0 failures / 1 skipped** —
  baseline held.
- `./gradlew compileDebugKotlin compileDebugJavaWithJavac` → BUILD SUCCESSFUL (no `app/src/main` change).

## Residual bounds

- **m4 above** is the honest limit: the positive control guards the one named scan, not a fabricated
  second scan. No `source.contains(...)` pin was added, per C-C1, because that form is self-satisfying.
- **R3-B1 not taken this round** (task says leave it unless it is one line and both GREEN mutants are
  shown RED; it needs two `contains` clauses plus two mutation runs, and it is exactly the textual-pin
  shape C-C1 warns about, so it deserves its own reasoned review rather than a squeeze-in here).
- The positive control calls `deleteFromLibraryKeys().get(0)`; if the default key list ever became
  empty it fails loudly (IndexOutOfBounds ⇒ error, not a vacuous pass), and
  `testDeleteStringKeyListIsDerivedNotEmpty` already fails first in that case.
