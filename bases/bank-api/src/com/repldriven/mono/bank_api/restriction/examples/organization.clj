(ns com.repldriven.mono.bank-api.restriction.examples.organization
  (:require
    [com.repldriven.mono.bank-restriction.interface
     :as restriction]))

(def Policies
  {:value (:policies (restriction/default-customer-organization-restrictions))})

(def Limits
  {:value (:limits (restriction/default-customer-organization-restrictions))})
