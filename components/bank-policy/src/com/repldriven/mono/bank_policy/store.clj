(ns com.repldriven.mono.bank-policy.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "policies")
(def ^:private bindings-store-name "policy-bindings")

(def transact fdb/transact)

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
       (schema/pb->Policy record)
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
        {:items (mapv schema/pb->Policy (:records result))
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
     (mapv schema/pb->Policy
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
       (schema/pb->PolicyBinding record)
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
        {:items (mapv schema/pb->PolicyBinding (:records result))
         :before (:before result)
         :after (:after result)}))
    :policy-binding/list
    "Failed to list policy bindings")))
