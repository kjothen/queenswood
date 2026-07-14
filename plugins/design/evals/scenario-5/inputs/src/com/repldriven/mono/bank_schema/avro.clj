(ns com.repldriven.mono.bank-schema.avro
  (:require
    [com.deercreeklabs.lancaster :as l]))

(l/def-record-schema transaction-settled-schema
  :bank-schema.event/transaction-settled
  [:transaction-id :string]
  [:settled-at :long])

;; TODO: interest-accrued-schema — account-id (string), amount (double)
