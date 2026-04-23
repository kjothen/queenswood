(ns com.repldriven.mono.bank-api.balance.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def BalanceNotFound
  {:value {:title "REJECTED"
           :type "balances/not-found"
           :status 404
           :detail "Balance not found"}})

(def BalanceAlreadyExists
  {:value {:title "REJECTED"
           :type "balance/already-exists"
           :status 409
           :detail "Balance already exists"}})

(def registry (examples-registry [#'BalanceNotFound #'BalanceAlreadyExists]))

(def Balance
  {:account-id "acc.01kprbmgcj35ptc8npmybhh4s8"
   :product-type :current
   :balance-type :default
   :balance-status :posted
   :currency "GBP"
   :credit 0
   :debit 0
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def BalanceList
  {:balances [Balance]
   :posted-balance {:value 0 :currency "GBP"}
   :available-balance {:value 0 :currency "GBP"}})

(def CreateBalanceRequest
  {:balance-type :default :balance-status :posted :currency "GBP"})

(def BalanceProduct {:balance-type :default :balance-status :posted})
