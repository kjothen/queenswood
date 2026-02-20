(ns com.repldriven.mono.fdb.fdb.core
  (:refer-clojure :exclude [get set])
  (:require
    [com.repldriven.mono.fdb.fdb.client :as client]))

(defn set [db key value] (client/set db key value))

(defn get [db key] (client/get db key))
