(ns com.repldriven.mono.bank-api.ledger-account.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def LedgerAccountNotFound
  {:value {:title "REJECTED"
           :type "ledger-account/not-found"
           :status 404
           :detail "Ledger account not found"}})

(def registry (examples-registry [#'LedgerAccountNotFound]))

(def LedgerAccount
  {:bank-id "bnk.01kprbmgcj35ptc8npmybhh4s7"
   :account-id "led.01kprbmgcj35ptc8npmybhh4sa"
   :gl-code "2100"
   :name "Customer deposits - current"
   :currency "GBP"
   :gl-account-type :liability
   :gl-account-class :control
   :required :mandatory
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def LedgerAccountId (:account-id LedgerAccount))

(def LedgerAccountList {:ledger-accounts [LedgerAccount]})