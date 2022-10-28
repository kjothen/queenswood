(ns com.repldriven.mono.pulsar.interface
  (:require [com.repldriven.mono.pulsar.admin :as admin]
            [com.repldriven.mono.pulsar.client :as client]
            [com.repldriven.mono.pulsar.crypto]
            [com.repldriven.mono.pulsar.env-reader :as env-reader]
            [com.repldriven.mono.pulsar.system.core :as system]
            [com.repldriven.mono.env.interface :as env]))

(defmethod env/reader 'pulsar-message-id
  [opts tag value]
  (env-reader/message-id opts tag value))

(defmethod env/reader 'pulsar-schema
  [opts tag value]
  (env-reader/schema opts tag value))

(defn configure-system
  [config]
  (system/configure config))

(defn ensure-tenant
  [admin tenant-name & opts]
  (if (some? opts)
    (admin/ensure-tenant admin tenant-name opts)
    (admin/ensure-tenant admin tenant-name)))

(defn ensure-namespace
  [admin namespace-name & opts]
  (if (some? opts)
    (admin/ensure-namespace admin namespace-name opts)
    (admin/ensure-namespace admin namespace-name)))

(defn ensure-topic
  [admin topic-name & opts]
  (if (some? opts)
    (admin/ensure-topic admin topic-name opts)
    (admin/ensure-topic admin topic-name)))

(defn publish
  [client topic-name message]
  (client/publish client topic-name message))

(defn subscribe
  [client topic-name f]
  (client/subscribe client topic-name f))
