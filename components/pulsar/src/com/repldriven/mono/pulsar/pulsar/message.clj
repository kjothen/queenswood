(ns com.repldriven.mono.pulsar.pulsar.message
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.util Optional)
    (org.apache.pulsar.client.api Message)
    (org.apache.pulsar.client.api.schema GenericRecord)
    (org.apache.pulsar.common.api EncryptionContext)))

(defn encrypted?
  "Returns true if the message is still encrypted (decryption failed)."
  [^Message msg]
  (let [^Optional encryption-ctx (.getEncryptionCtx msg)]
    (and (.isPresent encryption-ctx)
         (.isEncrypted ^EncryptionContext (.get encryption-ctx)))))

(defn- generic-record->map
  "Convert a Pulsar GenericRecord to a Clojure map."
  [^GenericRecord record]
  (when record
    (let [fields (.getFields record)]
      (into {}
            (map (fn [field]
                   (let [field-name (.getName field)
                         value (.getField record field-name)]
                     [(keyword field-name) value]))
                 fields)))))

(defn deserialize-same
  "Deserializes a message or returns an anomaly if encrypted.
   For schema-based messages (AUTO_CONSUME), uses Pulsar's automatic deserialization.
   Falls back to manual Lancaster Avro deserialization for legacy messages.
   Used internally by consumer and reader."
  [schema ^Message msg]
  (if (encrypted? msg)
    (error/fail :pulsar/message-decrypt "Message cannot be decrypted")
    (let [value (.getValue msg)]
      (if (instance? GenericRecord value)
        (generic-record->map value)
        (avro/deserialize-same schema (.getData msg))))))
