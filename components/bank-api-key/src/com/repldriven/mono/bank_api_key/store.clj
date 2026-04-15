(ns com.repldriven.mono.bank-api-key.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn get-api-key
  "Looks up an API key by its hash. Returns the ApiKey map
  or nil."
  [txn key-hash]
  (fdb/transact
   txn
   (fn [txn]
     (first (map schema/pb->ApiKey
                 (fdb/query-records (fdb/open txn "api-keys")
                                    "ApiKey"
                                    "key_hash"
                                    key-hash))))))

(defn- load-api-keys
  [store org-id]
  (mapv schema/pb->ApiKey
        (fdb/query-records store
                           "ApiKey"
                           "organization_id"
                           org-id)))

(defn get-api-keys
  "Lists all API keys for a given organization. Returns a
  sequence of ApiKey maps."
  [txn org-id]
  (fdb/transact txn
                (fn [txn]
                  (load-api-keys (fdb/open txn "api-keys") org-id))
                :api-key/list
                "Failed to list API keys"))
