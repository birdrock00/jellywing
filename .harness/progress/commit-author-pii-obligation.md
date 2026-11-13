# Obligation: commit-author PII (personal email) before the next push

Raised 2026-11-13 by the Orchestrator during round 4 of `E01-F01`, while screening commits for the
`AGENTS.md` rule *"Do not upload any PII … or local env references to github.com."*

## What was found

This repository's local git identity was set to the operator's **personal email**. Its local part is
deliberately not reproduced in this file: the domain is recoverable from the app's own package
namespace, so writing the local part here would republish the address this note exists to remove.
The published identity used by upstream contributors is `birdrock00 <…@users.noreply.github.com>`.

Consequences, separated by what is still fixable:

| State | Count | Fixable? |
|---|---|---|
| Unpushed commits carrying the personal author email | ~~30~~ **0** | **DONE 2026-11-13** — rewritten by `filter-branch` to the noreply identity over `origin/master..HEAD`; tree content byte-identical; old→new SHAs in [pii-rewrite-sha-map.md](pii-rewrite-sha-map.md); recoverable from `refs/heads/backup/pre-rewrite-20261113-1333` |
| Pushed commits carrying it, from the 2026-11-12 push of `63af1091..51c060e5` | **12** | Only by rewriting published history |
| Pushed commits carrying it that **predate this session** (earliest 2026-10-14) | **8** | Only by rewriting published history |

Remaining exposure: the 20 commits already in `origin/master` still carry it. Only a rewrite of
published history (force-push, breaking for anyone who cloned) would remove that, which is why it is
left as an explicit operator decision rather than done unilaterally.

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

## Addendum — re-measured 2026-11-13 (round 5), and two corrections to this note

Re-measured against `origin/master..HEAD` rather than trusting the table above.

| Item | This note said | Measured now |
|---|---|---|
| Unpushed commits carrying the personal address | 30 | **30** (unchanged; range is now 41 commits) |
| Pushed commits carrying it on `origin/master` | 20 | **20** (confirmed) |
| Personal address anywhere in tracked **file contents** | — | **0 occurrences.** The exposure is git *metadata* only |

**Correction 1 — "no new commit can repeat it" is not durable.** Plan step 1 asserts the repo-local
identity is corrected, and no new commit has repeated the personal address. But sub-agents commit with
their own identity: `56df4ff4` is authored `harness <harness@local>`. So the fix cannot be "set
`git config` once" — the rewrite must **normalize every author/committer in the range that is not the
published noreply identity**, not substitute one specific address for another. A rewrite scoped to the
personal address alone would publish `harness@local` and leave a second broken identity behind.

**Correction 2 — the address is reconstructable, so scrubbing this file is not a mitigation.** The
domain is the app's own package namespace (`club.thatpetbff` appears in 219 tracked Java files) and the
local part is written above. Concatenating them yields the address, and this file is tracked. Treat the
published-address question as settled by the metadata, and decide separately how much of *this* document
should ever be pushed — it also states that the address is the Jellyfin account name, which pairs an
identifier with the server details documented elsewhere in this repo (fork version, auth configuration).

**Newly noticed, separate from the above.** The QA fixture's real names — the track name and the real
playlist name — are committed in 5 tracked files, including `tools/qa-delete-cycle.sh` and the feature's
`.tests.md`. They are load-bearing for the fixture and for the driver defaults, so removing them is a
spec change, not a scrub. Flagged for an explicit operator decision rather than fixed silently, because
it is the operator's own library content being published against their name.

Verified clean and unlikely: no IPs, device serials, Jellyfin item GUIDs, tokens or secrets in tracked
content; `local.properties` is gitignored and holds only `sdk.dir`; no `/home/ec2-user` paths in tracked
files. One secrets-shaped regex hit in `AbsMusicServiceActivity.java` is a false positive
(`serviceToken = MusicPlayerRemote.bindToService`).
