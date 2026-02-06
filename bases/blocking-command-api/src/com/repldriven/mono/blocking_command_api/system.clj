(ns com.repldriven.mono.blocking-command-api.system
  (:require [com.repldriven.mono.server.interface :as server]
            [com.repldriven.mono.mqtt.interface :as mqtt]
            [com.repldriven.mono.pubsub.interface :as pubsub]))

(defmulti system (fn [k _] k))
(defmethod system :default [k v] {:system/defs {k v}})
(defmethod system :server [_ v] (server/configure-system v))
(defmethod system :pubsub [_ v] (pubsub/configure-system v))
(defmethod system :mqtt [_ v] (mqtt/configure-system v))

(defn configure-system [m k v] (merge-with into m (system k v)))

(defn configure [config] (reduce-kv configure-system {} config))
