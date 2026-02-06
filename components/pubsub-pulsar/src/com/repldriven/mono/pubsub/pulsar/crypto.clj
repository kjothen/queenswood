(ns com.repldriven.mono.pubsub.pulsar.crypto
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.encryption.interface :as encryption])
  (:import (org.apache.pulsar.client.api CryptoKeyReader EncryptionKeyInfo)
           (java.security PrivateKey PublicKey)
           (java.util Map)
           (java.io Serializable)))

(defn- get-crypto-key-pair
  [k named-kps]
  (let [k' (if (empty? k) (get :default-key named-kps) k)]
    (get-in named-kps [:keys k'])))

(defn- read-file-as-bytes
  [f]
  (if-let [h (some-> f
                     io/resource
                     io/file)]
    (do (log/info "Loading file: " f)
        (some-> h
                slurp
                .getBytes))
    (log/error "Unable to load file: " f)))

;; TODO: ->der-string does not work, require ->pem-string instead
(defn key-pair-generator
  [named-kps]
  (reduce-kv
   (fn [m k v]
     (assoc m
            k
            (let [kp (encryption/create-key-pair v)]
              {:public-key (-> kp
                               (get :public-key)
                               (encryption/public-key->der-string))
               :private-key (-> kp
                                (get :private-key)
                                (encryption/private-key->der-string))})))
   {}
   named-kps))

(defn key-pair-file-reader
  [named-kps]
  (reduce-kv (fn [m k v]
               (assoc m
                      k
                      {:public-key (read-file-as-bytes (:public-key v))
                       :private-key (read-file-as-bytes (:private-key v))}))
             {}
             named-kps))

(defn- key->encryption-key-info [k]
  (when (some? k)
    (doto (EncryptionKeyInfo.) (.setKey k))))

(defn key-reader
  [named-kps]
  (reify
    CryptoKeyReader
    (^EncryptionKeyInfo getPublicKey
      [this ^String keyName ^Map _metadata]
      (log/info "Trying to read public key: get" keyName
                "in" (keys (:keys named-kps)))
      (key->encryption-key-info (get (get-crypto-key-pair keyName named-kps) :public-key)))
    (^EncryptionKeyInfo getPrivateKey
      [this ^String keyName ^Map _metadata]
      (log/info "Trying to read private key: get" keyName
                "in" (keys (:keys named-kps)))
      (key->encryption-key-info (get (get-crypto-key-pair keyName named-kps) :private-key)))))

(comment
  (require '[com.repldriven.mono.encryption.interface :as encryption])
  (let [key-pair (encryption/create-rsa-512-key-pair)
        key-name "tenant-key-1"
        metadata (java.util.HashMap. {"tenant-key-1" "1st key"})
        r (key-reader {:default-key key-name
                       :keys (key-pair-generator {key-name {:algorithm "RSA"
                                                            :key-size 512}})})]
    {:private-key (encryption/private-key-pkcs8-encoded->rsa
                   (.getKey (.getPrivateKey r key-name metadata)))
     :public-key (encryption/public-key-x509-encoded->rsa
                  (.getKey (.getPublicKey r key-name metadata)))}))
