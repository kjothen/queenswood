(ns com.repldriven.mono.bank-party.core
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- create-person
  "Creates a person party with person-identification and
  optional national-identifier in a single transaction."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [party (domain/new-party data)
           {:keys [national-identifier]} data
           {:keys [organization-id party-id status]} party
           person-id (domain/new-person-identification data party-id)]
       (let-nom>
         [_ (store/save-person-identification txn person-id)
          _ (when national-identifier
              (store/save-party-national-identifier
               txn
               (domain/new-party-national-identifier
                national-identifier
                organization-id
                party-id)))
          result (store/save-party
                  txn
                  party
                  {:organization-id organization-id
                   :party-id party-id
                   :status-after status})]
         result)))))

(defn- create-internal
  "Creates an internal party — no person-identification or
  national-identifier."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [party (domain/new-party data)
           {:keys [organization-id party-id status]} party]
       (store/save-party
        txn
        party
        {:organization-id organization-id
         :party-id party-id
         :status-after status})))))

(defn new-party
  "Creates a party. Person parties include
  person-identification and optional national-identifier.
  Internal and organization parties skip both. Returns
  protobuf party record or anomaly."
  [txn data]
  (let [result (if (= :party-type-person (:type data))
                 (create-person txn data)
                 (create-internal txn data))]
    (if (store/uniqueness-violation? result)
      (error/reject :party/duplicate-national-identifier
                    "National identifier already exists")
      result)))
