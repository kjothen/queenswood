// bounds.js — formatting for a limit's Bound.
//
// A Limit is bounded by one of three shapes (proto: MaxBound / MinBound /
// RangeBound), each wrapping an Aggregate over a time window:
//
//   Bound =
//     | { kind: "max",   aggregate: Aggregate }
//     | { kind: "min",   aggregate: Aggregate }
//     | { kind: "range", min: Aggregate, max: Aggregate }
//
//   Aggregate =
//     | { type: "amount", minor: <int>, ccy: "GBP", window: TimeWindow }
//     | { type: "count",  value: <int>,            window: TimeWindow }
//
//   TimeWindow ∈ "instant" | "daily" | "weekly" | "monthly" | "rolling"
//
// AMOUNTS ARE MINOR UNITS (pence / cents) — same convention as the
// ledger's money.js, so "max £25,000 / day" is { minor: 2_500_000 }.
// Format only at the display edge. COUNTS are whole integers.

export const CCY_SYMBOLS = { GBP: "£", EUR: "€", USD: "$" };

// How each window renders next to the figure. "instant" is a point-in-time
// bound (a snapshot check, not a rolling total); the rest are per-period.
export const WINDOW_LABEL = {
  instant: "instant",
  daily: "/ day",
  weekly: "/ week",
  monthly: "/ month",
  rolling: "/ window",
};

// Format one Aggregate into { value, unit } parts so the caller can style
// the number and its unit separately.
//
//   formatAggregate({ type:"amount", minor: 2500000, ccy:"GBP" })
//     -> { value: "£25,000.00", unit: "GBP" }
//   formatAggregate({ type:"count", value: 100000 })
//     -> { value: "100,000", unit: "count" }
export function formatAggregate(agg, { locale = "en-GB" } = {}) {
  if (agg.type === "amount") {
    const sym = CCY_SYMBOLS[agg.ccy] ?? "";
    const abs = Math.abs(agg.minor) / 100;
    const body = abs.toLocaleString(locale, {
      minimumFractionDigits: abs % 1 ? 2 : 0,
      maximumFractionDigits: 2,
    });
    const sign = agg.minor < 0 ? "−" : ""; // U+2212 true minus
    return { value: sign + sym + body, unit: agg.ccy };
  }
  return { value: agg.value.toLocaleString(locale), unit: "count" };
}

// The operator word shown before the figure: "max" | "min" | "range".
export function boundOp(bound) {
  return bound.kind;
}

// The window for a bound (range uses its max's window, which equals min's).
export function boundWindow(bound) {
  return bound.kind === "range" ? bound.max.window : bound.aggregate.window;
}
