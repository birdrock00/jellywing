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
- [2026-11-12 builder] AGP connectedDebugAndroidTest on this host uninstalled app+test APKs (deletePackageX) after the run and wiped the app login, then retried against the wiped app — iterate instrumented tests with `adb install -r -d` + `am instrument -w -e class`, and treat any gradle connected run as a session-destroying event.
- [2026-11-12 builder] Never read or dump the operator's credential/session cache (kept outside the repo) even to debug: the values land in the run log; the builder lane needs zero credentials — device E2E is QA's protocol.
- [2026-11-12 reviewer] Before accepting a "deferred to QA" test row, grep for the named method: E01-F01's `.tests.md` mapped R3/R4/R9/R10 to five `e2e*` methods that were never written (only `e2e4_cancelNoop` exists), so a deferral can silently read as coverage — a deferred row must say NOT YET WRITTEN, and a source-contract test that asserts whole-file `contains(...)` cannot tell which branch ran, so scope it to the branch.
