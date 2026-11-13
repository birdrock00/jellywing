/**
 * Verify Gate — a Stop-hook equivalent for pi (exit-code-2 semantics).
 *
 * Why: this project's harness verified feature work with an independent Reviewer *round*, which
 * measurement showed to be the most expensive verification available (~1 h of generation per round
 * at local-model throughput). `agent_before_settle` is pi's final actionable boundary: it can append
 * session entries and request one continuation. That turns "a reviewer must agree you finished" into
 * "a script must not object to you finishing".
 *
 * Design constraints, all of them load-bearing:
 *
 * 1. Only fires when `tools/verify-feature.sh` exists and is executable. Its absence disables the
 *    gate, so removing the script is a complete opt-out.
 * 2. Results are CACHED against the exact state under test (HEAD + tracked-diff hash). Without this
 *    every reply would re-run a multi-minute gate, which would be worse than no gate.
 * 3. Hard cap on continuations. A gate that can always re-prompt is an infinite loop. After the cap
 *    it stops blocking and reports loudly that it is standing down, rather than trapping the agent.
 * 4. Fails OPEN on its own errors. A broken gate must not be able to freeze a session.
 * 5. Every path writes to a log file. The first version of this file silently never ran at all
 *    because it called a method that does not exist (`ctx.notify` instead of `ctx.ui.notify`), the
 *    throw landed in the fail-open handler, and that handler called the same missing method. A
 *    silent no-op looked exactly like a working gate. Nothing here is allowed to fail quietly.
 */

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { createHash } from "node:crypto";
import { appendFileSync, constants, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const GATE = "tools/verify-feature.sh";
const STATE_DIR = ".git/pi-verify-gate";
const MAX_BLOCKS_PER_SESSION = 2;
const GATE_TIMEOUT_MS = 20 * 60 * 1000;

export default function (pi: ExtensionAPI) {
  let blocksThisSession = 0;
  let lastCheckedKey: string | null = null;

  const sha = (s: string) => createHash("sha256").update(s).digest("hex").slice(0, 16);

  // Logging is deliberately best-effort and never throws into the caller.
  // ExtensionAPI exposes exec() but NO cwd; ExtensionContext does. At load time the only available
  // anchor is the process cwd, which for a pi session is the project directory.
  const loadCwd = process.cwd();
  // Path resolved per call, not memoised: memoising meant every repository a process touched wrote
  // into whichever log was opened first, which made per-repo forensics impossible.
  // cwd is a REQUIRED first argument on purpose: an earlier revision defaulted it to the process
  // cwd, so verdict lines silently landed in whichever repo the process started in.
  const log = (cwd: string, msg: string) => {
    try {
      mkdirSync(join(cwd, STATE_DIR), { recursive: true });
      appendFileSync(join(cwd, STATE_DIR, "gate.log"), `${new Date().toISOString()} ${msg}\n`);
    } catch {
      /* logging must never be the thing that breaks a session */
    }
  };
  const tell = (ctx: { ui?: { notify?: (m: string, t?: "info" | "warning" | "error") => void } }, msg: string, level: "info" | "warning" = "info") => {
    try {
      ctx.ui?.notify?.(msg, level);
    } catch {
      /* UI absent (e.g. non-TUI mode): the log line is the record */
    }
    log(ctx.cwd || loadCwd, `notify(${level}): ${msg}`);
  };

  log(loadCwd, `loaded (cwd=${loadCwd}, gate=${existsSync(join(loadCwd, GATE))})`);

  pi.on("agent_before_settle", async (event, ctx) => {
    // NOTE: `aborted` is a field of agent_settled, not of this event — aborted runs never reach
    // here (documented). Reading event.aborted was a no-op that only LOOKED like a guard.
    const cwd = ctx.cwd || loadCwd;
    const gate = join(cwd, GATE);
    try {
      if (!existsSync(gate)) return; // opt-out: no script, no gate
      try {
        // biome-ignore lint/style/noUselessElse: accessSync is the only X_OK check
        const { accessSync } = await import("node:fs");
        accessSync(gate, constants.X_OK);
      } catch {
        return; // present but not executable -> gate disabled
      }

      const head = await pi.exec("git", ["rev-parse", "HEAD"], { cwd });
      if (head.code !== 0) {
        tell(ctx, "verify-gate: not a git checkout, skipping", "warning");
        return;
      }
      const diff = await pi.exec("git", ["diff", "--stat", "HEAD"], { cwd });
      const nameOnly = await pi.exec("git", ["diff", "--name-only", "HEAD"], { cwd });
      if (diff.code !== 0 || nameOnly.code !== 0) return;

      // What we vouch for: HEAD plus tracked working-tree change. Untracked files are excluded —
      // they are not part of a commit, and including them would re-run the gate while an agent is
      // mid-write on a brand-new file.
      const stateKey = sha(`${head.stdout.trim()}|${diff.stdout.trim()}|${nameOnly.stdout.trim()}`);
      if (stateKey === lastCheckedKey) return; // already vouched for this exact state

      const cacheFile = join(cwd, STATE_DIR, stateKey);
      if (existsSync(cacheFile) && readFileSync(cacheFile, "utf8").trim() === "PASS") {
        lastCheckedKey = stateKey;
        log(cwd, `cached PASS ${stateKey}`);
        return;
      }

      if (blocksThisSession >= MAX_BLOCKS_PER_SESSION) {
        tell(ctx, `verify-gate: still failing after ${MAX_BLOCKS_PER_SESSION} blocks — standing down to avoid an infinite loop. Run \`sh ${GATE}\` manually.`, "warning");
        return;
      }

      blocksThisSession += 1;
      tell(ctx, `verify-gate: running ${GATE} (block ${blocksThisSession}/${MAX_BLOCKS_PER_SESSION}) …`);
      log(cwd, `run ${stateKey}`);

      const run = await pi.exec("sh", [GATE], { cwd, timeout: GATE_TIMEOUT_MS });
      const output = `${run.stdout}\n${run.stderr}`.trim();

      if (run.code === 0) {
        try {
          mkdirSync(join(cwd, STATE_DIR), { recursive: true });
          writeFileSync(cacheFile, "PASS");
        } catch {
          /* a cache miss next time is acceptable; never fail the turn over it */
        }
        lastCheckedKey = stateKey;
        tell(ctx, "verify-gate: PASS");
        return;
      }

      // Refuse to let the run settle. The entry is what makes it act: the failure output has to be
      // in context, not just a notification the model may not reason over.
      const tail = output.length > 6000 ? `[… ${output.length - 6000} chars truncated …]\n` + output.slice(-6000) : output;
      log(cwd, `FAIL ${stateKey} exit=${run.code}`);
      return {
        entries: [
          ...event.entries,
          {
            type: "custom_message",
            customType: "verify-gate-failure",
            content:
              `The deterministic verify gate FAILED (exit ${run.code}); you may not report this work as complete.\n\n` +
              "```\n" +
              tail +
              "\n```\n\n" +
              `Fix the cause and re-run \`sh ${GATE}\` yourself. Do not weaken, skip, or comment out a ` +
              `check to get green, and do not edit the gate script to make a real failure disappear — if ` +
              `a check is genuinely wrong, say so explicitly and explain why instead of changing it. ` +
              `Block ${blocksThisSession}/${MAX_BLOCKS_PER_SESSION} of this session's allowance.`,
            display: true,
          },
        ],
        continue: true,
      };
    } catch (err) {
      // Fail open, but LOUDLY and on disk, because a silent no-op is the failure mode that hid the
      // original bug in this file.
      const msg = err instanceof Error ? err.message : String(err);
      log(cwd, `ERROR, gate skipped: ${msg}`);
      tell(ctx, `verify-gate: internal error, gate skipped: ${msg}`, "warning");
      return;
    }
  });
}
