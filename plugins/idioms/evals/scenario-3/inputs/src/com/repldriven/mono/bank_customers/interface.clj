(ns com.repldriven.mono.bank-customers.interface
  (:require
    [com.repldriven.mono.bank-customers.core :as core]))

(defn customer-exists?
  "Returns true if a customer with the given id is on record."
  [system customer-id]
  (core/customer-exists? system customer-id))
