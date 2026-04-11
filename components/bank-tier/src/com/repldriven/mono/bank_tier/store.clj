(ns com.repldriven.mono.bank-tier.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn create
  "Persists a Tier record. Returns nil or anomaly."
  [{:keys [record-db record-store]} tier]
  (try-nom
   :tier/create
   "Failed to create tier"
   (fdb/transact record-db
                 record-store
                 "tiers"
                 (fn [store]
                   (fdb/save-record store
                                    (schema/Tier->java tier))))))

(defn save
  "Persists an updated Tier record. Returns nil or
  anomaly."
  [{:keys [record-db record-store]} tier]
  (try-nom
   :tier/update
   "Failed to update tier"
   (fdb/transact record-db
                 record-store
                 "tiers"
                 (fn [store]
                   (fdb/save-record store
                                    (schema/Tier->java tier))))))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly."
  [{:keys [record-db record-store]}]
  (try-nom :tier/list
           "Failed to list tiers"
           (fdb/transact record-db
                         record-store
                         "tiers"
                         (fn [store]
                           (mapv schema/pb->Tier
                                 (:records
                                  (fdb/scan-records
                                   store
                                   {:limit 100})))))))

(defn get-tier
  "Finds a Tier by tier-type. Returns the Tier map, nil
  if not found, or anomaly."
  [{:keys [record-db record-store]} tier-type]
  (try-nom
   :tier/find
   "Failed to find tier"
   (fdb/transact record-db
                 record-store
                 "tiers"
                 (fn [store]
                   (some-> (fdb/load-record
                            store
                            (.getNumber
                             (schema/tier-type->pb-enum
                              tier-type)))
                           schema/pb->Tier)))))
