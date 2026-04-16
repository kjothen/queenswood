(ns com.repldriven.mono.bank-idv.interface
  (:require
    com.repldriven.mono.bank-idv.system

    [com.repldriven.mono.bank-idv.store :as store]))

(defn find-idv
  "Loads an IDV by composite PK if it exists. Returns the
  IDV map, nil, or anomaly on I/O failure."
  [txn organization-id verification-id]
  (store/find-idv txn organization-id verification-id))
