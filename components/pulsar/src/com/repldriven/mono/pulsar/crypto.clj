(ns com.repldriven.mono.pulsar.crypto
  (:require [clojure.string :as string]
            [com.repldriven.mono.encryption.interface :as encryption])
  (:import (org.apache.pulsar.client.api CryptoKeyReader EncryptionKeyInfo)
           (java.security PrivateKey PublicKey)
           (java.util Map)
           (java.io Serializable)))


(defn- get-crypto-key-pair
  [k named-kps]
  (let [k' (if (empty? k) (get :default-key named-kps) k)]
    (get-in named-kps [:keys k'])))

(defn key-pair-generator
  [named-kps]
  (reduce-kv (fn [m k v] (assoc m k (encryption/create-key-pair v)))
             {}
             named-kps))

(defn key-reader
  [named-kps]
  (reify CryptoKeyReader

    (^EncryptionKeyInfo getPublicKey [this ^String keyName ^Map _metadata]
     (doto (EncryptionKeyInfo.)
       (.setKey (-> (:public-key (get-crypto-key-pair keyName named-kps))
                    encryption/public-key->der-string))))

    (^EncryptionKeyInfo getPrivateKey [this ^String keyName ^Map _metadata]
     (doto (EncryptionKeyInfo.)
       (.setKey (-> (:private-key (get-crypto-key-pair keyName named-kps))
                    encryption/private-key->der-string)))))
    )

(comment
  (require '[com.repldriven.mono.encryption.interface :as encryption])
  (let [key-pair (encryption/create-rsa-512-key-pair)
        key-name "tenant-key-1"
        metadata (java.util.HashMap. {"tenant-key-1" "1st key"})
        r (key-reader {:default-key key-name
                       :keys (key-pair-generator
                              {key-name {:algorithm "RSA" :key-size 512}})})]
    {:private-key (encryption/private-key-pkcs8-encoded->rsa
                   (.getKey (.getPrivateKey r key-name metadata)))
     :public-key (encryption/public-key-x509-encoded->rsa
                  (.getKey (.getPublicKey r key-name metadata)))})
)
