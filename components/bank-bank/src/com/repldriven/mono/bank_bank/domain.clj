(ns com.repldriven.mono.bank-bank.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-bank
  [bank-name bank-type bank-status aggregates policies]
  (let-nom>
    [_ (policy/check-capability policies
                                :bank
                                {:action :bank-action-create
                                 :type bank-type
                                 :status bank-status})
     _ (policy/check-limit policies
                           :bank
                           {:aggregate :count
                            :window :time-window-instant
                            :type bank-type
                            :status bank-status
                            :value (inc (get-in aggregates
                                                [:bank #{:type}]))})]
    (let [now (System/currentTimeMillis)]
      {:bank-id (utility/generate-id "bnk")
       :name bank-name
       :type bank-type
       :status bank-status
       :created-at now
       :updated-at now})))
