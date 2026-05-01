(ns com.repldriven.mono.bank-policy.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.utility.interface :as util]))

(def ^:private store-name "policies")
(def ^:private bindings-store-name "policy-bindings")

(def transact fdb/transact)

(defn- pb->Policy
  "Reads a policy from FDB and normalises its proto records to plain
  Clojure maps. Downstream code (`bank-policy/match`, etc.) compares
  via `=`, which treats records and content-equal maps as unequal —
  so the round-trip leaves nested records (e.g. `ComputedBalance`
  inside a `BalanceLimitFilter`) silently mismatching the runtime
  request. Strip records here once at the read boundary."
  [record]
  (util/record->map (schema/pb->Policy record)))

(defn- pb->PolicyBinding
  "Same as `pb->Policy` for `PolicyBinding`."
  [record]
  (util/record->map (schema/pb->PolicyBinding record)))

(defn save-policy
  "Saves a policy. Returns nil or anomaly."
  [txn policy]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/Policy->java policy)))
   :policy/save
   "Failed to save policy"))

(defn get-policy
  "Loads a policy by policy-id. Returns the policy map or
  rejection anomaly if not found."
  [txn policy-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      policy-id)]
       (pb->Policy record)
       (error/reject :policy/not-found
                     {:message "Policy not found"
                      :policy-id policy-id})))
   :policy/get
   "Failed to load policy"))

(defn get-policies
  "Lists policies. Returns
  {:items [maps] :before id|nil :after id|nil} or anomaly.
  opts supports :after, :before, :limit, :order (`:desc`
  default — clients show newest-first)."
  ([txn]
   (get-policies txn nil))
  ([txn opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [after before limit order]
             :or {limit 20 order :desc}}
            opts
            result (fdb/scan-records
                    (fdb/open txn store-name)
                    {:after after
                     :before before
                     :limit limit
                     :order order})]
        {:items (mapv pb->Policy (:records result))
         :before (:before result)
         :after (:after result)}))
    :policy/list
    "Failed to list policies")))

(defn get-policies-by-label
  "Loads all policies whose `labels` map contains an entry
  matching the given key/value. Uses the Policy_by_label
  index."
  [txn label-key label-value]
  (fdb/transact
   txn
   (fn [txn]
     (mapv pb->Policy
           (fdb/query-records-by-map-entry (fdb/open txn store-name)
                                           "Policy"
                                           "labels"
                                           label-key
                                           label-value
                                           {:index "Policy_by_label"})))
   :policy/list-by-label
   {:message "Failed to list policies by label"
    :label-key label-key
    :label-value label-value}))

(defn save-binding
  "Saves a policy binding. Returns nil or anomaly."
  [txn binding]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn bindings-store-name)
                      (schema/PolicyBinding->java binding)))
   :policy-binding/save
   "Failed to save policy binding"))

(defn get-binding
  "Loads a binding by binding-id. Returns the binding map
  or rejection anomaly if not found."
  [txn binding-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn bindings-store-name)
                                      binding-id)]
       (pb->PolicyBinding record)
       (error/reject :policy-binding/not-found
                     {:message "Policy binding not found"
                      :binding-id binding-id})))
   :policy-binding/get
   "Failed to load policy binding"))

(defn get-bindings
  "Lists policy bindings. Returns
  {:items [maps] :before id|nil :after id|nil} or anomaly.
  opts supports :after, :before, :limit, :order (`:desc`
  default — clients show newest-first)."
  ([txn]
   (get-bindings txn nil))
  ([txn opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [after before limit order]
             :or {limit 20 order :desc}}
            opts
            result (fdb/scan-records
                    (fdb/open txn bindings-store-name)
                    {:after after
                     :before before
                     :limit limit
                     :order order})]
        {:items (mapv pb->PolicyBinding (:records result))
         :before (:before result)
         :after (:after result)}))
    :policy-binding/list
    "Failed to list policy bindings")))
