(ns com.repldriven.mono.pubsub.pulsar.admin
  (:require [clojure.java.data.builder :as builder]
            [com.repldriven.mono.log.interface :as log])
  (:import (org.apache.pulsar.client.admin
             PulsarAdmin PulsarAdminBuilder PulsarAdminException)))

(defn ^PulsarAdmin create
  [{:keys [service-http-url]}]
  (try
    (log/info "Opening pulsar admin connection: " service-http-url)
    (builder/to-java PulsarAdmin
                     (PulsarAdmin/builder)
                     {:serviceHttpUrl service-http-url}
                     {:builder-class PulsarAdminBuilder})
    (catch PulsarAdminException e
      (log/error (format "Failed to open pulsar admin connection, %s" e)))))
