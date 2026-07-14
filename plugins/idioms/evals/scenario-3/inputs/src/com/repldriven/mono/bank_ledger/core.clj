(ns com.repldriven.mono.bank-ledger.core
  (:require
    [com.repldriven.mono.utility.interface :as util]))

(defn record-entry
  "Records a completed transaction into the ledger."
  [ledger tx-type amount]
  (conj ledger
        {:id (util/uuidv7)
         :type tx-type
         :amount amount
         :timestamp (util/now)}))
