(ns com.repldriven.mono.message-bus.mqtt
  (:require
    [com.repldriven.mono.message-bus.protocol :as proto]

    [com.repldriven.mono.mqtt.interface :as mqtt]))

(defrecord MqttProducer [client topic qos]
  proto/Producer
    (send [_ message] (mqtt/publish client topic message)))

(defrecord MqttConsumer [client topic qos]
  proto/Consumer
    (subscribe [_ handler-fn] (mqtt/subscribe client {topic qos} handler-fn))
    (unsubscribe [_] (mqtt/unsubscribe client [topic])))

(defn producer
  [{:keys [producer client conf]}]
  (if producer
    (map->MqttProducer producer)
    (let [{:keys [topic qos]} conf] (->MqttProducer client topic (or qos 0)))))

(defn consumer
  [{:keys [consumer client conf]}]
  (if consumer
    (map->MqttConsumer consumer)
    (let [{:keys [topic qos]} conf] (->MqttConsumer client topic (or qos 0)))))

