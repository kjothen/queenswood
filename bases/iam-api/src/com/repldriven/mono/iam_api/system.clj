(ns com.repldriven.mono.iam-api.system
  (:require [com.repldriven.mono.server.interface :as server]
            [com.repldriven.mono.sql.interface :as sql]))

(defmulti system (fn [k _] k))
(defmethod system :default [_ v] v)
(defmethod system :server [_ v] (server/configure-system v))
(defmethod system :sql [_ v] (sql/configure-system v))

(defn configure-system [m k v] (merge-with into m (system k v)))

(defn configure [config] (reduce-kv configure-system {} config))
