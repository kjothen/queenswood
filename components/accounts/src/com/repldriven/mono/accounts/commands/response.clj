(ns com.repldriven.mono.accounts.commands.response
  (:require
    [com.repldriven.mono.avro.interface :as avro]))

(defn ->account
  [schemas status payload]
  {:status status :payload (avro/serialize (get schemas "account") payload)})

(defn ->account-status
  [schemas status payload]
  {:status status
   :payload (avro/serialize (get schemas "account-status") payload)})
