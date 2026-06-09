// Scheduler view-model helpers — pure, presentation-only. The wire
// shape is the bank-api `/v1/jobs` Job: { periodicity, run-time-minutes,
// enabled, last-run-at, next-run-at, ... } where run-time-minutes is
// minutes past midnight (UTC) and periodicity is daily/monthly/yearly.
// The backend fixes the day for non-daily cadences (monthly → the 1st,
// yearly → 1 Jan), so the phrasing and cron below mirror that, not a
// free choice of day.

const MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];
const pad = (n) => String(n).padStart(2, "0");

// run-time-minutes → "HH:MM" (24h, UTC).
export function hhmm(minutes) {
  const m = minutes ?? 0;
  return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;
}

// Human phrasing: "Daily · 02:00 UTC" | "Monthly · 1st · 02:00 UTC" |
// "Annually · 1 Jan · 02:00 UTC".
export function humanSchedule(job) {
  const t = `${hhmm(job["run-time-minutes"])} UTC`;
  switch (job.periodicity) {
    case "daily":
      return `Daily · ${t}`;
    case "monthly":
      return `Monthly · 1st · ${t}`;
    case "yearly":
      return `Annually · 1 Jan · ${t}`;
    default:
      return t;
  }
}

// Standard 5-field unix cron mirroring the server's Quartz schedule —
// the form operators recognise. "m h * * *" | "m h 1 * *" | "m h 1 1 *".
export function cronOf(job) {
  const mins = job["run-time-minutes"] ?? 0;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  switch (job.periodicity) {
    case "daily":
      return `${m} ${h} * * *`;
    case "monthly":
      return `${m} ${h} 1 * *`;
    case "yearly":
      return `${m} ${h} 1 1 *`;
    default:
      return "";
  }
}

// Next fire time (epoch ms, UTC) strictly after `fromMs`, derived from
// the periodicity model. Used as a client-side fallback when the job
// carries no server-computed next-run-at (e.g. before the runner has
// registered its trigger).
export function nextRunAt(job, fromMs) {
  const mins = job["run-time-minutes"] ?? 0;
  const hh = Math.floor(mins / 60);
  const mm = mins % 60;
  const from = new Date(fromMs);
  const y = from.getUTCFullYear();
  const mo = from.getUTCMonth();
  const d = from.getUTCDate();
  if (job.periodicity === "daily") {
    let t = Date.UTC(y, mo, d, hh, mm);
    while (t <= fromMs) t += 86400000;
    return t;
  }
  if (job.periodicity === "monthly") {
    for (let i = 0; i < 120; i++) {
      const t = Date.UTC(y, mo + i, 1, hh, mm);
      if (t > fromMs) return t;
    }
  }
  if (job.periodicity === "yearly") {
    for (let i = 0; i < 12; i++) {
      const t = Date.UTC(y + i, 0, 1, hh, mm);
      if (t > fromMs) return t;
    }
  }
  return null;
}

export function fmtDur(secs) {
  if (secs == null) return "—";
  if (secs < 60) return `${secs}s`;
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return s ? `${m}m ${pad(s)}s` : `${m}m`;
}

// Coerce an API timestamp to epoch milliseconds, or null. bank-api
// serialises timestamps as ISO-8601 strings (the Timestamp schema's
// encode), not epoch-ms numbers, so anything doing arithmetic on them
// (fmtRel, durations) must normalise first or it gets NaN.
export function toMs(v) {
  if (v == null) return null;
  if (typeof v === "number") return v;
  const t = Date.parse(v);
  return Number.isNaN(t) ? null : t;
}

// "21 Apr · 02:00" (UTC), with the year appended only when it differs
// from the current one.
export function fmtAbs(v) {
  const ms = toMs(v);
  if (ms == null) return "—";
  const d = new Date(ms);
  const yr = d.getUTCFullYear();
  const yrPart = yr !== new Date().getUTCFullYear() ? ` ${yr}` : "";
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}${yrPart} · ${pad(
    d.getUTCHours(),
  )}:${pad(d.getUTCMinutes())}`;
}

// "in 14h" / "3d ago" — coarse relative phrasing against `nowMs`.
export function fmtRel(targetMs, nowMs) {
  const target = toMs(targetMs);
  if (target == null) return "—";
  const diff = target - nowMs;
  const abs = Math.abs(diff);
  const mins = abs / 60000;
  const hrs = mins / 60;
  const days = hrs / 24;
  let n;
  if (mins < 1) n = "moments";
  else if (mins < 60) n = `${Math.round(mins)}m`;
  else if (hrs < 48) n = `${Math.round(hrs)}h`;
  else if (days < 60) n = `${Math.round(days)}d`;
  else n = `${Math.round(days / 30)}mo`;
  return diff < 0 ? `${n} ago` : `in ${n}`;
}

// The badge state for a job: `running` while a run is in progress, else
// the latest run's outcome, else `scheduled` (never run yet). `run` is
// the newest run from `/v1/jobs/{id}/runs` (status one of
// running/succeeded/failed), or null when there are none.
export function lastOutcome(run) {
  if (!run) return "scheduled";
  if (run.status === "running") return "running";
  return run.status;
}

// Wall-clock duration of a run in whole seconds, or null when it hasn't
// finished (or never started).
export function runDurationSecs(run) {
  const started = toMs(run?.["started-at"]);
  const finished = toMs(run?.["finished-at"]);
  if (started == null || finished == null) return null;
  return Math.max(0, Math.round((finished - started) / 1000));
}

// Per-task pipeline view for a run. The API run carries only ordered
// progress (tasks-completed) and a status, not per-task records, so the
// task labels come from the job's ordered task-kinds and each node's
// state is derived: the tasks before the cursor are `ok`, the one at the
// cursor is `running`/`failed` by the run status, and the rest are
// `pending` (a live run) or `skipped` (a failed run — the first failure
// ends it). With no run yet, every task is `pending`.
export function pipelineSteps(taskKinds, run) {
  const tasks = taskKinds ?? [];
  if (!run) return tasks.map((name) => ({ name, status: "pending" }));
  const done = run["tasks-completed"] ?? 0;
  const { status } = run;
  return tasks.map((name, i) => {
    if (i < done) return { name, status: "ok" };
    if (i === done) {
      if (status === "failed") return { name, status: "failed" };
      if (status === "running") return { name, status: "running" };
      return { name, status: "ok" };
    }
    return { name, status: status === "failed" ? "skipped" : "pending" };
  });
}

// The next `n` fire times (epoch ms, UTC) strictly after `fromMs`, for
// the drawer's schedule preview. Walks `nextRunAt` forward.
export function nextRuns(job, fromMs, n) {
  const out = [];
  let from = fromMs;
  for (let i = 0; i < n; i += 1) {
    const t = nextRunAt(job, from);
    if (t == null) break;
    out.push(t);
    from = t;
  }
  return out;
}

// "HH:MM" → minutes past midnight, or null when unparseable.
export function minutesFromHHMM(s) {
  const [h, m] = (s ?? "").split(":").map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}
