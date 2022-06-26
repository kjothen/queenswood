(ns com.repldriven.mono.pulsar.components
  (:require [clojure.java.data :as j]
            [clojure.java.data.builder :as builder]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.pulsar.admin :as admin])
  (:import [java.util Map]
           [org.apache.pulsar.client.api ClientBuilder Consumer MessageId PulsarClient PulsarClientException Reader]
           [org.apache.pulsar.client.admin PulsarAdmin PulsarAdminBuilder PulsarAdminException]))

(def admin
  {:start (fn [{:keys [service-http-url]} instance _]
            (or instance
                (try
                  (log/info "Opening pulsar admin connection:" service-http-url)
                  (builder/to-java PulsarAdmin
                                   (PulsarAdmin/builder)
                                   {:serviceHttpUrl service-http-url}
                                   {:builder-class PulsarAdminBuilder})
                  (catch PulsarAdminException e
                    (log/error (format "Failed to open pulsar admin connection, %s" e)))))),
   :stop (fn [_ ^PulsarAdmin instance _]
           (when (some? instance)
             (try
               (log/info "Closing pulsar admin connection")
               (.close instance)
               (catch PulsarAdminException e
                 (log/error (format "Failed to close pulsar admin connection, %s" e)))))),
   :conf {:service-http-url system/required-component}})

(def client
  {:start (fn [{:keys [service-url]} instance _]
            (or instance
              (try
                (log/info "Opening pulsar client connection:" service-url)
                (builder/to-java PulsarClient
                  (PulsarClient/builder)
                  {:serviceUrl service-url}
                  {:builder-class ClientBuilder})
                (catch PulsarClientException e
                  (log/error (format "Failed to open pulsar client connection, %s" e)))))),
   :stop (fn [_ ^PulsarClient instance _]
           (when (some? instance)
             (try
               (log/info "Closing pulsar client connection")
               (.close instance)
               (catch PulsarClientException e
                 (log/error (format "Failed to close pulsar client connection, %s" e)))))),
   :conf {:service-url system/required-component}})

(def topic-creator
  {:start (fn [{:keys [admin topics-and-opts]} instance _]
            (or instance
                (try
                  (log/info "Creating pulsar topics:" topics-and-opts)
                  (run! (fn [[topic opts]] (admin/ensure-topic topic opts)) topics-and-opts)
                  (catch PulsarAdminException e
                    (log/error (format "Failed to create pulsar topics, %s" e)))))),
   :stop (fn [_ _ _]),
   :config {:admin system/required-component
           :topics-and-opts system/required-component}})

(def reader
  {:start (fn [{:keys [^PulsarClient client config]} instance _]
            (or instance
              (do
                (log/info "Opening pulsar reader")
                (try
                  (some-> client
                    (.newReader)
                    (.startMessageId (get config "startMessageId" MessageId/latest))
                    (.loadConf (j/to-java Map config))
                    (.create))
                  (catch PulsarClientException e
                    (log/error (format "Failed to open pulsar reader, %s" e))))))),
   :stop (fn [_ ^Reader instance _]
           (when (some? instance)
             (try
               (log/info "Closing pulsar reader")
               (.close instance)
               (catch PulsarClientException e
                 (log/error (format "Failed to close pulsar reader, %s" e)))))),
   :conf {:client system/required-component,
          :config system/required-component}})

(def consumer
  {:start (fn [{:keys [^PulsarClient client config]} instance _]
            (or instance
              (try
                (log/info "Opening pulsar consumer")
                (some-> client
                  (.newConsumer)
                  (.loadConf (j/to-java Map config))
                  (.subscribe))
                (catch PulsarClientException e
                  (log/error (format "Failed to open pulsar consumer, %s" e)))))),
   :stop (fn [_ ^Consumer instance _]
           (when (some? instance)
             (try
               (log/info "Closing pulsar consumer")
               (.close instance)
               (catch PulsarClientException e
                 (log/error (format "Failed to close pulsar consumer, %s" e)))))),
   :conf {:client system/required-component, :config system/required-component}})
