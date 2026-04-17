(ns com.repldriven.mono.bank-api-key.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "api-keys")

(def transact fdb/transact)

(defn redact
  [api-key]
  (dissoc api-key :key-hash))

(defn get-api-key
  "Loads an API key by its hash. Returns a redacted ApiKey 
  map or rejection anomaly if not found."
  [txn key-hash]
  (fdb/transact txn
                (fn [txn]
                  (if-let [record (fdb/load-record (fdb/open txn store-name)
                                                   key-hash)]
                    (redact (schema/pb->ApiKey record))
                    (error/reject :api-key/not-found
                                  {:message "API Key not found"})))
                :api-key/get
                {:message "Failed to load API key"}))

(defn get-api-keys
  "Lists all API keys for a given organization. Returns a
  sequence of ApiKey maps."
  [txn org-id]
  (fdb/transact txn
                (fn [txn]
                  (mapv (comp redact schema/pb->ApiKey)
                        (fdb/query-records
                         (fdb/open txn store-name)
                         "ApiKey"
                         "organization_id"
                         org-id
                         {:index "ApiKey_by_org"})))

                :api-key/list
                {:message "Failed to list API keys"
                 :organization-id org-id}))
