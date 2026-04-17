(ns com.repldriven.mono.bank-party.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "parties")
(def ^:private person-identifications-store-name "person-identifications")
(def ^:private party-national-identifiers-store-name
  "party-national-identifiers")

(def transact fdb/transact)
(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-party
  "Saves party and writes a changelog entry. Returns the pb
  record or anomaly."
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

(defn save-person-identification
  "Saves a person identification. Returns nil or anomaly."
  [txn person-identification]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn person-identifications-store-name)
      (schema/PersonIdentification->java person-identification)))
   :party/save-person-identification
   "Failed to save person identification"))

(defn save-party-national-identifier
  "Saves a party national identifier. Returns nil or
  anomaly."
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
  "Loads a party by org-id and party-id. Returns the
  party map or rejection anomaly if not found."
  [txn org-id party-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      org-id
                                      party-id)]
       (schema/pb->Party record)
       (error/reject :party/not-found
                     {:message "Party not found"
                      :organization-id org-id
                      :party-id party-id})))
   :party/get
   "Failed to load party"))

(defn get-parties
  "Lists parties for an organization. Returns
  {:parties [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit."
  ([txn org-id]
   (get-parties txn org-id nil))
  ([txn org-id opts]
   (fdb/transact txn
                 (fn [txn]
                   (let [result (fdb/scan-records
                                 (fdb/open txn store-name)
                                 (merge {:prefix [org-id] :limit 100}
                                        (select-keys opts
                                                     [:after :before :limit])))]
                     {:parties (mapv schema/pb->Party (:records result))
                      :before (:before result)
                      :after (:after result)}))
                 :party/list
                 "Failed to list parties")))
