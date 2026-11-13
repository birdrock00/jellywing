/**
 * Verify Gate — a Stop-hook equivalent for pi (exit-code-2 semantics).
 *
 * Why: this project's harness verified feature work with an independent Reviewer *round*, which
 * measurement showed to be the most expensive verification available (~1 h of generation per round
 * at local-model throughput). `agent_before_settle` is pi's final actionable boundary: it can append
 * a session entry and request one continuation. That turns "a reviewer must agree you finished" into
 * "a script must not object to you finishing".
 *
 * Design constraints, all of them load-bearing:
 *
 * 1. Only fires when `tools/verify-feature.sh` exists and is executable. Its absence disables the
 *    gate, so removing the script is a complete opt-out.
 * 2. Results are CACHED against the exact state under test (HEAD + tracked-diff hash). Without this
 *    every reply would re-run a multi-minute gate, which would be worse than no gate — the first
 *    version of this file had that bug and it is the reason the cache key is content, not time.
 * 3. Hard cap on continuations. A gate that can always re-prompt is an infinite loop; OpenHands'
 *    /goal uses a cap for exactly this reason. After the cap the gate stops blocking and reports
 *    loudly that it is standing down, rather than trapping the agent.
 * 4. Never blocks an aborted run.
 * 5. Fails OPEN on its own errors. A broken gate must not be able to freeze a session; it degrades
 *    to a warning. The gate adds signal, it is not a security boundary.
 */

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { createHash } from "node:crypto";

const GATE = "tools/verify-feature.sh";
const CACHE_DIR = ".git/pi-verify-gate";
const MAX_BLOCKS_PER_SESSION = 2;
const GATE_TIMEOUT_MS = 20 * 60 * 1000;

export default function (pi: ExtensionAPI) {
  let blocksThisSession = 0;
  let lastCheckedKey: string | null = null;

  const sha = (s: string) => createHash("sha256").update(s).digest("hex").slice(0, 16);

  pi.on("agent_before_settle", async (event, ctx) => {
    if (event.aborted) return;

    try {
      // Opt-in: no script, no gate.
      const probe = await pi.exec("test", ["-x", GATE]);
      if (probe.code !== 0) return;

      // What exactly are we vouching for? HEAD plus any tracked working-tree change. Untracked
      // files are deliberately excluded: they are not part of a commit, and including them would
      // re-run the gate while an agent is mid-write on a brand-new file.
      const head = await pi.exec("git", ["rev-parse", "HEAD"]);
      if (head.code !== 0) return;
      const diff = await pi.exec("git", ["diff", "--stat", "HEAD"]);
      const nameOnly = await pi.exec("git", ["diff", "--name-only", "HEAD"]);
      if (diff.code !== 0 || nameOnly.code !== 0) return;

      const stateKey = sha(`${head.stdout.trim()}|${diff.stdout.trim()}|${nameOnly.stdout.trim()}`);

      // Already vouched for exactly this state this session. This is what keeps the gate from
      // re-running on every reply.
      if (stateKey === lastCheckedKey) return;

      // Cached verdict for this exact state, e.g. from a run the agent did itself.
      const cached = await pi.exec("cat", [`${CACHE_DIR}/${stateKey}`]);
      if (cached.code === 0 && cached.stdout.trim() === "PASS") {
        lastCheckedKey = stateKey;
        return;
      }

      if (blocksThisSession >= MAX_BLOCKS_PER_SESSION) {
        ctx.notify({
          level: "warning",
          message: `verify-gate: still failing after ${MAX_BLOCKS_PER_SESSION} blocks — standing down to avoid an infinite loop. Run \`sh ${GATE}\` manually.`,
        });
        return;
      }

      blocksThisSession += 1;
      ctx.notify({ level: "info", message: `verify-gate: running ${GATE} …` });

      const run = await pi.exec("sh", [GATE], { timeoutMs: GATE_TIMEOUT_MS });
      const output = `${run.stdout}\n${run.stderr}`.trim();

      if (run.code === 0) {
        await pi.exec("mkdir", ["-p", CACHE_DIR]);
        await pi.exec("sh", ["-c", `printf 'PASS' > ${CACHE_DIR}/${stateKey}`]);
        lastCheckedKey = stateKey;
        ctx.notify({ level: "info", message: "verify-gate: PASS" });
        return;
      }

      // Refuse to let the agent settle. The entry is what makes it act: the failure output has to
      // be in context, not just a notification the model may not reason over.
      const tail = output.length > 6000 ? `\n[… ${output.length - 6000} chars truncated …]\n` + output.slice(-6000) : output;
      return {
        entries: [
          ...event.entries,
          {
            type: "custom_message",
            customType: "verify-gate-failure",
            content:
              `The deterministic verify gate FAILED (exit ${run.code}); you may not report this work as complete.\n\n` +
              `\`\`\`\n${tail}\n\`\`\`\n\n` +
              `Fix the cause and re-run \`sh ${GATE}\` yourself. Do not weaken, skip, or comment out a ` +
              `check to get green, and do not edit the gate script to make a real failure disappear — ` +
              `if a check is genuinely wrong, say so explicitly and explain why instead of changing it. ` +
              `Block ${blocksThisSession}/${MAX_BLOCKS_PER_SESSION} of this session's allowance.`,
            display: true,
          },
        ],
        continue: true,
      };
    } catch (err) {
      // Fail open: a broken gate must never be able to wedge a session.
      ctx.notify({
        level: "warning",
        message: `verify-gate: error, skipping gate: ${err instanceof Error ? err.message : String(err)}`,
      });
      return;
    }
  });
}
