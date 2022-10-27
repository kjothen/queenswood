(ns com.repldriven.mono.pulsar.env-reader
  (:import (org.apache.pulsar.client.api MessageId Schema)))

(defn message-id
  [_ tag value]
  (case value
    :earliest MessageId/earliest
    :latest MessageId/latest
    (throw (ex-info (format "Invalid value %s for tag %s" value tag) {:tag tag :value value}))))

(defn schema
  [_ tag value]
  (case value
    :auto-produce-bytes (Schema/AUTO_PRODUCE_BYTES)
    :auto-consume (Schema/AUTO_CONSUME)
    :string Schema/STRING
    (throw (ex-info (format "Invalid value %s for tag %s" value tag) {:tag tag :value value}))))
