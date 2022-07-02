(ns com.repldriven.mono.symmetric-key.core
  (:require [buddy.core.crypto :as crypto]
            [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce])
  (:import (javax.crypto KeyGenerator)))

(defonce ^:private ^KeyGenerator key-gen-aes-256
  (when-not *compile-files*
    (let [instance (KeyGenerator/getInstance "AES")
          _ (.init instance 256)]
      instance)))

(defn create-aes-256-key
  []
  {:iv        (nonce/random-bytes 16)
   :key       (-> (.generateKey key-gen-aes-256) (.getEncoded))
   :algorithm "AES"
   :key-size  256})

(defn encrypt-str
  [s {:keys [iv key]} algorithm]
  {:encrypted (crypto/encrypt (codecs/to-bytes s) key iv {:algorithm algorithm})
   :iv        iv
   :algorithm algorithm})

(defn decrypt-str
  [{:keys [encrypted iv algorithm]} {:keys [key]}]
  (codecs/bytes->str (crypto/decrypt encrypted key iv {:algorithm algorithm})))
