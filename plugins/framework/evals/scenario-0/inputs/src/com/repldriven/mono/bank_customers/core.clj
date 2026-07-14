(ns com.repldriven.mono.bank-customers.core)

(defn customer-exists?
  [system customer-id]
  (contains? (:customers system) customer-id))
