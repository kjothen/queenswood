(ns com.repldriven.mono.bank-party.interface
  "Party write side: create parties (people, organisations, internal
  entities) and their linked data (person identification, national
  identifier). Person parties start pending and transition to active
  when an IDV result lands on the changelog watcher; `seed-active-party`
  is an admin/test shortcut that bypasses it.

  Reads live in `bank-party-query`; this brick reuses them inside its
  own transactions. `bank-api` requires the query brick, not this one —
  party creation reaches the processor as a command over the bus."
  (:require
    com.repldriven.mono.bank-party.system

    [com.repldriven.mono.bank-party.core :as core]
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.bank-party-query.interface :as q]

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

(defn merge-party
  "Merge a suspended party into an active survivor. Direct,
  single-phase flip, no watcher leg — the merged-away party's
  status flips to `:party-status-merged` and records
  `:merged-into-party-id`. IDV/KYC and other party-linked records
  stay on the original party-id; cross-domain re-pointing (e.g.
  cash accounts) is a separate reaction (ADR-0008), not orchestrated
  here. Returns the updated party or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - data: map with `:bank-id`, `:party-id` (the merged-away party)
    and `:into-party-id` (the survivor).
  - opts (optional): map; `:policies` overrides policy resolution
    for the capability check."
  ([txn data]
   (merge-party txn data {}))
  ([txn data opts]
   (let-nom> [pb (core/merge-party txn data opts)]
     (schema/pb->Party pb))))

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
    [party (q/get-party txn bank-id party-id)
     activated (domain/activate-party party)
     saved (store/save-party txn
                             activated
                             {:bank-id bank-id
                              :party-id party-id
                              :status-before (:status party)
                              :status-after (:status activated)})]
    saved))
