(ns com.repldriven.mono.bank-idv.interface
  (:require
    com.repldriven.mono.bank-idv.system

    [com.repldriven.mono.bank-idv.store :as store]))

(defn get-idv
  "Loads an IDV by composite PK. Returns the
  idv or anomaly if missing/failure."
  [txn organization-id verification-id]
  (store/get-idv txn organization-id verification-id))
