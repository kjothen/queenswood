(ns com.repldriven.mono.pulsar.system.components
  (:require [clojure.java.data :as j]
            [clojure.java.data.builder :as builder]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.pulsar.admin :as admin]
            [com.repldriven.mono.pulsar.crypto :as crypto])
  (:import [java.util Map]
           [org.apache.pulsar.client.api
            ClientBuilder Consumer MessageId
            PulsarClient PulsarClientException
            Producer Reader Schema]
           [org.apache.pulsar.client.admin
            PulsarAdmin PulsarAdminBuilder
            PulsarAdminException]))
(def admin
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [service-http-url]} config]
           (try (log/info "Opening pulsar admin connection:" service-http-url)
                (builder/to-java PulsarAdmin
                  (PulsarAdmin/builder)
                  {:serviceHttpUrl service-http-url}
                  {:builder-class PulsarAdminBuilder})
                (catch PulsarAdminException e
                  (log/error (format "Failed to open pulsar admin connection, %s" e))))))),

   :system/post-start
   (fn [_] (log/info "After starting pulsar admin connection") (Thread/sleep 5000))
   :system/stop
   (fn [{:system/keys [^PulsarAdmin instance]}]
     (when (some? instance)
       (try
         (log/info "Closing pulsar admin connection")
         (.close instance)
         (catch PulsarAdminException e
           (log/error (format "Failed to close pulsar admin connection, %s" e)))))),

   :system/config
   {:service-http-url system/required-component}})

(def client
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [service-url]} config]
           (try
             (log/info "Opening pulsar client connection:" service-url)
             (builder/to-java PulsarClient
               (PulsarClient/builder)
               {:serviceUrl service-url}
               {:builder-class ClientBuilder})
             (catch PulsarClientException e
               (log/error (format "Failed to open pulsar client connection, %s" e))))))),

   :system/stop
   (fn [{:system/keys [^PulsarClient instance]}]
     (when (some? instance)
       (try
         (log/info "Closing pulsar client connection")
         (.close instance)
         (catch PulsarClientException e
           (log/error (format "Failed to close pulsar client connection, %s" e)))))),

   :system/config
   {:service-url system/required-component}})

(def consumer
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [^PulsarClient client conf]} config]
           (try
             (log/info "Opening pulsar consumer")
             (let [{:strs [cryptoKeyReader schema subscriptionName topics]}
                   conf]
               (cond-> (.. client (newConsumer schema))
                 (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
                 (some? subscriptionName) (.subscriptionName subscriptionName)
                 (some? topics) (.topics topics)
                 true (.subscribe)))
             (catch PulsarClientException e
               (log/error (format "Failed to open pulsar consumer, %s" e)))))))

   :system/stop
   (fn [{:system/keys [^Consumer instance]}]
     (when (some? instance)
       (try
         (log/info "Closing pulsar consumer")
         (.close instance)
         (catch PulsarClientException e
           (log/error (format "Failed to close pulsar consumer, %s" e)))))),

   :system/config
   {:client system/required-component,
    :conf system/required-component}})

(def crypto-key-pair-generator
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-pair-generator: " config)
           (crypto/key-pair-generator config))))

   :system/config system/required-component})

(def crypto-key-pair-file-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-pair-file-reader: " config)
           (crypto/key-pair-file-reader config))))

   :system/config system/required-component})

(def crypto-key-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-reader: " config)
           (crypto/key-reader config))))

   :system/config system/required-component})

(defn- add-encryption-keys
  [producer ks]
  (reduce #(.addEncryptionKey %1 %2) producer ks))

(def producer
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [^PulsarClient client conf]} config]
           (try
             (log/info "Opening pulsar producer")
             (let [{:strs [cryptoKeyReader encryptionKeys schema topic]} conf]
               (cond-> (.. client (newProducer schema))
                 (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
                 (some? encryptionKeys) (add-encryption-keys encryptionKeys)
                 (some? topic) (.topic topic)
                 true (.create)))
             (catch PulsarClientException e
               (log/error (format "Failed to open pulsar producer, %s" e))))))),

   :system/stop
   (fn [{:system/keys [^Producer instance]}]
     (when (some? instance)
       (try
         (log/info "Closing pulsar producer")
         (.close instance)
         (catch PulsarClientException e
           (log/error (format "Failed to close pulsar producer, %s" e)))))),

   :system/config
   {:client system/required-component,
    :conf system/required-component}})

(def reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [^PulsarClient client conf]} config]
           (try
             (log/info "Opening pulsar reader")
             (let [{:strs [cryptoKeyReader schema startMessageId topics]
                    :or {startMessageId MessageId/latest}} conf]
               (cond-> (.. client (newReader schema)
                           (startMessageId startMessageId))
                 (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
                 (some? topics) (.topics topics)
                 true (.create)))
             (catch PulsarClientException e
               (log/error (format "Failed to open pulsar reader, %s" e))))))),

   :system/stop
   (fn [{:system/keys [^Reader instance]}]
     (when (some? instance)
       (try
         (log/info "Closing pulsar reader")
         (.close instance)
         (catch PulsarClientException e
           (log/error (format "Failed to close pulsar reader, %s" e)))))),

   :system/config
   {:client system/required-component,
    :conf system/required-component}})

(def topics
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [admin topics]} config]
           (try
             (log/info "Creating pulsar topics:" topics)
             (doall
              (mapv (fn [{:keys [topic schema opts]}]
                      (admin/ensure-topic admin topic opts)
                      (when (some? schema)
                        (admin/ensure-schema admin topic schema)))
                    topics))
             (catch PulsarAdminException e
               (log/error (format "Failed to create pulsar topics, %s" e))))))),

   :system/config
   {:admin system/required-component
    :topics system/required-component}})
