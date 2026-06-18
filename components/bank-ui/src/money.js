// money.js — currency formatting for ledger / balance figures.
//
// Money is held in MINOR UNITS (pence / cents) everywhere in the ledger
// so arithmetic stays exact; format only at the display edge.
//
//   formatMoney(482014055, "GBP")  -> "£4,820,140.55"
//   formatMoney(-4598020, "GBP")   -> "−£45,980.20"   (true minus sign)
//   moneyTone(-1) -> "neg"  moneyTone(0) -> "zero"  moneyTone(1) -> "pos"

export const CCY_SYMBOLS = { GBP: "£", EUR: "€", USD: "$" };

export function formatMoney(minor, ccy = "GBP", { locale = "en-GB" } = {}) {
  const sym = CCY_SYMBOLS[ccy] ?? "";
  const neg = minor < 0;
  const abs = Math.abs(minor) / 100;
  const body = abs.toLocaleString(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  // U+2212 MINUS SIGN, not a hyphen — aligns with tabular figures.
  return (neg ? "−" : "") + sym + body;
}

// Like formatMoney but ALWAYS shows a leading sign (+ or −) — for
// transaction amounts and breakdown deltas where direction matters.
//   formatSigned(245000, "GBP")  -> "+£2,450.00"
//   formatSigned(-540, "GBP")    -> "−£5.40"
export function formatSigned(minor, ccy = "GBP", { locale = "en-GB" } = {}) {
  const sym = CCY_SYMBOLS[ccy] ?? "";
  const abs = Math.abs(minor) / 100;
  const body = abs.toLocaleString(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return (minor < 0 ? "−" : "+") + sym + body;
}

export function moneyTone(minor) {
  return minor < 0 ? "neg" : minor === 0 ? "zero" : "pos";
}

// Sum a list of balances (minor units). The available balance of a ledger
// account is the sum of its constituent balances — keep this the single
// source of that truth so the tree is a real decomposition.
export function sumMinor(balances) {
  return balances.reduce((acc, b) => acc + b.minor, 0);
}
