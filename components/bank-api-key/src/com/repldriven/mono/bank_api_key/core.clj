(ns com.repldriven.mono.bank-api-key.core
  (:require
    [com.repldriven.mono.bank-api-key.domain :as domain]
    [com.repldriven.mono.bank-api-key.store :as store]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn- get-policies
  [txn org-id opts]
  (or (:policies opts)
      (policy/get-effective-policies txn {:organization-id org-id})))

(defn- counts
  "Builds the api-key aggregates map for the limit checks in
  `domain/new-api-key`. Each entry is keyed by the set of
  dimensions the count is grouped on."
  [txn org-id]
  (let-nom>
    [total (store/count-api-keys-by-org txn org-id)]
    {:api-key {#{:organization-id} total}}))

(defn new-api-key
  ([txn org-id status key-name]
   (new-api-key txn org-id status key-name {}))
  ([txn org-id status key-name opts]
   (let-nom>
     [policies (get-policies txn org-id opts)
      aggregates (counts txn org-id)]
     (domain/new-api-key org-id status key-name aggregates policies))))
