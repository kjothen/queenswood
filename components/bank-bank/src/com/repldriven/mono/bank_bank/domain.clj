(ns com.repldriven.mono.bank-bank.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-bank
  [bank-name bank-status policies]
  (let-nom>
    [_ (policy/check-capability policies
                                :bank
                                {:action :bank-action-create
                                 :status bank-status})]
    (let [now (System/currentTimeMillis)]
      {:bank-id (utility/generate-id "bnk")
       :name bank-name
       :status bank-status
       :created-at now
       :updated-at now})))
