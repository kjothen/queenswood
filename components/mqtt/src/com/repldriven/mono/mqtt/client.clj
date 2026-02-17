(ns com.repldriven.mono.mqtt.client
  (:require
   [com.repldriven.mono.log.interface :as log]
   [clojurewerkz.machine-head.client :as mh]
   [com.repldriven.mono.error.interface :as error]))

(defn publish
  "Publish a message to an MQTT topic. Returns nil on success or an anomaly on failure."
  [client topic payload]
  (log/debugf "mqqt.client/publish [client=%s, topic=%s, payload=%s]" client topic payload)
  (try (mh/publish client topic payload)
       nil
       (catch Exception e
         (error/fail :mqtt/publish-failed
                     (format "Failed to publish to topic %s: %s" topic (.getMessage e))))))

(defn subscribe
  "Subscribe to MQTT topics. Returns nil on success or an anomaly on failure."
  [client topics-and-qos handler-fn]
  (log/debugf "mqqt.client/subscribe [client=%s, topic-and-qos=%s, handler-fn=%s]" client topics-and-qos handler-fn)
  (try (mh/subscribe client topics-and-qos handler-fn)
       nil
       (catch Exception e
         (error/fail :mqtt/subscribe-failed
                     (format "Failed to subscribe to topics %s: %s" topics-and-qos (.getMessage e))))))

(defn unsubscribe
  "Unsubscribe from MQTT topics. Returns nil on success or an anomaly on failure."
  [client topics]
  (log/debugf "mqqt.client/usubscribe [client=%s, topics=%s]" client topics)
  (try (mh/unsubscribe client topics)
       nil
       (catch Exception e
         (error/fail :mqtt/unsubscribe-failed
                     (format "Failed to unsubscribe from topics %s: %s" topics (.getMessage e))))))
