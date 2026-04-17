(ns com.repldriven.mono.bank-balance.restriction
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn check-unique?
  "Returns true if no balance exists for the given data,
  rejection anomaly otherwise."
  [data exists?]
  (if exists?
    (error/reject
     :balance/already-exists
     (merge {:message "Balance already exists"}
            (select-keys data
                         [:account-id :balance-type
                          :currency :balance-status])))
    true))
