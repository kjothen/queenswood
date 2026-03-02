(ns com.repldriven.mono.encryption.core
  (:require
    [buddy.core.codecs :as codecs]
    [buddy.core.crypto :as crypto]
    [buddy.core.nonce :as nonce]

    [clojure.string :as string])
  (:import
    (javax.crypto KeyGenerator)
    (java.security KeyFactory KeyPairGenerator PrivateKey PublicKey)
    (java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec)
    (java.util Base64)))

(defonce ^:private ^KeyGenerator key-gen-aes-256
  (when-not *compile-files*
    (doto (KeyGenerator/getInstance "AES") (.init 256))))

(defonce ^:private ^KeyPairGenerator key-pair-gen-rsa-512
  (when-not *compile-files*
    (doto (KeyPairGenerator/getInstance "RSA") (.initialize 512))))

(defonce ^:private ^KeyFactory key-factory-rsa
  (when-not *compile-files* (KeyFactory/getInstance "RSA")))

(defn decode64 [^String s] (.decode (Base64/getDecoder) s))

(defn encode64 [bytes] (.encodeToString (Base64/getEncoder) bytes))

(defn create-aes-256-key
  []
  {:iv (nonce/random-bytes 16)
   :key (-> (.generateKey key-gen-aes-256)
            (.getEncoded))
   :algorithm "AES"
   :key-size 256})

(defn create-rsa-512-key-pair
  []
  (when-let [key-pair (.generateKeyPair key-pair-gen-rsa-512)]
    {:private-key (.getPrivate key-pair)
     :public-key (.getPublic key-pair)
     :algorithm "RSA"
     :key-size 512}))

(defn create-key-pair
  [opts]
  (case (select-keys opts [:algorithm :key-size])
    {:algorithm "RSA" :key-size 512} (create-rsa-512-key-pair)))

(defn public-key-x509-encoded->rsa
  [encoded-key]
  (.generatePublic key-factory-rsa (X509EncodedKeySpec. encoded-key)))

(defn private-key-pkcs8-encoded->rsa
  [encoded-key]
  (.generatePrivate key-factory-rsa (PKCS8EncodedKeySpec. encoded-key)))

(defn public-key->der-string
  [^PublicKey k]
  (-> k
      .getEncoded
      encode64
      (string/replace #"\n" "")))

(defn private-key->der-string
  [^PrivateKey k]
  (-> k
      .getEncoded
      PKCS8EncodedKeySpec.
      .getEncoded
      encode64))

(defn encrypt-str
  [s {:keys [iv key]} algorithm]
  {:encrypted (crypto/encrypt (codecs/to-bytes s) key iv {:algorithm algorithm})
   :iv iv
   :algorithm algorithm})

(defn decrypt-str
  [{:keys [encrypted iv algorithm]} {:keys [key]}]
  (codecs/bytes->str (crypto/decrypt encrypted key iv {:algorithm algorithm})))

(defn generate-id
  [prefix]
  (str prefix "." (codecs/bytes->b64-str (nonce/random-bytes 16) true)))

(comment
  (generate-id "ba")
  (create-aes-256-key)
  (create-rsa-512-key-pair)
  (create-key-pair {:algorithm "RSA" :key-size 512}))
