(ns com.repldriven.mono.bank-party.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- load-party
  [store org-id party-id]
  (if-let [record (fdb/load-record store org-id party-id)]
    (schema/pb->Party record)
    (error/reject :party/not-found
                  {:message "Party not found"
                   :organization-id org-id
                   :party-id party-id})))

(defn get-party
  "Loads a party by org-id and party-id. Returns the
  party map or rejection anomaly if not found."
  [txn org-id party-id]
  (fdb/transact txn
                (fn [txn]
                  (load-party (fdb/open txn "parties") org-id party-id))
                :party/get
                "Failed to load party"))

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
                  (load-parties (fdb/open txn "parties") org-id))
                :party/list
                "Failed to list parties"))
