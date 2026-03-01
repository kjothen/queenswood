(ns com.repldriven.mono.fdb.interface
  (:require
    com.repldriven.mono.fdb.system.core

    [com.repldriven.mono.fdb.changelog.core :as changelog]
    [com.repldriven.mono.fdb.core :as core]
    [com.repldriven.mono.fdb.record :as record]))

(defn set-str [db key value] (core/set-str db key value))

(defn get-str [db key] (core/get-str db key))

(defn set-bytes [db key value] (core/set-bytes db key value))

(defn get-bytes [db key] (core/get-bytes db key))

(defn load-record
  ([store primary-key] (record/load store primary-key))
  ([record-db open-store-fn store-name primary-key]
   (core/load-record record-db open-store-fn store-name primary-key)))

(defn save-record [store record] (record/save store record))

(defn query-records
  [store record-type field value]
  (record/query store record-type field value))

(defn write-changelog
  [ctx store-name record-id]
  (changelog/write ctx store-name record-id))

(defn process-changelog
  ([record-db open-store-fn consumer-id store-name handler]
   (changelog/process record-db open-store-fn consumer-id store-name handler))
  ([record-db open-store-fn consumer-id store-name handler opts]
   (changelog/process record-db
                      open-store-fn
                      consumer-id
                      store-name
                      handler
                      opts)))

(defn run [record-db f] (core/run record-db f))
