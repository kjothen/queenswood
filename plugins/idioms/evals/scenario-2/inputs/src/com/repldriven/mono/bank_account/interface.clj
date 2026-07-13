(ns com.repldriven.mono.bank-account.interface
  (:require
    [com.repldriven.mono.bank-account.core :as core]))

(defn deposit
  "Deposits an amount into the account named by account-id. Returns the
  updated system."
  [system account-id amount]
  (core/deposit system account-id amount))
