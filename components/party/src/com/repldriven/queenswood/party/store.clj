(ns com.repldriven.queenswood.party.store
  (:require
    [com.repldriven.queenswood.party.changelog :as changelog]

    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

;; must match bank-party-query.store/store-name — same FDB store
(def ^:private store-name "parties")
;; must match bank-party-query.store/party-national-identifiers-store-name
(def ^:private party-national-identifiers-store-name
  "party-national-identifiers")

(def transact fdb/transact)
(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-party
  [txn party changelog]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (let-nom>
         [_ (fdb/save-record store (schema/Party->java party))
          _ (fdb/write-changelog txn
                                 store-name
                                 (:party-id party)
                                 (changelog/status-changed
                                  (assoc changelog
                                         :bank-id
                                         (:bank-id party))))]
         (schema/Party->pb party))))
   :party/save
   "Failed to save party"))

(defn save-party-national-identifier
  [txn party-national-identifier]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn party-national-identifiers-store-name)
      (schema/PartyNationalIdentifier->java party-national-identifier)))
   :party/save-party-national-identifier
   "Failed to save party national identifier"))
