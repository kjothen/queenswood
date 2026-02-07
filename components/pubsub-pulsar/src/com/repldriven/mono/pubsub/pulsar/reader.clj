(ns com.repldriven.mono.pubsub.pulsar.reader
  (:require [clojure.java.data :as j]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.pubsub.pulsar.schemas :as schemas])
  (:import [java.util Map]
           [org.apache.pulsar.client.api PulsarClient PulsarClientException
            Reader]))

(defn create ^Reader
  [{:keys [^PulsarClient client conf schemas]}]
  (try (log/info "Opening pulsar reader")
       (let [{:strs [cryptoKeyReader schema startMessageId]} conf
             manual-conf ["cryptoKeyReader" "schema" "startMessageId"]
             auto-conf (j/to-java Map (apply dissoc conf manual-conf))
             instance (if (some? schema)
                        (.. client (newReader (schemas/resolve schemas schema)))
                        (.. client newReader))
             builder (.. instance (loadConf auto-conf))]
         (.create (cond-> builder
                    (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
                    (some? startMessageId) (.startMessageId startMessageId))))
       (catch PulsarClientException e
         (log/error (format "Failed to open pulsar reader, %s" e)))))
