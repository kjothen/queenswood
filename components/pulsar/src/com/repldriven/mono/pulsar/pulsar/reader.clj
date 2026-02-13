(ns com.repldriven.mono.pulsar.pulsar.reader
  (:refer-clojure :exclude [read])
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.pulsar.pulsar.message :as message]

    [com.repldriven.mono.log.interface :as log]

    [clojure.core.async :as async]
    [clojure.java.data :as j])
  (:import
    (org.apache.pulsar.client.api Message
                                  PulsarClient
                                  PulsarClientException
                                  Reader
                                  ReaderBuilder)
    (java.util Map)
    (java.util.concurrent TimeUnit)))

(defn create
  ^Reader [{:keys [^PulsarClient client conf schemas]}]
  (try (log/info "Opening pulsar reader")
       (let [{:keys [cryptoKeyReader schema startMessageId]} conf
             manual-conf [:cryptoKeyReader :schema :startMessageId]
             conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
             auto-conf
             (j/to-java Map (apply dissoc conf-str-keys (map name manual-conf)))
             instance (if (some? schema)
                        (.. client (newReader (schemas/resolve schemas schema)))
                        (.. client newReader))
             ^ReaderBuilder builder (.. instance (loadConf auto-conf))
             ^ReaderBuilder builder-with-conf
             (cond-> builder
               (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
               (some? startMessageId) (.startMessageId startMessageId))]
         (.create builder-with-conf))
       (catch PulsarClientException e
         (log/error (format "Failed to open pulsar reader, %s" e)))))

(defn read
  "Continuously read messages from a Pulsar reader and put them on a channel.
  Returns {:c chan :stop chan}. Send anything to :stop to stop reading."
  [^Reader reader schema timeout-ms]
  (let [c (async/chan)
        stop (async/chan)]
    (async/thread
     (try (loop []
            (let [[v port] (async/alts!!
                            [stop
                             (async/thread
                              (when-let [^Message msg
                                         (.. reader
                                             (readNext timeout-ms
                                                       TimeUnit/MILLISECONDS))]
                                msg))])]
              (cond (= port stop) nil
                    (some? v) (do (async/>!!
                                   c
                                   {:message v
                                    :data (message/deserialize-same schema v)})
                                  (recur))
                    :else (recur))))
          (finally (async/close! c) (async/close! stop))))
    {:c c :stop stop}))
