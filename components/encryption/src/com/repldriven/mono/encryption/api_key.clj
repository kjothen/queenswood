(ns com.repldriven.mono.encryption.api-key
  (:require
    [buddy.core.codecs :as codecs]
    [buddy.core.hash :as hash]
    [buddy.core.nonce :as nonce]))

(defn generate
  "Returns a random API key string with the given prefix."
  [prefix]
  (str prefix
       (codecs/bytes->str (codecs/bytes->b64 (nonce/random-bytes 32) true))))

(defn hash-key
  "Returns the hex-encoded SHA-256 hash of a raw API key."
  [raw-key]
  (codecs/bytes->hex (hash/sha256 raw-key)))
