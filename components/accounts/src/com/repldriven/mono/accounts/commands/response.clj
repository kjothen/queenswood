(ns com.repldriven.mono.accounts.commands.response
  (:require
    [com.repldriven.mono.avro.interface :as avro]))

(defn ->account
  [schemas status data]
  {:status status :payload (avro/serialize (get schemas "account") data)})

(defn ->account-status
  [schemas status data]
  {:status status
   :payload (avro/serialize (get schemas "account-status") data)})
