(ns com.repldriven.mono.bank-membership.core
  (:require
    [com.repldriven.mono.bank-membership.domain :as domain]
    [com.repldriven.mono.bank-membership.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn new-membership
  [txn {:keys [user-id bank-id role]}]
  (store/transact
   txn
   (fn [txn]
     (let [membership (domain/new-membership
                       {:user-id user-id
                        :bank-id bank-id
                        :role role})]
       (let-nom> [_ (store/create txn membership)]
         membership)))
   :membership/new
   "Failed to create membership"))

(defn list-by-user
  [txn user-id]
  (store/list-by-user txn user-id))

(defn list-by-bank
  [txn bank-id]
  (store/list-by-bank txn bank-id))

(defn find-by-id
  [txn membership-id]
  (store/get-membership txn membership-id))
