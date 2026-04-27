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

(defn new-api-key
  ([txn org-id status key-name]
   (new-api-key txn org-id status key-name {}))
  ([txn org-id status key-name opts]
   (let-nom>
     [policies (get-policies txn org-id opts)
      api-key-count (store/count-api-keys-by-org txn org-id)]
     (domain/new-api-key org-id
                         status
                         key-name
                         {:api-key {:count api-key-count}}
                         policies))))
