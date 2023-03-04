(ns com.repldriven.mono.pubsub.pulsar.producer
  (:refer-clojure :exclude [send])
  (:require [clojure.java.data :as j]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.pubsub.pulsar.schemas :as schemas])
  (:import [java.util Map]
           [org.apache.pulsar.client.api Producer PulsarClient
            PulsarClientException]))

(defn- add-encryption-keys
  [producer ks]
  (reduce #(.addEncryptionKey %1 %2) producer ks))

(defn ^Producer create
  [{:keys [^PulsarClient client conf schemas]}]
  (try
    (log/info "Creating pulsar producer" conf)
    (let [{:strs [cryptoKeyReader encryptionKeys schema]} conf
          manual-conf ["cryptoKeyReader" "encryptionKeys" "schema"]
          auto-conf (j/to-java Map (apply dissoc conf manual-conf))
          instance (if (some? schema)
                     (.. client (newProducer (schemas/resolve schemas schema)))
                     (.. client newProducer))
          builder (.. instance (loadConf auto-conf))]
      (.create (cond-> builder
                 (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
                 (some? encryptionKeys) (add-encryption-keys encryptionKeys))))
    (catch PulsarClientException e
      (log/error (format "Failed to create pulsar producer, %s" e)))))

(defn send
  ([^Producer producer data] (.. producer (send data)))
  ([^Producer producer data opts]
   (.. producer newMessage (loadConf opts) (value data) send)))

(defn send-async
  ([^Producer producer data] (.. producer (sendAsync data)))
  ([^Producer producer data opts]
   (.. producer newMessage (loadConf opts) (value data) sendAsync)))
