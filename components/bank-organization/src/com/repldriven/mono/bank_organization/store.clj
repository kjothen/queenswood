(ns com.repldriven.mono.bank-organization.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "organizations")
(def ^:private api-keys-store-name "api-keys")

(def transact fdb/transact)

(defn create
  "Persists an organization and its initial API key
  atomically. Returns nil or anomaly."
  [txn org api-key]
  (fdb/transact txn
                (fn [txn]
                  (fdb/save-record (fdb/open txn store-name)
                                   (schema/Organization->java org))
                  (fdb/save-record (fdb/open txn api-keys-store-name)
                                   (schema/ApiKey->java api-key)))
                :organization/create
                "Failed to create organization"))

(defn get-organization
  "Loads an organization by id. Returns the organization
  map or rejection anomaly if not found."
  [txn org-id]
  (fdb/transact txn
                (fn [txn]
                  (if-let [record (fdb/load-record (fdb/open txn store-name)
                                                   org-id)]
                    (schema/pb->Organization record)
                    (error/reject :organization/not-found
                                  {:message "Organization not found"
                                   :organization-id org-id})))
                :organization/get
                "Failed to load organization"))

(defn get-organizations
  "Lists organizations. Returns a sequence of organization
  maps or anomaly. opts supports :limit and :order (`:desc`
  default — clients show newest-first)."
  ([txn]
   (get-organizations txn nil))
  ([txn opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [limit order] :or {limit 100 order :desc}} opts]
        (mapv schema/pb->Organization
              (:records (fdb/scan-records
                         (fdb/open txn store-name)
                         {:limit limit :order order})))))
    :organization/list
    "Failed to list organizations")))

(defn count-organizations-by-type
  "Returns the count of organizations matching the given
  type. Uses the Organization_count_by_type count index."
  [txn org-type]
  (fdb/transact txn
                (fn [txn]
                  (fdb/count-records
                   (fdb/open txn store-name)
                   "Organization_count_by_type"
                   (.getNumber
                    (schema/organization-type->pb-enum org-type))))
                :organization/count-by-type
                "Failed to count organizations by type"))

(defn get-organizations-by-type
  "Lists organizations matching the given type. Pins the
  planner to the Organization_by_type index. Returns a
  sequence of organization maps or anomaly."
  [txn org-type]
  (fdb/transact txn
                (fn [txn]
                  (mapv schema/pb->Organization
                        (fdb/query-records
                         (fdb/open txn store-name)
                         "Organization"
                         "type"
                         (schema/organization-type->pb-enum org-type)
                         {:index "Organization_by_type"})))
                :organization/list-by-type
                "Failed to list organizations by type"))
