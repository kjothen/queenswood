(ns com.repldriven.mono.fdb.store
  (:require
    [com.repldriven.mono.fdb.keyspace :as keyspace])
  (:import
    (com.apple.foundationdb.record.provider.foundationdb FDBRecordStore)))

(defn- builder
  [ctx registry store-name]
  (let [meta (get registry store-name)
        path (keyspace/records-path store-name)]
    (when-not meta (throw (ex-info "Unknown record store" {:store store-name})))
    (-> (FDBRecordStore/newBuilder)
        (.setMetaDataProvider meta)
        (.setContext ctx)
        (.setKeySpacePath path))))

(defn create-store
  "Creates the named record store in FDB if it does not already exist,
  or opens it if it does. Must be called before open-record-store."
  [ctx registry store-name]
  (.createOrOpen (builder ctx registry store-name)))

(defn open-record-store
  "Opens an existing record store within the given context. Throws if
  the store has not been created first via create-store."
  [ctx registry store-name]
  (.open (builder ctx registry store-name)))
