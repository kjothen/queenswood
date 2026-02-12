(ns com.repldriven.mono.pulsar.pulsar.consumer
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]

    [com.repldriven.mono.log.interface :as log]

    [clojure.java.data :as j])
  (:import
    (java.util Map)
    (java.util.concurrent TimeUnit)
    (org.apache.pulsar.client.api Consumer
                                  Message
                                  PulsarClient
                                  PulsarClientException)))

(defn create
  ^Consumer [{:keys [^PulsarClient client conf schemas]}]
  (try (log/info "Creating pulsar consumer" conf schemas)
       (let [{:keys [cryptoKeyReader schema]} conf
             manual-conf [:cryptoKeyReader :schema]
             conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
             auto-conf
             (j/to-java Map (apply dissoc conf-str-keys (map name manual-conf)))
             instance (if (some? schema)
                        (.. client
                            (newConsumer (schemas/resolve schemas schema)))
                        (.. client newConsumer))
             builder (.. instance (loadConf auto-conf))]
         (.subscribe (cond-> builder
                       (some? cryptoKeyReader) (.cryptoKeyReader
                                                cryptoKeyReader))))
       (catch PulsarClientException e
         (log/error (format "Failed to create pulsar consumer, %s" e)))))

(defn receive
  ^Message ([^Consumer consumer] (when consumer (.. consumer receive)))
  ([^Consumer consumer timeout-ms]
   (when consumer (.. consumer (receive timeout-ms TimeUnit/MILLISECONDS)))))
