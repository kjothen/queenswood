(ns com.repldriven.mono.fdb.interface
  (:refer-clojure :exclude [get set])
  (:require
    com.repldriven.mono.fdb.system

    [com.repldriven.mono.fdb.core :as core]))

(defn set [db key value] (core/set db key value))

(defn get [db key] (core/get db key))
