(ns com.repldriven.mono.pubsub.system.components
  (:refer-clojure :exclude [name namespace type])
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.pubsub.pulsar.admin :as admin]
            [com.repldriven.mono.pubsub.pulsar.client :as client]
            [com.repldriven.mono.pubsub.pulsar.crypto :as crypto]
            [com.repldriven.mono.pubsub.pulsar.consumer :as consumer]
            [com.repldriven.mono.pubsub.pulsar.namespaces :as namespaces]
            [com.repldriven.mono.pubsub.pulsar.producer :as producer]
            [com.repldriven.mono.pubsub.pulsar.reader :as reader]
            [com.repldriven.mono.pubsub.pulsar.schemas :as schemas]
            [com.repldriven.mono.pubsub.pulsar.tenants :as tenants]
            [com.repldriven.mono.pubsub.pulsar.topics :as topics]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.apache.pulsar.client.api PulsarClientException)
           (org.apache.pulsar.client.admin PulsarAdminException)))

(defn- close-admin-connection
  [instance n]
  (try
    (log/info "Closing pulsar %s connection" n)
    (.close instance)
    (catch PulsarAdminException e
      (log/error (format "Failed to close pulsar %s connection, %s" n e)))))

(defn- close-client-connection
  [instance n]
  (try
    (log/info "Closing pulsar %s connection" n)
    (.close instance)
    (catch PulsarClientException e
      (log/error (format "Failed to close pulsar %s connection, %s" n e)))))

(def named-component
  {:system/start (fn [{:system/keys [config]}] config)
   :system/config system/required-component})

;; ---
;; admin
;; ---

(def admin
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (admin/create config)))
   :system/post-start (fn [_] (Thread/sleep 5000))
   :system/stop (fn [{:system/keys [instance]}]
                  (close-admin-connection instance "admin"))
   :system/config {:service-http-url system/required-component}})

;; ---
;; client
;; ---

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (client/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (close-client-connection instance "client"))
   :system/config {:service-url system/required-component}})

;; ---
;; consumer
;; ---

(def consumers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (reduce-kv
                   (fn [m k v]
                     (assoc m k (consumer/create v)))
                   {} config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun
                     (map (fn [[_ instance]]
                            (close-client-connection instance "consumer"))
                          instance))))
   :system/config system/required-component})

(def consumer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (consumer/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (close-client-connection instance "consumer"))
   :system/config {:client system/required-component
                   :conf system/required-component}})

;; ---
;; crypto
;; ---

(def crypto-key-pair-generator
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-pair-generator: " config)
           (crypto/key-pair-generator config))))
   :system/config system/required-component})

; crypto-key-pair-file-reader(s)
(def crypto-key-pair-file-readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (reduce-kv
          (fn [m k v]
              (assoc m k (crypto/key-pair-file-reader v)))
          {} config)))
   :system/config system/required-component})

(def crypto-key-pair-file-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (crypto/key-pair-file-reader config)))
   :system/config system/required-component})

; crypto-key-reader(s)
(def crypto-key-readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (reduce-kv
          (fn [m k v]
              (assoc m k (crypto/key-reader v)))
          {} config)))
   :system/config system/required-component})

(def crypto-key-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (crypto/key-reader config)))
   :system/config system/required-component})

;; ---
;; namespaces
;; ---

(def namespaces
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (namespaces/create-namespaces config)))
   :system/config system/required-component})

;; ---
;; producer
;; ---

(def producer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (producer/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (close-client-connection instance "producer"))
   :system/config {:client system/required-component
                   :conf system/required-component
                   :schemas nil}})

;; ---
;; reader
;; ---

(def reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (reader/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (close-client-connection instance "reader"))
   :system/config {:client system/required-component
                   :conf system/required-component}})

;; ---
;; schemas
;; ---

(def schemas
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (schemas/create-schemas config)))
   :system/config system/required-component})

;; ---
;; tenants
;; ---

(def tenants
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (tenants/create-tenants config)))
   :system/config system/required-component})


;; ---
;; topics
;; ---

(def topics
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (topics/create-topics config)))
   :system/config system/required-component})
