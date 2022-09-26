(ns com.repldriven.mono.pulsar.system.components
  (:require [clojure.java.data :as j]
            [clojure.java.data.builder :as builder]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.pulsar.admin :as admin])
  (:import [java.util Map]
           [org.apache.pulsar.client.api ClientBuilder Consumer MessageId PulsarClient PulsarClientException Reader]
           [org.apache.pulsar.client.admin PulsarAdmin PulsarAdminBuilder PulsarAdminException]))

(def admin
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [service-http-url]} config]
                         (try (log/info "Opening pulsar admin connection:" service-http-url)
                              (builder/to-java PulsarAdmin
                                (PulsarAdmin/builder)
                                {:serviceHttpUrl service-http-url}
                                {:builder-class PulsarAdminBuilder})
                              (catch PulsarAdminException e
                                (log/error (format "Failed to open pulsar admin connection, %s" e))))))),
   :system/post-start (fn [_] (log/info "After starting pulsar admin connection") (Thread/sleep 5000))
   :system/stop (fn [{:system/keys [^PulsarAdmin instance]}]
                  (when (some? instance)
                    (try
                      (log/info "Closing pulsar admin connection")
                      (.close instance)
                      (catch PulsarAdminException e
                        (log/error (format "Failed to close pulsar admin connection, %s" e)))))),
   :system/config {:service-http-url system/required-component}})

(def client
  {:system/start (fn [{:system/keys [config instance]}]
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
   :system/stop (fn [{:system/keys [^PulsarClient instance]}]
                  (when (some? instance)
                    (try
                      (log/info "Closing pulsar client connection")
                      (.close instance)
                      (catch PulsarClientException e
                        (log/error (format "Failed to close pulsar client connection, %s" e)))))),
   :system/config {:service-url system/required-component}})

(def topics
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [admin topics-and-opts]} config]
                         (try
                           (log/info "Creating pulsar topics:" topics-and-opts)
                           (doall (mapv (fn [{:keys [topic opts]}] (admin/ensure-topic admin topic opts)) topics-and-opts))
                           (catch PulsarAdminException e
                             (log/error (format "Failed to create pulsar topics, %s" e))))))),
   :system/config {:admin system/required-component
                   :topics-and-opts system/required-component}})

(def reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [^PulsarClient client conf]} config]
                         (try
                           (log/info "Opening pulsar reader")
                           (.. client
                               (newReader)
                               (startMessageId (get config "startMessageId" MessageId/latest))
                               (loadConf (j/to-java Map conf))
                               (create))
                           (catch PulsarClientException e
                             (log/error (format "Failed to open pulsar reader, %s" e))))))),
   :system/stop (fn [{:system/keys [^Reader instance]}]
                  (when (some? instance)
                    (try
                      (log/info "Closing pulsar reader")
                      (.close instance)
                      (catch PulsarClientException e
                        (log/error (format "Failed to close pulsar reader, %s" e)))))),
   :system/config {:client system/required-component,
                   :conf system/required-component}})

(def consumer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:system/keys [^PulsarClient client conf]} config]
                         (try
                           (log/info "Opening pulsar consumer")
                           (.. client
                               (newConsumer)
                               (loadConf (j/to-java Map conf))
                               (subscribe))
                           (catch PulsarClientException e
                             (log/error (format "Failed to open pulsar consumer, %s" e))))))),
   :system/stop (fn [{:system/keys [^Consumer instance]}]
                  (when (some? instance)
                    (try
                      (log/info "Closing pulsar consumer")
                      (.close instance)
                      (catch PulsarClientException e
                        (log/error (format "Failed to close pulsar consumer, %s" e)))))),
   :system/config {:client system/required-component,
                   :conf system/required-component}})
