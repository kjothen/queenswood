(ns com.repldriven.mono.pulsar.env-reader
  (:import (org.apache.pulsar.client.api MessageId Schema)))

(defn message-id
  [_ tag value]
  (case value
    :earliest MessageId/earliest
    :latest MessageId/latest
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                    {:tag tag :value value}))))

(defn schema
  [_ tag value]
  (case value
    ;; primitive types
    :BOOL (Schema/BOOL)
    :BYTEBUFFER (Schema/BYTEBUFFER)
    :BYTES (Schema/BYTES)
    :DATE (Schema/DATE)
    :DOUBLE (Schema/DOUBLE)
    :FLOAT (Schema/FLOAT)
    :INSTANT (Schema/INSTANT)
    :INT16 (Schema/INT16)
    :INT32 (Schema/INT32)
    :INT64 (Schema/INT64)
    :INT8 (Schema/INT8)
    :LOCAL_DATE (Schema/LOCAL_DATE)
    :LOCAL_DATE_TIME (Schema/LOCAL_DATE_TIME)
    :STRING (Schema/STRING)
    :TIME (Schema/TIME)
    :TIMESTAMP (Schema/TIMESTAMP)

    ;; auto typee
    :AUTO_CONSUME (Schema/AUTO_CONSUME)
    :AUTO_PRODUCE_BYTES (Schema/AUTO_PRODUCE_BYTES)
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                    {:tag tag :value value}))))
