/**
 * Headless test for .pi/extensions/verify-gate.ts.
 *
 * Loads the REAL extension factory against a fake pi/ctx and drives the registered
 * agent_before_settle handler through every branch: green, red, no-UI, absent gate, caching, the
 * per-session block cap, and on-disk forensics.
 *
 * Why this exists as a file rather than a one-off: the shipped gate silently did nothing for a
 * whole session because it called ctx.notify(), a method that does not exist (the real one is
 * ctx.ui.notify). The throw was swallowed by its own fail-open handler, which called the same
 * missing method. A broken gate and a working gate looked identical from the outside. This suite is
 * what would have caught it, so it is part of the repo.
 *
 *   node --experimental-strip-types tools/test-verify-gate.mjs
 *
 * Two testing traps recorded here because both produced bogus results during authoring:
 *   - a fake pi.exec that ignores opts.cwd makes every fixture share the harness process's git
 *     state, hence one cache key, hence ten phantom failures;
 *   - reusing ONE factory instance across scenarios means the block cap (2) has already fired by
 *     scenario C, so a correct stand-down reads as a broken gate. Fresh harness per scenario.
 */
//   - the fake exec ignored opts.cwd, so every fixture shared the harness process's git state and
//     therefore one cache key (ten bogus failures);
//   - one factory instance was reused across scenarios, so the per-session block cap (2) had already
//     fired by scenario C, and the cap stand-down was mistaken for a broken gate.
// Hence: a FRESH harness per scenario, and gate invocations counted as `sh <gate>` only.
import { execFile, execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync, chmodSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Paths are derived from this file's own location. Hardcoding the checkout path would both break on
// another machine and trip tools/verify-feature.sh, which fails the build on local absolute paths in
// tracked content.
const REPO = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const { default: factory } = await import(join(REPO, ".pi/extensions/verify-gate.ts"));

const mkRepo = (name, gateBody) => {
  const dir = `/tmp/gate-fixtures/${name}`;
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(join(dir, "tools"), { recursive: true });
  const g = join(dir, "tools/verify-feature.sh");
  writeFileSync(g, gateBody);
  chmodSync(g, 0o755);
  // Each fixture is its OWN git repo: the gate keys its cache on git state, so a fixture that is not
  // a repo makes git rev-parse fall through to the harness's repo and every scenario share a key.
  for (const a of [["init", "-q"], ["config", "user.email", "t@users.noreply.github.com"], ["config", "user.name", "t"], ["add", "-A"], ["commit", "-qm", "x"]]) {
    execFileSync("git", a, { cwd: dir, stdio: "ignore" });
  }
  return dir;
};

// Fresh extension instance = fresh per-session state (block counter + memoised verdict).
const newHarness = () => {
  const counters = { gateCalls: 0 };
  const pi = {
    on(_event, handler) {
      this.handler = handler;
      return () => {};
    },
    async exec(command, args, opts) {
      if (command === "sh" && args[0]?.endsWith("verify-feature.sh")) counters.gateCalls++;
      return new Promise((resolve) => {
        // honouring opts.cwd is mandatory: the extension resolves everything relative to it
        execFile(command, args, { cwd: opts?.cwd, maxBuffer: 32 << 20 }, (err, stdout, stderr) =>
          resolve({ stdout: stdout ?? "", stderr: stderr ?? "", code: err ? (err.code ?? 1) : 0, killed: false, truncated: false }),
        );
      });
    },
  };
  factory(pi);
  const call = async (dir, withUI = true) => {
    const notified = [];
    const ctx = { cwd: dir, ...(withUI ? { ui: { notify: (m, t) => notified.push([t ?? "info", m]) } } : {}) };
    const before = counters.gateCalls;
    const res = await pi.handler({ type: "agent_before_settle", entries: [], continue: false, context: {}, outcome: {} }, ctx);
    return { res, notified, gateCalls: counters.gateCalls - before };
  };
  return { call, counters };
};

let failures = 0;
const check = (cond, label) => {
  console.log(`${cond ? "  ok  " : "  FAIL"} ${label}`);
  if (!cond) failures++;
};

// --- A: green tree -------------------------------------------------------------------------------
console.log("A) gate exits 0");
{
  const dir = mkRepo("green", "#!/bin/sh\necho PASS\nexit 0\n");
  const { call } = newHarness();
  const r = await call(dir);
  check(r.res === undefined, "does not block a passing tree");
}

// --- B: red tree must block, with evidence in context --------------------------------------------
console.log("B) gate exits 2");
{
  const dir = mkRepo("red", "#!/bin/sh\necho 'membership reads without Limit' >&2\nexit 2\n");
  const { call } = newHarness();
  const { res, notified } = await call(dir);
  const entry = res?.entries?.[0];
  check(res?.continue === true, "requests a continuation");
  check(entry?.type === "custom_message" && entry?.customType === "verify-gate-failure", "appends a failure entry");
  check(typeof entry?.content === "string" && entry.content.includes("exit 2"), "names the exit code");
  check(entry?.content?.includes("membership reads without Limit"), "carries the gate output into context");
  check(entry?.display === true, "is visible to the operator");
  check(notified.length > 0, "told the UI it ran");
}

// --- C: no UI on ctx (the shape that hid the original bug) ---------------------------------------
console.log("C) ctx without ui");
{
  const dir = mkRepo("noui", "#!/bin/sh\necho boom >&2\nexit 2\n");
  const { call } = newHarness();
  let threw = null;
  let r;
  try {
    r = await call(dir, false);
  } catch (e) {
    threw = e;
  }
  check(threw === null, "does not throw when ui is absent");
  check(threw === null && r?.res?.continue === true, "still blocks even with no UI");
}

// --- D: absent gate script = complete opt-out ----------------------------------------------------
console.log("D) no verify-feature.sh");
{
  const dir = "/tmp/gate-fixtures/absent";
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
  for (const a of [["init", "-q"], ["config", "user.email", "t@users.noreply.github.com"], ["config", "user.name", "t"], ["commit", "-qm", "x", "--allow-empty"]]) {
    execFileSync("git", a, { cwd: dir, stdio: "ignore" });
  }
  const { call } = newHarness();
  const r = await call(dir);
  check(r.res === undefined && r.gateCalls === 0, "opt-out: no script, no gate call, no block");
}

// --- E: caching ----------------------------------------------------------------------------------
console.log("E) identical state must not re-run the gate");
{
  const dir = mkRepo("cached", "#!/bin/sh\necho PASS\nexit 0\n");
  const { call } = newHarness();
  const a = await call(dir);
  const b = await call(dir);
  check(a.gateCalls === 1, "first call ran the gate exactly once");
  check(b.gateCalls === 0, "second identical call skipped (in-session memo)");
}

// --- F: per-session block cap prevents an infinite loop ------------------------------------------
console.log("F) block cap");
{
  const dir = mkRepo("cap", "#!/bin/sh\necho still broken >&2\nexit 2\n");
  const { call } = newHarness();
  const r1 = await call(dir);
  const r2 = await call(dir);
  const r3 = await call(dir);
  check(r1.res?.continue === true && r2.res?.continue === true, "blocks the first two attempts");
  check(r3.res === undefined, "third attempt stands down instead of looping forever");
  check(r3.notified.some(([, m]) => m.includes("standing down")), "stands down loudly, not silently");
}

// --- G: on-disk forensics ------------------------------------------------------------------------
console.log("G) on-disk log");
{
  const dir = mkRepo("logs", "#!/bin/sh\nexit 0\n");
  const { call } = newHarness();
  await call(dir);
  const per = readFileSync(join(dir, ".git/pi-verify-gate/gate.log"), "utf8");
  const loadLog = readFileSync(join(REPO, ".git/pi-verify-gate/gate.log"), "utf8");
  check(loadLog.includes("loaded"), "load recorded where load happens (process cwd)");
  check(per.includes("run ") && per.includes("PASS"), "verdict recorded in the repo under test, not the first log opened");
}

console.log(failures ? `\nGATE TEST: FAIL (${failures})` : "\nGATE TEST: PASS");
process.exit(failures ? 1 : 0);
