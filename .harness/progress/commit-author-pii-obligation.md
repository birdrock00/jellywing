# Obligation: commit-author PII (personal email) before the next push

Raised 2026-11-13 by the Orchestrator during round 4 of `E01-F01`, while screening commits for the
`AGENTS.md` rule *"Do not upload any PII … or local env references to github.com."*

## What was found

This repository's local git identity is set to the operator's **personal email**
(`brucey@…`, i.e. the same address that is the Jellyfin account name), while the published identity used
by upstream contributors is `birdrock00 <…@users.noreply.github.com>`.

Consequences, separated by what is still fixable:

| State | Count | Fixable? |
|---|---|---|
| Unpushed commits carrying the personal author email | **30** | Yes — rewrite before pushing, no force-push needed |
| Pushed commits carrying it, from the 2026-11-12 push of `63af1091..51c060e5` | **12** | Only by rewriting published history |
| Pushed commits carrying it that **predate this session** (earliest 2026-10-14) | **8** | Only by rewriting published history |

So the address is **already public** in this repository's history through commits made before this
session began. That does not make the 30 unpushed ones acceptable, and it does not erase that the
2026-11-12 push added 12 more.

## Why I stopped instead of fixing it

Two things were in progress that make a history rewrite unsafe right now:

1. An Architect run has uncommitted edits under `.harness/specs/`. A rewrite needs a clean tree and
   would sweep those edits in unpredictably.
2. Rewriting changes every commit SHA in the range. The review reports, telemetry records and matrix
   rows cite those SHAs as mutation evidence — `9561be6b`, `e88b33fa`, `fe54b230`, `738820b1` and about
   two dozen others. A rewrite without an ID map turns that audit trail into dangling references.

## Plan, on approval

1. Repo-local identity is **already corrected** to the noreply identity, so no new commit can repeat it.
2. After the Architect's edits are committed and no agent is mid-commit: rewrite author and committer on
   `origin/master..HEAD`, verify `git diff <pre-rewrite> HEAD` is empty (metadata-only change), then
   confirm zero hits for the address in the range.
3. Write a **tracked old→new SHA map** in this directory, and add a line to `history.md`, so every hash
   cited in the review reports remains resolvable.
4. Push only after 2 and 3 are green.

## Decision left to the operator

Whether to also rewrite the **published** history (20 commits, including 8 from before this session).
That requires a force-push to `master`, rewrites other people's view of a shared branch, and the address
is very likely indexed already. My recommendation: **do not** force-push; fix the unpushed range, keep
the identity correct going forward, and treat the published occurrences as a known, disclosed state.

Independent of this, the two passwords leaked into chat earlier still need rotation.
