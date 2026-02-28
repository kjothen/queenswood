(ns com.repldriven.mono.fdb.interface
  (:require
    com.repldriven.mono.fdb.system.core

    [com.repldriven.mono.fdb.consumer :as consumer]
    [com.repldriven.mono.fdb.core :as core]
    [com.repldriven.mono.fdb.producer :as producer]))

(defn set-str [db key value] (core/set-str db key value))

(defn get-str [db key] (core/get-str db key))

(defn set-bytes [db key value] (core/set-bytes db key value))

(defn get-bytes [db key] (core/get-bytes db key))

(defn load-record
  [record-db store store-name primary-key]
  (core/load-record record-db store store-name primary-key))

(defn save-record
  [record-db store store-name record]
  (core/save-record record-db store store-name record))

(defn store-load
  [fdb-store primary-key]
  (core/store-load fdb-store primary-key))

(defn store-save [fdb-store record] (core/store-save fdb-store record))

(defn store-query
  [fdb-store record-type field value]
  (core/store-query fdb-store record-type field value))

(defn write-changelog
  [ctx store-name record-id]
  (producer/write-changelog ctx store-name record-id))

(defn process-changelog
  [record-db open-store-fn consumer-id store-name handler]
  (consumer/process-changelog record-db
                              open-store-fn
                              consumer-id
                              store-name
                              handler))

(defn transact [record-db f] (core/transact record-db f))

(defn query-records
  [record-db store store-name record-type field value]
  (core/query-records record-db store store-name record-type field value))

