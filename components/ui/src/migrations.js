// Cash-account migration view-model helpers — badge tones, the
// ineligibility vocabulary, and the date/rate formatting the Migrations
// screen shares between its rail, hero and preview panels. The screen
// itself lives in console.

// Wire enums arrive short ("draft") but tolerate the namespaced keyword
// spelling too, the way the Accounts screen does.
export function shortEnum(x) {
  return String(x ?? "")
    .replace(/^:/, "")
    .replace(
      /^(cash-account-migration-status|cash-account-migration-run-status|cash-account-migration-outcome|cash-account-migration-ineligibility|version-status|product-type)-/,
      "",
    );
}

// A migration draft is inert rather than in-flight, so it takes the
// grey `archived` tone rather than the amber one a product draft uses.
export const MIGRATION_TONE = {
  draft: "archived",
  approved: "pending",
  completed: "published",
  cancelled: "rejected",
};

export const RUN_TONE = {
  running: "running",
  completed: "succeeded",
  failed: "failed",
};

export const OUTCOME_TONE = {
  migrated: "published",
  eligible: "pending",
  ineligible: "draft",
  failed: "failed",
};

// Fixed reporting order, most-common authoring mistake first. Matches
// the order the domain evaluates them in, so the reason an account
// carries is always the most specific one that applies.
export const INELIGIBILITY_ORDER = [
  "version-not-in-source",
  "already-on-target",
  "account-not-open",
  "currency-not-allowed",
];

export const INELIGIBILITY_LABEL = {
  "version-not-in-source": "Not on a selected source version",
  "already-on-target": "Already on the target version",
  "account-not-open": "Account is not open",
  "currency-not-allowed": "Currency not allowed on target",
};

// The rejections bank-api raises on create. Surfaced in the console
// before the POST so approval is never a surprise 4xx, and shown with
// their wire `type` because operators quote these to engineers.
export const MIGRATION_GUARDS = {
  "source-product-not-found": "cash-account-migration/source-product-not-found",
  "target-is-source": "cash-account-migration/target-is-source",
  "product-type-mismatch": "cash-account-migration/product-type-mismatch",
  "target-not-published": "cash-account-migration/target-not-published",
  "notice-after-due": "cash-account-migration/notice-after-due",
};

export function fmtDay(iso) {
  if (!iso) return null;
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

export function fmtStamp(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const day = d.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
  const time = d.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  });
  return `${day} · ${time}`;
}

export function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

// Whole days between two ISO dates, positive when `to` is later.
export function daysBetween(fromIso, toIso) {
  if (!fromIso || !toIso) return null;
  const a = Date.parse(`${fromIso}T00:00:00Z`);
  const b = Date.parse(`${toIso}T00:00:00Z`);
  if (Number.isNaN(a) || Number.isNaN(b)) return null;
  return Math.round((b - a) / 86400000);
}

export function fmtRate(bps) {
  return typeof bps === "number" ? `${(bps / 100).toFixed(2)}%` : null;
}

// Rate delta against the highest of the selected source rates — the
// rate the best-off customer on the source is giving up.
export function fmtRateDelta(targetBps, sourceBpsList) {
  const rates = (sourceBpsList ?? []).filter((r) => typeof r === "number");
  if (typeof targetBps !== "number" || rates.length === 0) return null;
  const pp = (targetBps - Math.max(...rates)) / 100;
  if (pp === 0) return { text: "none", tone: "flat" };
  return {
    text: `${pp > 0 ? "+" : "−"}${Math.abs(pp).toFixed(2)} pp`,
    tone: pp > 0 ? "up" : "down",
  };
}

export function fmtDuration(startedAt, finishedAt) {
  if (!startedAt) return "—";
  if (!finishedAt) return "in progress";
  const ms = Date.parse(finishedAt) - Date.parse(startedAt);
  if (Number.isNaN(ms) || ms < 0) return "—";
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
}
