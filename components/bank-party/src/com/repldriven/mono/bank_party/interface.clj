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
  - data: party submission map (organization-id, type,
    display-name, optional person fields).
  - opts: optional map; `:policies` overrides policy resolution.

  Returns the party map or an anomaly."
  ([txn data]
   (new-party txn data {}))
  ([txn data opts]
   (let-nom> [pb (core/new-party txn data opts)]
     (schema/pb->Party pb))))

(defn get-party
  "Load a party by organisation and id.

  Args:
  - txn: FDB handle or open transaction.
  - org-id: organisation id.
  - party-id: party id.

  Returns the party map or a `:party/not-found` anomaly."
  [txn org-id party-id]
  (store/get-party txn org-id party-id))

(defn get-parties
  "List parties for an organisation in a paged result.

  Args:
  - txn: FDB handle or open transaction.
  - org-id: organisation id.
  - opts: optional map with :after, :before, :limit, :order.

  Returns `{:parties [...] :before id|nil :after id|nil}` or an
  anomaly."
  ([txn org-id]
   (store/get-parties txn org-id))
  ([txn org-id opts]
   (store/get-parties txn org-id opts)))

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
  - organization-id: organisation id.
  - party-id: party id to activate.

  Returns the active party (pb record) or an anomaly."
  [txn organization-id party-id]
  (let-nom>
    [party (store/get-party txn organization-id party-id)
     activated (domain/activate-party party)
     saved (store/save-party txn
                             activated
                             {:organization-id organization-id
                              :party-id party-id
                              :status-before (:status party)
                              :status-after (:status activated)})]
    saved))
