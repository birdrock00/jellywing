# Agentic alternatives to this harness — scout research (2026-11-13)

Read-only research. All recency dates are from the GitHub API or the cited page's own
"last updated" stamp on 2026-11-13. Anything I could not fetch is marked **unverified**.

## Baseline caveat (must be read before the table)
`better-agentic-system.md` (in repo root, dated 2026-11-13) measures the same E01-F01 day as
**12.08 h of 14.3 h (84%) spent inside model generation at ~17 tok/s** on a local quantized GGUF,
and states a hosted model at 100–150 tok/s does the same work in ~1.5–2 h. That contradicts the
brief's "62% was coordination overhead". Both can be true at once (coordination *is* more model
output), but the dominant lever is model throughput + volume of prose generated, not the number of
role handoffs. Everything below is therefore ranked by **tokens generated per shipped line**, not
by elegance.

## Comparison table

| Name (recency) | Verification model | Round-trip cost per feature | Adoption cost here | Verdict |
|---|---|---|---|---|
| **OpenHands SDK goal-completion loop** (`/goal`, `run_goal`) — OpenHands/OpenHands, 93.4k★, pushed 2026-11-13 | Judge LLM audits the transcript for *authoritative evidence* (file contents, command output, test results); re-prompts with `missing` until complete or **hard iteration cap** | 1 agent + 1 cheap judge per iteration, in-process; zero human/orchestrator hops | Medium — Python SDK script wrapping the repo's gradle commands | **Adopt the mechanism**, not the platform |
| **Claude Code hooks** — anthropics/claude-code v2.1.323, 2026-11-12 | `Stop` hook fires when the agent tries to finish; exit code 2 blocks termination. Docs cover "Run tests after file changes", prompt-based hooks that "check multiple conditions before stopping", agent hooks, async hooks | Zero extra agent rounds — gate is a shell exit code | Low — a `settings.json` hook + one script in this repo | **Cheapest real win** |
| **OpenHands hooks** — docs.openhands.dev/sdk/guides/hooks.md | Same contract: `PreToolUse` / `UserPromptSubmit` / `Stop` block on exit 2; docs explicitly "Stop: enforce task completion criteria" | Zero extra agent rounds | Low (if OpenHands adopted) | Same pattern, second implementation |
| **Kiro** (AWS) — kirodotdev/Kiro 4.5k★ pushed 2026-11-05; docs page updated 2026-11-12 | `requirements.md`/`design.md`/`tasks.md`; **hooks on triggers** (`PostFileSave`, `Stop`) with command or agent actions, generated from "run tests after every file save"; task dependency graph runs independent tasks in parallel "waves"; "Correctness (property-based testing)" listed IDE/CLI only | Gate is event-driven, not a role round-trip | High — proprietary IDE/CLI, AWS-adjacent | Steal the *trigger* idea; don't migrate |
| **spec-kit** — github/spec-kit 149.6k★, v1.4.0 released **2026-11-10** | Workflow YAML chains agent commands + **shell tasks** + control flow + human review gates into resumable runs; a gate "prompts on a TTY or pauses", and a *declared verdict input can decide the gate without a prompt*. Bundled community workflow "Full SDD Cycle — **no gates**" runs specify→plan→tasks→implement with no review; "Guided SDD Pipeline" adds a clarify gate + post-implementation **convergence loop** | Configurable: 0 gates (yolo) → 2 gates → converge-until-clean | Medium — `specify init`, Python 3.11+/uv, replaces `.harness/` layout | **Closest maintained thing to this harness, with the gates switchable** |
| **GitHub Agentic Workflows (`gh-aw`)** — github/gh-aw 5.6k★, pushed 2026-11-13 | Markdown+YAML compiled to **ordinary GitHub Actions**; README: "use conventional GitHub Actions for deterministic builds, tests, linting"; agent jobs read-only and sandboxed by default; agents run *in CI* on labels/events | Agent runs async in Actions; human never in the loop for the gate | Medium-high — needs GitHub repo + Actions; this repo's unpushed state makes it awkward | Best "gates not rounds" architecture, wrong transport for a local-only repo |
| **opencode** — anomalyco/opencode 226.5k★, v1.19.5 2026-10-28 | `mode: primary|subagent|all` agents with per-agent **permissions**; docs show a `reviewer` subagent with `edit: deny, shell: deny` in a fresh child session; parent's `subagent` permission controls what it may spawn | 1 optional reviewer subagent, invocable, not mandated | Low-medium | Cleanest minimal role model — reviewer as *opt-in tool* |
| **ralph** — snarktank/ralph 22.2k★, last push **2026-02-02** (9 mo stale) | Loop the coding tool repeatedly until every `prd.json` item is complete; **fresh context each iteration**; memory = git history + `progress.txt` + `prd.json` | No review round; acceptance is a JSON/test predicate | Very low — a shell loop + prompts | Right shape for token survival; **stale**, and acceptance strength is entirely up to your predicate |
| **aider** — Aider-AI/aider 50k★, last release **v0.86.0 (2025-08-09)**, last push 2026-05-22 | `/architect` proposes + `/code` model applies file edits; `--auto-accept-architect` removes the human accept step | 1 handoff, automatable | Low | Maintenance has slowed ~6 mo; use as pattern, not as tool |
| **goose** — aaif-goose/goose 56.5k★ (moved from block/), pushed 2026-11-13 | Recipes = reusable YAML workflows, AGENTS.md/`.goosehints` project instructions, skills, permission tiers ("when goose may act without approval") | Single agent, no mandated reviewer | Low-medium | Single-agent + recipe YAML; fine, no distinctive gate |
| **Android skills** — **android/skills (Google)** 10.5k★, pushed 2026-11-11 | Open agent-skills standard (`SKILL.md`), progressive disclosure; `testing/testing-setup` SKILL.md (metadata `last-updated 2026-10-28`) routes UI verification to **Robolectric/Roborazzi/Paparazzi so tests run without a device**, reserving device E2E for release-candidate checks | Zero rounds — it is knowledge, not a loop | **Very low**: `/plugin marketplace add android/skills` or `android skills add` | Best Android-specific item found |
| **Maestro** — mobile-dev-inc/Maestro 16.4k★, pushed 2026-11-13 | YAML declarative mobile E2E (repo-level verification only; docs not read this run) | Deterministic, no agent | Medium (YAML flows) | Candidate gate for the delete-flow UI |
| **gradle-pitest-plugin** — szpak/gradle-pitest-plugin 251★, pushed 2026-10-27 | PIT mutation testing as a build task | Deterministic | Medium (slow on Android/Kotlin) | Only if you distrust test suites, not rounds |

Not covered (could not verify this run, marked **unverified**): Cursor rules/background agents, Devin,
shai, gptme (repo live: gptme/gptme v0.36.0 2026-11-02) depth, tiny-agents (no repo at guessed path),
OpenAI Codex AGENTS.md doc pages, BMAD-METHOD internals (repo live, 55.3k★, pushed 2026-11-12).

## Q1 — Which make the review round optional/automatic, and with what?
- **Claude Code / OpenHands hooks**: the review round is replaced by a **shell gate on the `Stop` event**; exit 2 = "you may not declare done". This is the only mechanism in the wild that removes the reviewer *round-trip* rather than just shrinking it. Verified in both hook docs.
- **OpenHands `/goal`**: replaces the reviewer with a **second judge LLM + hard iteration cap**, and the judge must see green command output, not prose. This is "self-critique with a budget cap", implemented.
- **spec-kit workflows**: gates are *declared, optional steps*; a verdict can be supplied non-interactively, and community `yolo` ships zero gates. Convergence is a loop that re-runs until it reports `Converged`.
- **Kiro**: gates become **file-save/stop triggers** running commands, generated conversationally.
- **gh-aw**: determinism is delegated to plain GitHub Actions; agents only fill the reasoning gaps.
- **ralph / aider**: no reviewer at all; acceptance is `prd.json` completion / auto-accepted architect.
- Mutation testing (PIT) exists but nothing mainstream makes it the primary gate — **unverified** as a review-substitute.

## Q2 — Best for small Android/Kotlin + instrumented tests
1. **android/skills (Google, official, active)** — near-zero adoption cost, and `testing-setup`
   explicitly pushes verification *off the device* (Robolectric + Roborazzi/Paparazzi), which is
   exactly what removes the human-shaped QA gate.
2. **A `Stop` hook (Claude Code or OpenHands) that runs one gradle command and exits 2 on red** — this
   repo already has a KVM-backed headless AVD booting in ~4 s (`.harness/progress/qa-emulator.md`),
   so instrumented tests are already machine-runnable. `app/build.gradle` declares espresso deps but
   I found **no `managedDevices` block** — adding Gradle Managed Devices would make
   `./gradlew connectedDebugAndroidTest` a single hermetic gate. (Gradle Managed Devices doc URL not
   fetched this run: **unverified**.)
3. Maestro for the user-visible delete flow if Espresso is too brittle.
Cost of adoption: one skill install + one hook script + one `managedDevices` block. Nothing else.

## Q3 — Is multi-agent role separation slower than one agent + strong gates?
**No one has measured this claim directly. Treat it as unverified.** What is verifiable:
- Cognition, *Don't Build Multi-Agents*, Walden Yan, **2025.06.12** (cognition.ai/blog/dont-build-multi-agents):
  parallel subagents make conflicting implicit decisions; reliability of long-running agents depends on
  sharing full context; "context engineering is the #1 job". Argues against, but no wall-clock benchmark.
- Anthropic, *How we built our multi-agent research system* (anthropic.com/engineering/built-multi-agent-research-system):
  multi-agent ≈**15× chat tokens** (agents ≈4× chat); beat single-agent Opus 4 by ~90% on an internal
  research eval; **token spend alone explained 80% of variance**; wins are for parallelizable,
  context-exceeding, read-heavy work — i.e. the *opposite* of a 360-line single-feature edit.
- arXiv:2503.13657, *Why Do Multi-Agent LLM Systems Fail?* (v1 2025-03-17, v3 2025-10-26): across
  **1600+ traces / 7 MAS frameworks**, "performance gains on popular benchmarks are often minimal";
  14 failure modes, κ=0.88.
Net: the literature supports "multi-agent costs ~15× tokens and its benchmark gains are often minimal",
which is *consistent with* the claim, not proof of it. This repo's own telemetry
(`better-agentic-system.md`) is the strongest direct datum: 33 runs, all cost, 84% of the day.

## Q4 — Cheapest change to THIS layout that cuts wall clock
Ranked by (expected hours saved) / (effort):
1. **Kill the mandated independent Reviewer round; make it a gate.** One script
   (`tools/verify-feature.sh`: unit + GMD instrumented + lint) wired as a `Stop`-style hook / driver
   check that the builder must pass green before it may report done. Cap self-critique at **2**
   iterations, then escalate. Directly attacks 6 review rounds / 1 REJECT / 4 APPROVE-WITH-CONDITIONS.
2. **Reduce generated prose volume.** 2,111 lines of progress prose + 964 spec lines for 360 lines of
   product code is ~8.5 tok-generating output artifacts per product line at 17 tok/s. Cap progress
   notes at ~40 lines / diff-referenced bullets; make doc-critic opt-in.
3. **Switch the model/provider for writing-heavy roles** (already documented in
   `better-agentic-system.md`): the largest single lever, and it is not a layout change.
4. **Subagent-per-exploration with output caps, not subagent-per-role.** 4 deaths at
   `stopReason=length` happened during *exploration*. Claude Code's model (own context window per
   subagent, body-of-skill loads only when used, 15k-token description warning) is the fix shape;
   ralph's "fresh context each iteration, memory in git + progress.txt" is the cheap version.
5. **Parallel lanes only for independent features** (Kiro-style dependency waves over `tasks`), not
   for stages of one feature.
6. Fix the armed-but-disarmed escalation (`init.sh` warns `builder-heavy` never fires) — otherwise
   the configured escape hatch is decoration.

## Ranked shortlist (3)
1. **Hooks-as-gate (`Stop` → exit 2) + one hermetic gradle command + android/skills `testing-setup`.**
   Smallest diff, deletes the reviewer round, makes the destructive-device QA cycle scripted rather
   than human. Everything needed already exists on this machine.
2. **OpenHands `/goal` judge-with-iteration-cap** as the loop semantics: "cannot finish until the
   judge sees green output", hard cap so it cannot loop forever. Adopt the pattern even if the tool
   stays.
3. **spec-kit v1.4.0 workflow YAML** if a full replacement is wanted: it is the only actively released
   SDD harness (released 2026-11-10) where gates, convergence loops and shell checks are *declarative
   config*, so "0 gates for small features, 2 for risky ones" is a diff, not a rewrite.
