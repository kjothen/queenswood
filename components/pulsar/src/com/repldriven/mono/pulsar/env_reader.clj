(ns com.repldriven.mono.pulsar.env-reader
  (:import (org.apache.pulsar.client.api MessageId)))

(defn message-id
  [_ tag value]
  (case value
    :earliest MessageId/earliest
    :latest MessageId/latest
    (throw (ex-info (format "Invalid value %s for tag %s" value tag) {:tag tag :value value}))))
