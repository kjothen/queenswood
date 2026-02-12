(ns com.repldriven.mono.pulsar.pulsar.admin
  (:require
    [com.repldriven.mono.log.interface :as log]

    [clojure.java.data.builder :as builder])
  (:import
    (org.apache.pulsar.client.admin PulsarAdmin
                                    PulsarAdminBuilder
                                    PulsarAdminException)))

(defn create
  ^PulsarAdmin [{:keys [service-http-url]}]
  (try (log/info "Opening pulsar admin connection: " service-http-url)
       (builder/to-java PulsarAdmin
                        (PulsarAdmin/builder)
                        {:serviceHttpUrl service-http-url}
                        {:builder-class PulsarAdminBuilder})
       (catch PulsarAdminException e
         (log/error (format "Failed to open pulsar admin connection, %s" e)))))
