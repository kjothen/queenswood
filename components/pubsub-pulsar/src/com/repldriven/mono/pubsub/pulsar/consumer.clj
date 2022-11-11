(ns com.repldriven.mono.pubsub.pulsar.consumer
  (:require [clojure.java.data :as j]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.pubsub.pulsar.schemas :as schemas])
  (:import (java.util Map)
           (java.util.concurrent TimeUnit)
           (org.apache.pulsar.client.api
             Consumer Message PulsarClient PulsarClientException)))

(defn ^Consumer create
  [{:keys [^PulsarClient client conf schemas]}]
  (try
    (log/info "Creating pulsar consumer")
    (let [{:strs [cryptoKeyReader schema]} conf
          manual-conf ["cryptoKeyReader" "schema"]
          auto-conf (j/to-java Map (apply dissoc conf manual-conf))
          instance (if (some? schema)
                     (.. client (newConsumer (schemas/resolve schemas schema)))
                     (.. client newConsumer))
          builder (.. instance (loadConf auto-conf))]
      (.subscribe (cond-> builder
                    (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader))))
    (catch PulsarClientException e
      (log/error (format "Failed to create pulsar consumer, %s" e)))))

(defn ^Message receive
  ([^Consumer consumer]
   (.. consumer receive))
  ([^Consumer consumer timeout-ms]
   (.. consumer (receive timeout-ms TimeUnit/MILLISECONDS))))
