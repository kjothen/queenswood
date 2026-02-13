(ns com.repldriven.mono.pulsar.pulsar.message
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.util Optional)
    (org.apache.pulsar.client.api Message)
    (org.apache.pulsar.common.api EncryptionContext)))

(defn encrypted?
  "Returns true if the message is still encrypted (decryption failed)."
  [^Message msg]
  (let [^Optional encryption-ctx (.getEncryptionCtx msg)]
    (and (.isPresent encryption-ctx)
         (.isEncrypted ^EncryptionContext (.get encryption-ctx)))))

(defn deserialize-same
  "Deserializes a message or returns an anomaly if encrypted.
   Used internally by consumer and reader."
  [schema ^Message msg]
  (if (encrypted? msg)
    (error/fail :pulsar/message-decrypt "Message cannot be decrypted")
    (avro/deserialize-same schema (.getData msg))))
