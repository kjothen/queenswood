(ns com.repldriven.mono.bank-api.restriction.examples.cash-account-product
  (:require
    [com.repldriven.mono.bank-restriction.interface
     :as restriction]))

(def Policies
  {:value (:policies (restriction/default-cash-account-product-restrictions))})

(def Limits
  {:value (:limits (restriction/default-cash-account-product-restrictions))})
