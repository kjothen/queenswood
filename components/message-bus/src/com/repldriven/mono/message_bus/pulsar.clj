(ns com.repldriven.mono.message-bus.pulsar
  (:require
    [com.repldriven.mono.message-bus.protocol :as proto]

    [com.repldriven.mono.pulsar.interface :as pulsar]

    [clojure.core.async :as async]))

(defrecord PulsarProducer [producer]
  proto/Producer
    (send [_ message] (pulsar/send producer message)))

(defrecord PulsarConsumer [consumer schema timeout stop-ch]
  proto/Consumer
    (subscribe [_ handler-fn]
      (let [{:keys [c stop]} (pulsar/receive consumer schema timeout)]
        (reset! stop-ch stop)
        (async/go-loop []
          (when-let [{:keys [message data]} (async/<! c)]
            (handler-fn data)
            (pulsar/acknowledge consumer message)
            (recur)))))
    (unsubscribe [_]
      (when-let [stop @stop-ch]
        (async/put! stop :stop)
        (reset! stop-ch nil))))

(defn producer [opts] (->PulsarProducer (pulsar/producer opts)))

(defn consumer
  [opts]
  (let [{:keys [instance schema]} (pulsar/consumer opts)]
    (map->PulsarConsumer {:consumer instance
                          :schema schema
                          :timeout (get opts :timeout 10000)
                          :stop-ch (atom nil)})))

