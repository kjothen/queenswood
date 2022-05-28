(ns com.repldriven.mono.symmetric-key.interface
  (:require [com.repldriven.mono.symmetric-key.core :as core]))

(defn create-aes-256-key
  []
  (core/create-aes-256-key))

(defn encrypt-str
  [s symmetric-key algorithm]
  (core/encrypt-str s symmetric-key algorithm))

(defn decrypt-str
  [encrypted symmetric-key]
  (core/decrypt-str encrypted symmetric-key))

