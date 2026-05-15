(ns com.repldriven.mono.bank-party.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "parties")
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
          _ (fdb/write-changelog store
                                 store-name
                                 (:party-id party)
                                 (schema/PartyChangelog->pb changelog))]
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

(defn get-party
  [txn org-id party-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name) org-id party-id)
             schema/pb->Party))
   :party/get
   "Failed to load party"))

(defn get-parties
  ([txn org-id]
   (get-parties txn org-id nil))
  ([txn org-id opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [after before limit order]
             :or {limit 100 order :desc}}
            opts
            result (fdb/scan-records
                    (fdb/open txn store-name)
                    {:prefix [org-id]
                     :after after
                     :before before
                     :limit limit
                     :order order})]
        {:parties (mapv schema/pb->Party (:records result))
         :before (:before result)
         :after (:after result)}))
    :party/list
    "Failed to list parties")))
