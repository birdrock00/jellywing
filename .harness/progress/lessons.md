# Earned lessons (append-only)

> One entry per lesson that cost a review round, a rejection, or a debugging session to
> learn. Every role reads this file at session start; any lane may append. Never rewrite
> or delete an entry — supersede it with a newer one. Format:
>
>     - [YYYY-MM-DD <lane>] <the lesson, one or two lines, imperative>
- [2026-11-12 builder] Before writing or approving a toolchain-version gate, run the probe on THIS host (`java -version`, JDK dirs): E00-F01 R9 pinned "JDK 17 on PATH" while the host carried only JDK 21, and a strict fail() in init.project.sh would have aborted init.sh before every orchestrator step.
- [2026-11-12 builder] Verify the per-pair "commit after each GREEN" is even possible before planning commits around it: `.harness/` can be entirely untracked and the spec may bar committing it (E00-F01) — then pair commits pin only the test file and the Reviewer must be told the working tree is part of the deliverable.
- [2026-11-12 builder] When a prose-contract test keys on "that line or its neighbour", the physical line-wrap of the prose is part of the contract: draft the bullet so both anchor tokens land on adjacent physical lines, or the GREEN hinges on wrap accidents (E00-F01 r7/P1).
- [2026-11-12 reviewer] A plan asserting a host fact needs the command output, not a date: E00-F01 .plan.md:70 recorded "java -version reports major 17" as verified 2026-11-12 while the host ran 21.0.12.1 the same day — treat undated-output host claims in plans as unverified until probed.
