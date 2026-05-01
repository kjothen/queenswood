(ns com.repldriven.mono.bank-party.interface
  (:require
    com.repldriven.mono.bank-party.system

    [com.repldriven.mono.bank-party.core :as core]
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn new-party
  "Creates a party. Returns party map or anomaly. opts
  supports `:policies` to override policy resolution for the
  capability check."
  ([txn data]
   (new-party txn data {}))
  ([txn data opts]
   (let-nom> [pb (core/new-party txn data opts)]
     (schema/pb->Party pb))))

(defn get-party
  "Loads a party by org-id and party-id. Returns party
  map or rejection anomaly if not found."
  [txn org-id party-id]
  (store/get-party txn org-id party-id))

(defn get-parties
  "Lists parties for an organization. Returns
  {:parties [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit."
  ([txn org-id]
   (store/get-parties txn org-id))
  ([txn org-id opts]
   (store/get-parties txn org-id opts)))

(defn match-name
  "Compares party-name against query-name. Returns
  :match, :close-match, or :no-match."
  [party-name query-name]
  (domain/match-name party-name query-name))

(defn seed-active-party
  "Test/admin shortcut: marks `party-id` active by writing the
  status transition directly to the store, bypassing the IDV →
  changelog-watcher path that activates parties in production.

  This exists because there's no IDV simulator yet (in the same
  spirit as `bank-clearbank-simulator`); harnesses that need an
  active person-party use this as a transitional shim. Delete it
  when an IDV simulator lands and tests can drive the real
  pending → IDV-accepted → active flow.

  Returns the active party (pb record) or anomaly."
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
