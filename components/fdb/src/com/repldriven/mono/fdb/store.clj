(ns com.repldriven.mono.fdb.store
  (:require
    [com.repldriven.mono.fdb.keyspace :as keyspace]
    [com.repldriven.mono.fdb.metadata :as metadata])
  (:import
    (com.apple.foundationdb.record.provider.foundationdb FDBRecordStore)))

(def ^:private store-metadata {"persons" (metadata/build-persons-metadata)})

(defn open-record-store
  "Opens or creates the named record store within the given context."
  [ctx store-name]
  (let [meta (get store-metadata store-name)
        path (keyspace/records-path store-name)]
    (when-not meta (throw (ex-info "Unknown record store" {:store store-name})))
    (-> (FDBRecordStore/newBuilder)
        (.setMetaDataProvider meta)
        (.setContext ctx)
        (.setKeySpacePath path)
        .createOrOpen)))
