(ns com.repldriven.mono.message-bus.core
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.message-bus.protocol :as proto]
    [com.repldriven.mono.message-bus.pulsar :as pulsar]
    [com.repldriven.mono.message-bus.mqtt :as mqtt]))

(defn producer
  [{:keys [kind] :as opts}]
  (case (keyword kind)
    :pulsar (pulsar/producer opts)
    :mqtt (mqtt/producer opts)))

(defn consumer
  [{:keys [kind] :as opts}]
  (case (keyword kind)
    :pulsar (pulsar/consumer opts)
    :mqtt (mqtt/consumer opts)))

(defrecord Bus [producers consumers])

(defn send
  [bus producer-name message]
  (proto/send (get (:producers bus) producer-name) message))

(defn subscribe
  [bus consumer-name handler-fn]
  (proto/subscribe (get (:consumers bus) consumer-name) handler-fn))

(defn unsubscribe
  [bus consumer-name]
  (proto/unsubscribe (get (:consumers bus) consumer-name)))
