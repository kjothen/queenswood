(ns com.repldriven.mono.pubsub.pulsar.env-reader
  (:import (org.apache.pulsar.client.api ConsumerCryptoFailureAction
             MessageId Schema SubscriptionType)))

(defn crypto-failure-action
  [_ tag value]
  (case value
    :CONSUME ConsumerCryptoFailureAction/CONSUME
    :DISCARD ConsumerCryptoFailureAction/DISCARD
    :FAIL ConsumerCryptoFailureAction/FAIL
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
             {:tag tag :value value}))))

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
    ;; primitive
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

    ;; auto
    :AUTO_CONSUME (Schema/AUTO_CONSUME)
    :AUTO_PRODUCE_BYTES (Schema/AUTO_PRODUCE_BYTES)
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
             {:tag tag :value value}))))

(defn subscription-type
  [_ tag value]
  (case value
    :Exclusive SubscriptionType/Exclusive
    :Failover SubscriptionType/Failover
    :Key_Shared SubscriptionType/Key_Shared
    :Shared SubscriptionType/Shared
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
             {:tag tag :value value}))))
