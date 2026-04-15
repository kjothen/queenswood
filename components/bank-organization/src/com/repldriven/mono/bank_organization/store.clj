(ns com.repldriven.mono.bank-organization.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- load-organizations
  [store]
  (mapv schema/pb->Organization
        (:records (fdb/scan-records store {:limit 100}))))

(defn- load-organizations-by-type
  [store org-type]
  (mapv schema/pb->Organization
        (fdb/query-records
         store
         "Organization"
         "type"
         (schema/organization-type->pb-enum org-type))))

(defn- load-organization-by-id
  [store org-id]
  (if-let [record (fdb/load-record store org-id)]
    (schema/pb->Organization record)
    (error/reject :organization/not-found
                  {:message "Organization not found"
                   :organization-id org-id})))

(defn get-organization-by-id
  "Loads an organization by id. Returns the organization
  map or rejection anomaly if not found."
  [txn org-id]
  (fdb/transact txn
                (fn [txn]
                  (load-organization-by-id (fdb/open txn "organizations")
                                           org-id))
                :organization/get
                "Failed to load organization"))

(defn count-organizations-by-type
  "Returns the count of organizations matching the given
  type. Uses the Organization_count_by_type count index."
  [txn org-type]
  (fdb/transact txn
                (fn [txn]
                  (fdb/count-records
                   (fdb/open txn "organizations")
                   "Organization_count_by_type"
                   (.getNumber
                    (schema/organization-type->pb-enum org-type))))
                :organization/count-by-type
                "Failed to count organizations by type"))

(defn create
  "Persists an organization and its initial API key
  atomically. Returns nil or anomaly."
  [txn org api-key]
  (fdb/transact txn
                (fn [txn]
                  (fdb/save-record (fdb/open txn "organizations")
                                   (schema/Organization->java org))
                  (fdb/save-record (fdb/open txn "api-keys")
                                   (schema/ApiKey->java api-key)))
                :organization/create
                "Failed to create organization"))

(defn get-organizations
  "Lists organizations. Returns a sequence of organization
  maps or anomaly."
  [txn]
  (fdb/transact txn
                (fn [txn]
                  (load-organizations (fdb/open txn "organizations")))
                :organization/list
                "Failed to list organizations"))

(defn get-organizations-by-type
  "Lists organizations matching the given type. Returns
  a sequence of organization maps or anomaly."
  [txn org-type]
  (fdb/transact txn
                (fn [txn]
                  (load-organizations-by-type
                   (fdb/open txn "organizations")
                   org-type))
                :organization/list-by-type
                "Failed to list organizations by type"))
