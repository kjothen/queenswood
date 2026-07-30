(ns com.repldriven.queenswood.party.core
  (:require
    [com.repldriven.queenswood.party.domain :as domain]
    [com.repldriven.queenswood.party.store :as store]

    [com.repldriven.queenswood.cash-account-query.interface :as cash-accounts]
    [com.repldriven.queenswood.party-query.interface :as q]
    [com.repldriven.queenswood.person-identification.interface :as person-id]
    [com.repldriven.queenswood.policy.interface :as policy]

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

(def ^:private idv-status->transition
  {:idv-status-accepted domain/activate-party
   :idv-status-rejected domain/reject-party})

(defn apply-idv-status
  "Advance a pending party to match the outcome of its identity
  verification. Statuses that are not terminal for the party, and
  parties that have already left pending, are no-ops — event
  redelivery and replay must not reject here."
  [txn bank-id party-id idv-status]
  (when-let [transition (idv-status->transition idv-status)]
    (store/transact
     txn
     (fn [txn]
       (let-nom> [party (q/get-party txn bank-id party-id)]
         (when (= :party-status-pending (:status party))
           (let [updated (transition party)]
             (store/save-party txn
                               updated
                               {:bank-id bank-id
                                :party-id party-id
                                :status-before (:status party)
                                :status-after (:status updated)}))))))))

(defn- has-open-accounts?
  [txn bank-id party-id]
  (let-nom> [accounts
             (cash-accounts/find-accounts-by-party txn bank-id party-id)]
    (boolean
     (some (fn [account]
             (not= :cash-account-status-closed (:account-status account)))
           accounts))))

(defn merge-party
  ([txn data]
   (merge-party txn data {}))
  ([txn data opts]
   (store/transact
    txn
    (fn [txn]
      (let [{:keys [bank-id party-id into-party-id]} data]
        (let-nom>
          [policies (or (:policies opts)
                        (policy/get-effective-policies txn {:bank-id bank-id}))
           survivor (q/get-party txn bank-id into-party-id)
           merged-away (q/get-party txn bank-id party-id)
           open-accounts? (has-open-accounts? txn bank-id party-id)
           updated (domain/merge-party survivor
                                       merged-away
                                       open-accounts?
                                       policies)
           result (store/save-party txn
                                    updated
                                    {:bank-id bank-id
                                     :party-id party-id
                                     :status-before (:status merged-away)
                                     :status-after (:status updated)})]
          result))))))
