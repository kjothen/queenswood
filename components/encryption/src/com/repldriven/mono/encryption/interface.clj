(ns com.repldriven.mono.encryption.interface
  (:require [clojure.string :as string]
            [com.repldriven.mono.encryption.core :as core]))

(defn decode64 [s] (core/decode64 s))

(defn encode64 [bytes] (core/encode64 bytes))

(defn create-aes-256-key [] (core/create-aes-256-key))

(defn create-rsa-512-key-pair [] (core/create-rsa-512-key-pair))

(defn create-key-pair [opts] (core/create-key-pair opts))

(defn private-key-pkcs8-encoded->rsa
  [encoded-key]
  (core/private-key-pkcs8-encoded->rsa encoded-key))

(defn public-key-x509-encoded->rsa
  [encoded-key]
  (core/public-key-x509-encoded->rsa encoded-key))

(defn public-key->der-string [k] (core/public-key->der-string k))

(defn private-key->der-string [k] (core/private-key->der-string k))

(defn encrypt-str
  [s symmetric-key algorithm]
  (core/encrypt-str s symmetric-key algorithm))

(defn decrypt-str
  [encrypted symmetric-key]
  (core/decrypt-str encrypted symmetric-key))
