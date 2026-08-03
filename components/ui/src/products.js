// Cash-account-product view-model helpers — pure, presentation-only.
// The wire shape is the bank-api `/v1/cash-account-products` item:
// { product-id, versions: [{ version-id, version-number, status,
// effective-from, effective-to, ... }] }.
//
// A product is a timeline, not a single record. Several versions can be
// published at once — each owns a half-open `[effective-from,
// effective-to)` window, and publishing a successor does not retire its
// predecessor, it just bounds it. Dates arrive as ISO `YYYY-MM-DD`
// strings, which compare correctly as strings, so nothing here parses a
// date.

const PUBLISHED = "published";
const DRAFT = "draft";

// Today in UTC, in the same `YYYY-MM-DD` form the API sends.
export function today() {
  return new Date().toISOString().slice(0, 10);
}

function isEffective(version, asOf) {
  const from = version["effective-from"];
  const to = version["effective-to"];
  return (from == null || from <= asOf) && (to == null || asOf < to);
}

// Latest by effective-from, then version-number — the same tie-break
// the backend's active-version uses, so the console and the ledger
// agree on which version an account opened today would pin to.
function latest(versions) {
  return [...versions].sort((a, b) => {
    const from = (a["effective-from"] ?? "").localeCompare(
      b["effective-from"] ?? "",
    );
    return from !== 0
      ? from
      : (a["version-number"] ?? 0) - (b["version-number"] ?? 0);
  })[versions.length - 1];
}

// The published version in force on `asOf`, or null. Mirrors
// active-version in cash-account-product-query — a product's headline
// figures are whichever version is effective, not whichever was
// published last.
export function activeVersion(item, asOf = today()) {
  const live = (item?.versions ?? [])
    .filter((v) => v.status === PUBLISHED)
    .filter((v) => isEffective(v, asOf));
  return live.length ? latest(live) : null;
}

// One row per product: the version to show on the mainline, everything
// else as its history, and the counts the mainline needs to advertise
// what the history holds.
//
// The mainline is the effective version. Failing that — a product whose
// only version is an unpublished draft, or whose published versions are
// all still ahead of their start date — it is the earliest version the
// product has, so a product is never represented by nothing.
//
// A pending draft stays out of the mainline but is counted, because
// burying it silently would bury its publish and discard actions with
// it.
export function productRows(items, asOf = today()) {
  return (items ?? []).map((item) => {
    const versions = item.versions ?? [];
    const active = activeVersion(item, asOf);
    const upcoming = versions
      .filter((v) => v.status === PUBLISHED && (v["effective-from"] ?? "") > asOf)
      .sort((a, b) =>
        (a["effective-from"] ?? "").localeCompare(b["effective-from"] ?? ""),
      );
    const drafts = versions.filter((v) => v.status === DRAFT);
    const mainline = active ?? upcoming[0] ?? versions[0] ?? null;
    return {
      productId: item["product-id"],
      mainline,
      // Effective today, as opposed to merely the best row to show.
      live: active != null && active === mainline,
      history: versions.filter((v) => v !== mainline),
      draftCount: drafts.length,
      upcomingCount: upcoming.filter((v) => v !== mainline).length,
      versionCount: versions.length,
    };
  });
}
