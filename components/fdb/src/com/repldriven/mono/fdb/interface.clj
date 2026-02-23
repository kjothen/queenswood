(ns com.repldriven.mono.fdb.interface
  (:require
    com.repldriven.mono.fdb.system

    [com.repldriven.mono.fdb.core :as core]))

(defn set-str [db key value] (core/set-str db key value))

(defn get-str [db key] (core/get-str db key))

(defn set-bytes [db key value] (core/set-bytes db key value))

(defn get-bytes [db key] (core/get-bytes db key))

(defn load-record
  [record-db store-name primary-key]
  (core/load-record record-db store-name primary-key))

(defn save-record
  [record-db store-name record]
  (core/save-record record-db store-name record))

(defn outbox-record
  [record-db store-name record event-bytes]
  (core/outbox-record record-db store-name record event-bytes))
