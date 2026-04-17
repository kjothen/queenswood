(ns com.repldriven.mono.bank-payment.interface
  (:require
    com.repldriven.mono.bank-payment.system

    [com.repldriven.mono.bank-payment.store :as store]))

(defn get-internal-payment
  "Loads an internal payment by payment-id. Returns the
  payment map or nil."
  [txn payment-id]
  (store/get-internal-payment txn payment-id))

(defn get-outbound-payment
  "Loads an outbound payment by payment-id. Returns the
  payment map or nil."
  [txn payment-id]
  (store/get-outbound-payment txn payment-id))
