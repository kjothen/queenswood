(ns com.repldriven.mono.pulsar.system.components
  (:refer-clojure :exclude [name namespace type])
  (:require
    [com.repldriven.mono.pulsar.pulsar.admin :as admin]
    [com.repldriven.mono.pulsar.pulsar.client :as client]
    [com.repldriven.mono.pulsar.pulsar.consumer :as consumer]
    [com.repldriven.mono.pulsar.pulsar.crypto :as crypto]
    [com.repldriven.mono.pulsar.pulsar.namespaces :as namespaces]
    [com.repldriven.mono.pulsar.pulsar.producer :as producer]
    [com.repldriven.mono.pulsar.pulsar.reader :as reader]
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.pulsar.pulsar.tenants :as tenants]
    [com.repldriven.mono.pulsar.pulsar.topics :as topics]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.log.interface :as log]))

;; ---
;; admin
;; ---

(def admin
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (admin/create config)))
   :system/stop (fn [{:system/keys [instance]}] (admin/close instance))
   :system/config {:service-http-url system/required-component}})

;; ---
;; client
;; ---

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (client/create config)))
   :system/stop (fn [{:system/keys [instance]}] (client/close instance))
   :system/config {:service-url system/required-component}})

;; ---
;; consumer
;; ---

(def consumers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k
                                 (consumer/create
                                  (assoc v :name (clojure.core/name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Pulsar consumer:"
                                            (clojure.core/name k))
                                  (consumer/close v))
                                instance))))
   :system/config system/required-component})

(def consumer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (consumer/create config)))
   :system/stop (fn [{:system/keys [instance]}] (consumer/close instance))
   :system/config {:client system/required-component
                   :conf system/required-component}})

;; ---
;; crypto
;; ---

(def crypto-key-pair-generator
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (crypto/key-pair-generator config)))
   :system/config system/required-component})

;; crypto-key-pair-file-reader(s)
(def crypto-key-pair-file-readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k (crypto/key-pair-file-reader v)]) config))))
   :system/config system/required-component})

(def crypto-key-pair-file-reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (crypto/key-pair-file-reader config)))
   :system/config system/required-component})

;; crypto-key-reader(s)
(def crypto-key-readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {} (map (fn [[k v]] [k (crypto/key-reader v)]) config))))
   :system/config system/required-component})

(def crypto-key-reader
  {:system/start (fn [{:system/keys [config instance]}]
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
;; producer(s)
;; ---

(def producers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k
                                 (producer/create
                                  (assoc v :name (clojure.core/name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Pulsar producer:"
                                            (clojure.core/name k))
                                  (producer/close v))
                                instance))))
   :system/config system/required-component})

(def producer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (producer/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance (producer/close instance)))
   :system/config {:client system/required-component
                   :conf system/required-component
                   :schemas nil}})

;; ---
;; reader
;; ---

(def readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k
                                 (reader/create
                                  (assoc v :name (clojure.core/name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Pulsar reader:"
                                            (clojure.core/name k))
                                  (reader/close v))
                                instance))))
   :system/config system/required-component})

(def reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (reader/create config)))
   :system/stop (fn [{:system/keys [instance]}] (reader/close instance))
   :system/config {:client system/required-component
                   :conf system/required-component
                   :schemas nil}})

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
