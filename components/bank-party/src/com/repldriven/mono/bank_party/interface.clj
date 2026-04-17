(ns com.repldriven.mono.bank-party.interface
  (:require
    com.repldriven.mono.bank-party.system

    [com.repldriven.mono.bank-party.core :as core]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn new-party
  "Creates a party. Returns party map or anomaly."
  [txn data]
  (let-nom> [pb (core/new-party txn data)]
    (schema/pb->Party pb)))

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
