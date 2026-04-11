(ns com.repldriven.mono.bank-organization.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn count-organizations-by-type
  "Returns the count of organizations matching the given
  type. Uses the Organization_count_by_type count index."
  [{:keys [record-db record-store]} org-type]
  (try-nom :organization/count-by-type
           "Failed to count organizations by type"
           (fdb/transact
            record-db
            record-store
            "organizations"
            (fn [store]
              (fdb/count-records
               store
               "Organization_count_by_type"
               (.getNumber
                (schema/organization-type->pb-enum
                 org-type)))))))

(defn create
  "Persists an organization and its initial API key
  atomically. Returns nil or anomaly."
  [{:keys [record-db record-store]} org api-key]
  (try-nom
   :organization/create
   "Failed to create organization"
   (fdb/transact-multi
    record-db
    record-store
    (fn [open-store]
      (let [org-store (open-store "organizations")
            key-store (open-store "api-keys")]
        (fdb/save-record org-store (schema/Organization->java org))
        (fdb/save-record key-store (schema/ApiKey->java api-key)))))))

(defn get-organizations
  "Lists organizations. Returns a sequence of organization
  maps or anomaly."
  [{:keys [record-db record-store]}]
  (try-nom :organization/list
           "Failed to list organizations"
           (fdb/transact record-db
                         record-store
                         "organizations"
                         (fn [store]
                           (mapv schema/pb->Organization
                                 (:records
                                  (fdb/scan-records store
                                                    {:limit 100})))))))

(defn get-organizations-by-type
  "Lists organizations matching the given type. Returns
  a sequence of organization maps or anomaly."
  [{:keys [record-db record-store]} org-type]
  (try-nom :organization/list-by-type
           "Failed to list organizations by type"
           (fdb/transact record-db
                         record-store
                         "organizations"
                         (fn [store]
                           (mapv schema/pb->Organization
                                 (fdb/query-records
                                  store
                                  "Organization"
                                  "type"
                                  (schema/organization-type->pb-enum
                                   org-type)))))))
