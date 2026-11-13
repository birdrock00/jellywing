# E01-F01 round 4 — Orchestrator notes (role-deviation record)

Date: 2026-11-13 (UTC).

## What happened

- The round-4 Architect run applied **all eleven** matrix corrections from review-round-3 §3 and the
  two residual bounds, then hit its time cap before writing its own notes file.
- The round-4 Builder cleared condition C-C1 at `738820b1` while the Architect was still working, so the
  matrix it produced described C-C1 as in progress.

## Deviation, and why

The Orchestrator edited `.tests.md` directly. That is normally Architect-owned authoring.

This edit was a **status mirror**, not new specification: rows that said "C-C1 IN PROGRESS / F5 open"
were stale facts about work already committed and already evidenced. Leaving them would have told the QA
round that a hole was open when it was closed — the inverse of the round-1 blocker, and the same class of
error as the stale matrix that produced B1. Scope of the edit: the R12 pair of rows, one bullet, one
open-items list. Requirement text, acceptance criteria and plan citations were not touched.

Two rows were left deliberately marked **"Reviewer re-run pending"** rather than cleared, because the
closure rests on Builder-run mutation evidence, not independent verification.

## Evidence mirrored

Named scan `translatedDeleteKeys(String localeXml)` with no fragment parameter, plus a planted-key
positive control. Four mutants RED; focused class 16/0; full suite 150 / 0 failures / 1 skip.

## Gates after the edit

`tasks-pair-check.py` 0 (12 R-ids, 23 tasks) · `readiness-check.py` 0 (87 refs) ·
`validate-board.py` 0.
