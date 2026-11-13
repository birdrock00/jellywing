# Why this took a day — measured, and what to change

Date: 2026-11-13. Feature: `E01-F01` ("delete the playing song from the library").
Thesis, in one line: **~64% of a 14-hour day was one local 4-bit model generating tokens at ~19/s,
and the product code it was generating was finished after 4 of those hours.**
Every number below is measured from `git`, `.harness/telemetry.jsonl`, and the subagent run
transcripts under `~/.pi/agent/sessions/` and `/tmp/pi-subagents-uid-1000/async-subagent-runs/`.
Where I estimated, I say so.

## The headline: it was not the process. It was 17 tokens/second.

| Measured | Value |
|---|---|
| Wall clock, first to last `E01-F01` commit | **14.3 h** (09:14 → 23:32 local) |
| Subagent generation **inside that window** (23 runs, 1,048 turns) | **9.21 h** — **64% of the day**, and a **lower bound**: it excludes my own orchestrator turns, which are not timed anywhere |
| Output rate | **~19 tokens/s** (2.56 M chars generated ÷ 33,156 s) |

For scale, the *whole* day across both sessions on this repo (including the earlier `E00-F01`
work) generated 2.96 M thinking + 154 k visible chars in **12.08 h** of model time. Do not quote
12.08 h as this feature's cost; the in-window figure is 9.21 h.
| Model | local `Qwen3.8-Flash-Next-UD-Q4_K_XL` GGUF, 4 shards, provider `strix-llama` |
| Model events on that GGUF | 5,715 of 5,715 — every agent, every round |

At least 64% of the day was one quantized local model writing tokens at ~19/s. That is the dominant
cost by a wide margin, and it is a **hardware/model** fact, not a harness-design fact. A hosted model
at 100–150 tok/s does the identical work in roughly **1.5–2 h**. Nothing in a project layout can beat
that lever; most layout changes are worth tens of percent, this one is worth ~6×.

## The second finding: 19× more thinking than saying

| Role | gen hours | thinking chars | visible chars | ratio |
|---|---|---|---|---|
| builder | 4.87 h | 960 k | 66 k | 14:1 |
| **reviewer** | **3.97 h** | **1,648 k** | **63 k** | **26:1** |
| architect | 2.92 h | 335 k | 23 k | 14:1 |
| scout | 0.33 h | 13 k | ~0 | — |
| **total (both sessions)** | **12.08 h** | **2.95 M** | **154 k** | **19:1** |
| **inside the feature window only** | **9.21 h** | **2.44 M** | **117 k** | **20.9:1** |

The Reviewer is the worst offender and the biggest single lever: it burns ~1 h of generation per
round to emit a ~77-line report, thinking 26× more than it writes. Its reasoning is genuinely
useful — see F1 and C-1 below — but it is being paid for at 17 tok/s in a role whose *output* is a
page of text.

## The third finding: the product was finished in 4 hours

```
09:14  T1 …        first product commit
13:14  e88b33fa    LAST commit touching app/src/main   ← feature behaviour complete
23:32  274f41c6    where I am now
```

**360 lines of product code, done at 13:14.** Everything after — **26 commits and ~10 hours** — was
test harness, a QA driver, prose, and bookkeeping. Not one additional line of product behaviour.

| Path | Lines (unpushed range) | Shipped behaviour |
|---|---|---|
| `app/src/main` | +360 | yes |
| `app/src/androidTest` | +1,696 | **never executed** |
| `tools/qa-delete-cycle.sh` | +668 | **never executed** |
| `.harness/progress` (prose) | +2,111 | no |
| `.harness/specs` | +964 | no |

Process prose runs ~8.5:1 against product code — and that is only the *visible* prose. The thinking
behind it is another 19×.

**And the thing we were all working toward still cannot be done by an agent:** C-B needs
operator-supplied `QA_SERVER`/`QA_TOKEN`/`QA_USER`, a human P5 restore, and a human-triggered scan.
We spent 10 hours building an apparatus whose terminal step is manual.

## Root causes, ranked by what they'd actually save

1. **Model throughput (≥9.2 h).** Local Q4 GGUF at ~19 tok/s. Biggest term. Fix: run the
   reasoning-heavy roles on a hosted model, keep local for mechanical edits. Expected: 14 h → ~2–3 h.
2. **Uncapped thinking in report-shaped roles (~3–4 h).** 26:1 on reviewer. Fix: lower thinking
   budget for reviewer/scout; reserve high budgets for the builder's actual bug-hunting.
3. **Verification done by agents instead of by machines (~3–4 h).** This is the real design flaw.
   The harness treats *mistakes* as the scarce resource and buys safety with agent review rounds.
   But on this host the scarce resource is **token latency**, so agent-round verification is the most
   expensive verification available. Both of the round's best findings are deterministic and cost
   ~zero if a script owns them:
   - **F1** — the driver polled for a scan inside a *blocking* `am instrument -w` and only POSTed it
     afterwards. A 5-line static check ("does any phase await state produced by a later phase?")
     catches it.
   - **C-1** — `p3b_scan`'s membership reads dropped `&Limit=1000` and the `X-QA-Read` cache-buster
     that the deleted in-test read carried, and are the only 2 of 5 call sites that never cross-check
     `TotalRecordCount`. A grep for "membership read without `Limit` + total check" catches it.
     7 bare reads exist today.
4. **Nothing was ever executed (worth ~10 h of rework).** 668 lines of bash authored, reviewed twice,
   run zero times. `bash -n` + `shellcheck` + a `--dry-run` against a **stub server** would have
   surfaced F1/F2/F3/C-1 in minutes, before any review round existed. Authoring-before-executing is
   what made six rounds necessary.
5. **Prose as a deliverable (~2 h).** `history.md`, `lessons.md`, `review-round*.md`, builder notes.
   Some is real institutional memory (the poisoned-fixture and AGP-uninstall lessons have saved
   repeats), but most is narration an LLM writes at 17 tok/s. Gates should emit **one line of JSON**,
   not a paragraph.
6. **Bookkeeping commits: 16 of 50.** A commit per status change, per history line, per lesson.
   These are state transitions, not versions. Put them in the TaskStore, not `git log`.
7. **Serial-only execution.** All runs shared one working tree and one git index, so I deliberately
   serialized them (a real recorded lesson). Worktrees would let independent lanes run in parallel —
   but note this multiplies local-GPU contention, so with one local model it buys less than it looks.
8. **Brief sizing.** 4 subagent deaths at `stopReason=length` with zero deliverables (~1–2 h).
   Already fixed: pre-digest context *into* the brief, one file per run, "commit partial beats empty
   tree." Recorded in `lessons.md`.

## What is genuinely NOT the problem

I want to be honest about this, because "slow multi-agent harness" is the obvious accusation and it's
partly wrong:

- **The review loop found real, shipping-blocking defects.** F1 would have burned a destructive
  delete and a manual restore against working code. C-1 is a forgeable green — a truncated or cached
  read reporting "playlist clean" when it merely didn't look. Round 6 also *disproved* my own
  assumption that moving an assertion might have weakened it (it's strictly wider), and it probed the
  grip by extraction with `api`/`sleep`/`die` stubbed instead of just reading it. That is not theatre.
- **Agents were not idle.** Measured per-run spans average ~95% busy, and the 64% in-window figure
  above is generation *only* — the remainder is my orchestration, not waiting. I initially assumed
  coordination overhead was the problem and the data refuted me. There was no slacking to reclaim;
  there was only expensive work, plus work that should not have existed at all.
- **62% of the 29 telemetry rows have no duration**, so any "X% was coordination" claim made from
  `telemetry.jsonl` alone would be wrong. The turn-level transcript timing above is the real measure.

## Recommended target system

Keep the harness's *invariants*, replace its *expensive habits*. In priority order:

1. **Put a fast model on the reasoning roles.** Single largest win. Local model for mechanical
   edits/greps only.
2. **Make the reviewer a `Stop` hook, not a round.** The mechanism exists and is verified in two
   places (Claude Code hooks, OpenHands hooks): when the agent tries to declare done, a shell command
   runs and **exit code 2 means it may not finish**. That converts a 1-hour agent round into a shell
   exit code. Pair it with OpenHands-`/goal` semantics — a judge that must see **green command output,
   not prose** — and cap self-critique at 2 iterations before escalating. Below it, keep fail-fast
   static checks in `tools/`:
   phase-ordering await check, `Limit`/`TotalRecordCount` membership-read check, `bash -n`,
   `shellcheck`, `assumeTrue`-absence scan, secret/PII scan. Cost: seconds. Replaces: a review round.
3. **Execute before you review.** `--dry-run` driver against a stubbed server; run the JVM tests;
   only then ask a reviewer. An artifact that has never run is not reviewable — it's guesswork, which
   is why we needed 6 rounds.
4. **One review round, adversarial, with a budget cap** — plus an explicit "no more than N findings
   of severity ≥ X" rule. Approve-with-conditions that bounces to a new builder round is where the
   rounds accumulate; batch conditions instead.
5. **Kill narration artifacts.** Machine-emitted JSON status; no `history.md` paragraphs; commit only
   evidence (`review-roundN.md`), never briefs (already gitignored — correct call by the layout).
6. **Fix the human gate early.** The moment a feature's terminal step needs credentials, say so in
   the plan and either (a) get a non-destructive substitute, or (b) stop and ask, *before* building
   2,364 lines of apparatus on the far side of it. This is the single "did we even need to do this"
   question the harness never asks.
7. **Cap prose at ~1 line per gate** and put state transitions in the TaskStore instead of commits.

Realistic target for a feature of this size: **~2–3 h** (fast model + gates + one review), vs 14 h.
The product behaviour alone took 4 h and was always going to; the other 10 was process weight.

## External comparison

A research run evaluated 12 alternatives against this layout; full table with sources and recency
dates in `.harness/progress/agentic-alternatives-research.md`. I re-verified its load-bearing claims
independently rather than pasting them — all confirmed:

| Claim | Verification |
|---|---|
| `android/skills` is Google's, active | GitHub API: org `android`, 10,494★, pushed 2026-11-11 |
| spec-kit v1.4.0 is current | API: release v1.4.0 published **2026-11-10**; repo 149,595★, pushed 2026-11-12 |
| ralph is stale | API: last push **2026-02-02** (~9 months) |
| aider maintenance slowed | API: last release v0.86.0 **2025-08-09**, last push 2026-05-22 |
| arXiv:2503.13657 exists as cited | Title *Why Do Multi-Agent LLM Systems Fail?*; abstract: *"performance gains on popular benchmarks are often minimal"*, 1600+ traces / 7 frameworks |
| Cognition post | live at cognition.com/blog/dont-build-multi-agents |
| Anthropic 15× tokens | verbatim: *"agents typically use about 4× more tokens than chat interactions, and multi-agent systems use about 15× more tokens than chats"* |

### The idea worth stealing, in one line

**A `Stop` hook that exits 2 is a gate; a reviewer round is a round-trip.** Claude Code and OpenHands
both implement it: when the agent tries to declare done, a shell command runs, and exit 2 means *you
may not finish*. That deletes the reviewer round-trip instead of shrinking it, and it costs one script
plus a config line. OpenHands' `/goal` adds the right loop semantics: a judge that must see **green
command output, not prose**, with a hard iteration cap.

### Why the literature does *not* vindicate this layout

Anthropic's own number is 15× chat tokens for multi-agent, and they report **token spend alone
explained ~80% of outcome variance** — their wins come from *parallelizable, read-heavy work that
exceeds one context window*. A 360-line single-feature edit with a serial builder→reviewer chain is
the opposite shape: no parallelism to exploit, so you pay the 15× and get none of the coverage. The
honest statement is that **nobody has benchmarked "multi-agent role separation is slower for small
sequential features"** — the scout checked, and it is unverified. But this repo's 9.21 h of serial
generation on 360 product lines is a clean data point on the side of the argument.

### The Android-specific win that removes the human gate

Google's `android/skills` → `testing/testing-setup` routes UI verification to
**Robolectric/Roborazzi/Paparazzi so tests run without a device**, reserving device E2E for release
candidates. That is precisely the C-B problem: our shipping gate is one destructive device cycle needing
a human with server credentials.

I verified this repo's actual position, and it is the root cause of the whole C-B mess:

| Checked | Result |
|---|---|
| `managedDevices` block in `app/build.gradle` | **absent** → instrumented runs are not hermetic or scriptable |
| Robolectric / Roborazzi / Paparazzi deps | **absent** |
| Unit tests using `RobolectricTestRunner` | **0** |
| Test deps | plain JUnit JVM (`testImplementation junit`) + Espresso `androidTestImplementation` |

So the repo has **150 JVM tests that cannot touch Android UI, and zero device-free UI verification.**
Every UI assertion therefore has to go to an instrumented test, and every instrumented test that needs
a real server state goes to the human-gated destructive cycle. That is not a harness flaw — it is a
**test-pyramid gap**, and it is why six review rounds could not close C-B: no amount of reviewing
closes a gate that only a device and a credential can open.

Adoption cost: one skill install, one hook script, one `managedDevices` block, and Robolectric for the
assertions that never needed a device in the first place.

## Reconciling two numbers I published

The research run read an earlier draft of this file that said **12.08 h / 84%**, and flagged that it
contradicted my own "62% coordination" claim. Both flags were fair, so to be explicit:

- **12.08 h** = model generation across *both* sessions on this repo today, including the earlier
  `E00-F01` work. It is the right number for "how long did the machine generate tokens today", and
  the wrong one for this feature.
- **9.21 h (64%)** = generation inside the `E01-F01` commit window only. This is the feature's cost.
- The earlier **"62% coordination overhead"** line was computed from `telemetry.jsonl`, where 62% of
  rows have a null `duration_s`. It was an artifact of missing data, not a finding, and should be
  disregarded. Turn-level transcript timing supersedes it.

The conclusion is unchanged and survives the correction in both directions: generation dominates, the
model is the first lever, and the prose volume is the second.
