(ns com.repldriven.mono.pulsar.pulsar.producer
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]

    [com.repldriven.mono.log.interface :as log]

    [clojure.java.data :as j])
  (:import
    (java.util Map)
    (org.apache.pulsar.client.api Producer
                                  PulsarClient
                                  PulsarClientException)))

(defn- add-encryption-keys
  [producer ks]
  (reduce #(.addEncryptionKey %1 %2) producer ks))

(defn create
  ^Producer [{:keys [^PulsarClient client conf schemas]}]
  (try
    (log/info "Creating pulsar producer" conf)
    (let [{:keys [cryptoKeyReader encryptionKeys schema]} conf
          manual-conf [:cryptoKeyReader :encryptionKeys :schema]
          conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
          auto-conf
          (j/to-java Map (apply dissoc conf-str-keys (map name manual-conf)))
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
