(ns com.repldriven.mono.fdb.interface
  (:refer-clojure :exclude [get set])
  (:require
    com.repldriven.mono.fdb.system

    [com.repldriven.mono.fdb.core :as core]))

(defn set [db key value] (core/set db key value))

(defn get [db key] (core/get db key))

(defn set-bytes [db key value] (core/set-bytes db key value))

(defn get-bytes [db key] (core/get-bytes db key))

(defn load-record
  [record-db store-name primary-key]
  (core/load-record record-db store-name primary-key))

(defn save-record!
  [record-db store-name record event-bytes]
  (core/save-record! record-db store-name record event-bytes))
