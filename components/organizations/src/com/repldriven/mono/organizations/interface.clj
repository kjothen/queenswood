(ns com.repldriven.mono.organizations.interface
  (:require
    [com.repldriven.mono.organizations.domain :as domain]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn create-organization
  "Creates an organization and its initial API key atomically.
  Returns {:organization <map> :api-key <map> :raw-key <string>}
  or anomaly. The raw-key is only available at creation time."
  [{:keys [record-db record-store]} org-name]
  (let [org (domain/new-organization org-name)
        key-data (domain/generate-api-key)
        api-key (domain/new-api-key (:organization-id org) "default" key-data)]
    (error/let-nom> [_ (fdb/transact-multi
                        record-db
                        record-store
                        (fn [open-store]
                          (let [org-store (open-store "organizations")
                                key-store (open-store "api-keys")]
                            (fdb/save-record org-store
                                             (schema/Organization->java org))
                            (fdb/save-record key-store
                                             (schema/ApiKey->java api-key)))))]
      {:organization org :api-key api-key :raw-key (:raw-key key-data)})))

(defn find-api-key-by-hash
  "Looks up an API key by its hash. Returns the ApiKey map
  or nil."
  [{:keys [record-db record-store]} key-hash]
  (fdb/transact
   record-db
   record-store
   "api-keys"
   (fn [store]
     (first (map schema/pb->ApiKey
                 (fdb/query-records store "ApiKey" "key_hash" key-hash))))))

(defn verify-api-key
  "Verifies a raw API key. Returns the ApiKey map if valid,
  nil otherwise."
  [config raw-key]
  (let [key-hash (domain/hash-raw-key raw-key)]
    (find-api-key-by-hash config key-hash)))
