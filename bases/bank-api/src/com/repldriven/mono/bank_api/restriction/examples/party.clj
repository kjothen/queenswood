(ns com.repldriven.mono.bank-api.restriction.examples.party
  (:require
    [com.repldriven.mono.bank-restriction.interface
     :as restriction]))

(def Policies
  {:value (:policies (restriction/default-person-party-restrictions))})

(def Limits
  {:value (:limits (restriction/default-person-party-restrictions))})
