(ns com.repldriven.mono.bank-party.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb])
  (:import
    (com.apple.foundationdb.record RecordIndexUniquenessViolation)))

(def ^:private store-name "parties")
(def ^:private person-identifications-store-name "person-identifications")
(def ^:private party-national-identifiers-store-name
  "party-national-identifiers")

(def transact fdb/transact)

(defn uniqueness-violation?
  "Returns true if anomaly was caused by a
  RecordIndexUniquenessViolation."
  [anomaly]
  (when (error/anomaly? anomaly)
    (loop [ex (:exception (error/payload anomaly))]
      (cond
       (nil? ex)
       false

       (instance? RecordIndexUniquenessViolation ex)
       true

       :else
       (recur (.getCause ex))))))

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

(defn find-party
  "Loads a party by org-id and party-id if it exists.
  Returns the party map, nil, or anomaly on I/O failure.
  For existence probes (e.g. watcher handlers)."
  [txn org-id party-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name) org-id party-id)
             schema/pb->Party))
   :party/find
   "Failed to load party"))

(defn get-party
  "Loads a party by org-id and party-id. Returns the
  party map or rejection anomaly if not found."
  [txn org-id party-id]
  (let-nom> [party (find-party txn org-id party-id)]
    (or party
        (error/reject :party/not-found
                      {:message "Party not found"
                       :organization-id org-id
                       :party-id party-id}))))

(defn- load-parties
  [store org-id]
  (mapv schema/pb->Party
        (:records
         (fdb/scan-records store
                           {:prefix [org-id]
                            :limit 100}))))

(defn get-parties
  "Lists parties for an organization. Returns a sequence of
  party maps or anomaly."
  [txn org-id]
  (fdb/transact txn
                (fn [txn]
                  (load-parties (fdb/open txn store-name) org-id))
                :party/list
                "Failed to list parties"))
