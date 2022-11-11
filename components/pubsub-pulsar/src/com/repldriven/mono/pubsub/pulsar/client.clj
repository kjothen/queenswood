(ns com.repldriven.mono.pubsub.pulsar.client
  (:require [clojure.java.data.builder :as builder]
            [com.repldriven.mono.log.interface :as log])
  (:import (org.apache.pulsar.client.api ClientBuilder
                                         PulsarClient
                                         PulsarClientException)))

(defn ^PulsarClient create
  [{:keys [service-url]}]
  (try (log/info "Opening pulsar client connection:" service-url)
       (builder/to-java PulsarClient
                        (PulsarClient/builder)
                        {:serviceUrl service-url}
                        {:builder-class ClientBuilder})
       (catch PulsarClientException e
         (log/error (format "Failed to open pulsar client connection, %s" e)))))
