(ns com.repldriven.mono.pulsar.pulsar.consumer
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.pulsar.pulsar.message :as message]

    [com.repldriven.mono.log.interface :as log]

    [clojure.core.async :as async]
    [clojure.java.data :as j])
  (:import
    (java.util Map)
    (java.util.concurrent TimeUnit)
    (org.apache.pulsar.client.api Consumer
                                  ConsumerBuilder
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
             ^ConsumerBuilder builder (.. instance (loadConf auto-conf))
             ^ConsumerBuilder builder-with-conf
             (cond-> builder
               (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader))]
         (.subscribe builder-with-conf))
       (catch PulsarClientException e
         (log/error (format "Failed to create pulsar consumer, %s" e)))))

(defn receive
  "Continuously receive messages from a Pulsar consumer and put them on a channel.
   Returns {:c chan :stop chan}. Send anything to :stop to stop receiving."
  [^Consumer consumer schema timeout-ms]
  (let [c (async/chan)
        stop (async/chan)]
    (async/thread
     (try (loop []
            (let [[v port] (async/alts!!
                            [stop
                             (async/thread
                              (when-let [^Message msg
                                         (.. consumer
                                             (receive timeout-ms
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
