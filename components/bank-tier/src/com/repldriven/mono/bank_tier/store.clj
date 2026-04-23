(ns com.repldriven.mono.bank-tier.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "tiers")
(def ^:private organizations-store-name "organizations")

(def transact fdb/transact)

(defn- load-tiers
  [store {:keys [limit order] :or {limit 100 order :desc}}]
  (mapv schema/pb->Tier
        (:records (fdb/scan-records store {:limit limit :order order}))))

(defn- load-tier
  [store tier-id]
  (if-let [record (fdb/load-record store tier-id)]
    (schema/pb->Tier record)
    (error/reject :tier/not-found
                  {:message "Tier not found"
                   :tier-id tier-id})))

(defn- load-org
  [store org-id]
  (if-let [record (fdb/load-record store org-id)]
    (schema/pb->Organization record)
    (error/reject :tier/org-unknown
                  {:message "Organization not found"
                   :organization-id org-id})))

(defn create
  "Persists a Tier record. Returns nil or anomaly."
  [txn tier]
  (fdb/transact txn
                (fn [txn]
                  (fdb/save-record (fdb/open txn store-name)
                                   (schema/Tier->java tier)))
                :tier/create
                "Failed to create tier"))

(defn save
  "Persists an updated Tier record. Returns nil or
  anomaly."
  [txn tier]
  (fdb/transact txn
                (fn [txn]
                  (fdb/save-record (fdb/open txn store-name)
                                   (schema/Tier->java tier)))
                :tier/update
                "Failed to update tier"))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly. opts supports :limit and :order (`:desc` default
  — clients show newest-first)."
  ([txn] (get-tiers txn nil))
  ([txn opts]
   (fdb/transact txn
                 (fn [txn]
                   (load-tiers (fdb/open txn store-name) opts))
                 :tier/list
                 "Failed to list tiers")))

(defn get-tier
  "Finds a Tier by tier-id. Returns the Tier map or
  rejection anomaly if not found."
  [txn tier-id]
  (fdb/transact txn
                (fn [txn]
                  (load-tier (fdb/open txn store-name) tier-id))
                :tier/retrieve
                "Failed to retrieve tier"))

(defn get-org-tier
  "Loads the tier for the given organization. Opens the
  organizations store to extract tier-id, then loads the
  tier. Returns the Tier map or rejection anomaly."
  [txn org-id]
  (fdb/transact txn
                (fn [txn]
                  (let-nom>
                    [org (load-org (fdb/open txn organizations-store-name)
                                   org-id)]
                    (get-tier txn (:tier-id org))))
                :tier/load-org
                "Failed to load organization"))
