(ns com.repldriven.mono.bank-party.interface
  "Party records (people, organisations, internal entities) and the
  data linked to them: person identification, national identifiers,
  display name. Person parties start pending and transition to
  active when an IDV result lands on the changelog. Returns party
  maps (or nil/anomaly on lookups)."
  (:require
    com.repldriven.mono.bank-party.system

    [com.repldriven.mono.bank-party.core :as core]
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn new-party
  "Create a party. Person parties also persist a person-
  identification and optional national-identifier; internal and
  organisation parties skip both. Capability is checked against
  effective policies before the write.

  Args:
  - txn: FDB handle or open transaction.
  - data: party submission map (bank-id, type,
    display-name, optional person fields).
  - opts: optional map; `:policies` overrides policy resolution.

  Returns the party map or an anomaly."
  ([txn data]
   (new-party txn data {}))
  ([txn data opts]
   (let-nom> [pb (core/new-party txn data opts)]
     (schema/pb->Party pb))))

(defn get-party
  "Load a party by bank and id.

  Args:
  - txn: FDB handle or open transaction.
  - bank-id: bank id.
  - party-id: party id.

  Returns the party map or a `:party/not-found` anomaly."
  [txn bank-id party-id]
  (core/get-party txn bank-id party-id))

(defn get-party-detail
  "Load a party, optionally enriched with sub-records selected by the
  `embed` opts.

  Args:
  - txn: FDB handle or open transaction.
  - bank-id: bank id.
  - party-id: party id.
  - opts: embed flags — `:person-identification` (given/middle/family
    names, date-of-birth, nationality), `:address`, and
    `:national-identifier`. Each truthy flag opts that sub-record into
    the result; omit them all for just the summary.

  Returns the (possibly enriched) party map, or a `:party/not-found`
  anomaly. Enrichment is best-effort — a sub-record read failure is
  skipped, never propagated. Internal and organisation parties carry no
  person identification, so those embeds are no-ops for them."
  [txn bank-id party-id opts]
  (core/get-party-detail txn bank-id party-id opts))

(defn get-parties
  "List parties for a bank in a paged result.

  Args:
  - txn: FDB handle or open transaction.
  - bank-id: bank id.
  - opts: optional map with :after, :before, :limit, :order.

  Returns `{:parties [...] :before id|nil :after id|nil}` or an
  anomaly."
  ([txn bank-id]
   (store/get-parties txn bank-id))
  ([txn bank-id opts]
   (store/get-parties txn bank-id opts)))

(defn match-name
  "Compare a stored party name against a query name, returning a
  match grade.

  Args:
  - party-name: the stored display name.
  - query-name: the name to compare against.

  Returns `:match`, `:close-match`, or `:no-match`."
  [party-name query-name]
  (domain/match-name party-name query-name))

(defn seed-active-party
  "Activate a pending party by writing the status transition
  directly, bypassing the IDV → changelog-watcher path that
  activates parties in production. Test/admin shim retained while
  harnesses can't drive a real IDV flow.

  Args:
  - txn: FDB handle or open transaction.
  - bank-id: bank id.
  - party-id: party id to activate.

  Returns the active party (pb record) or an anomaly."
  [txn bank-id party-id]
  (let-nom>
    [party (core/get-party txn bank-id party-id)
     activated (domain/activate-party party)
     saved (store/save-party txn
                             activated
                             {:bank-id bank-id
                              :party-id party-id
                              :status-before (:status party)
                              :status-after (:status activated)})]
    saved))
