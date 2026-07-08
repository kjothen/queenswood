(ns com.repldriven.mono.bank-party.core
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.bank-person-identification.interface :as person-id]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- create-person
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [party (domain/new-party data)
           {:keys [national-identifier]} data
           {:keys [bank-id party-id status]} party
           pi (person-id/new-person-identification data party-id)]
       (let-nom>
         [_ (person-id/save-person-identification txn pi)
          _ (when national-identifier
              (store/save-party-national-identifier
               txn
               (domain/new-party-national-identifier
                national-identifier
                bank-id
                party-id)))
          result (store/save-party
                  txn
                  party
                  {:bank-id bank-id
                   :party-id party-id
                   :status-after status})]
         result)))))

(defn- create-internal
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [party (domain/new-party data)
           {:keys [bank-id party-id status]} party]
       (store/save-party
        txn
        party
        {:bank-id bank-id
         :party-id party-id
         :status-after status})))))

(defn new-party
  ([txn data]
   (new-party txn data {}))
  ([txn data opts]
   (let-nom>
     [policies (or (:policies opts)
                   (policy/get-effective-policies
                    txn
                    {:bank-id (:bank-id data)}))
      _ (policy/check-capability policies
                                 :party
                                 {:action :party-action-create
                                  :type (:type data)})]
     (let [result (if (= :party-type-person (:type data))
                    (create-person txn data)
                    (create-internal txn data))]
       (if (store/uniqueness-violation? result)
         (error/reject :party/identification-rejected
                       "Identification rejected for this party")
         result)))))
